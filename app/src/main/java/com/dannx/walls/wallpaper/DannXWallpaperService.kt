package com.dannx.walls.wallpaper

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.dannx.walls.data.AppearancePrefs
import com.dannx.walls.data.LiveMotion
import com.dannx.walls.ui.editor.EditorBlur
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Fondo animado de DannX: la foto nítida se corre sobre una copia
 * desenfocada y más grande de sí misma cuando el launcher cambia de pantalla.
 *
 * No anima por su cuenta. Solo redibuja cuando el sistema informa un
 * desplazamiento nuevo, así que en reposo no gasta batería. Con el modo lite
 * del teléfono o con las animaciones del sistema apagadas, la foto queda fija.
 */
class DannXWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = DannXWallpaperEngine()

    private inner class DannXWallpaperEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "dannx-wallpaper").apply { isDaemon = true }
        }
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        private val fallbackPaint = Paint()
        private val backdropDst = RectF()
        private val foregroundDst = RectF()
        private var fallbackWidth = 0
        private var fallbackHeight = 0

        private var layers: Layers? = null
        private var loadToken = 0
        private var destroyed = false
        private var visible = false
        private var width = 0
        private var height = 0
        private var xOffset = 0.5f
        private var moves = true
        private var motion: LiveMotion = LiveMotion.NORMAL

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LiveWallpaperStore.KEY_VERSION) reload()
        }

        /**
         * La intensidad y el modo lite se aplican en caliente: alcanza con
         * volver a dibujar, no hace falta recargar la foto.
         */
        private val appearanceListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    AppearancePrefs.KEY_LIVE_MOTION -> {
                        refreshMotion()
                        drawFrame()
                    }
                    AppearancePrefs.KEY_LITE -> {
                        moves = motionAllowed()
                        drawFrame()
                    }
                }
            }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            moves = motionAllowed()
            refreshMotion()
            LiveWallpaperStore.prefs(applicationContext)
                .registerOnSharedPreferenceChangeListener(prefsListener)
            appearancePrefs().registerOnSharedPreferenceChangeListener(appearanceListener)
            reload()
        }

        override fun onDestroy() {
            destroyed = true
            runCatching {
                LiveWallpaperStore.prefs(applicationContext)
                    .unregisterOnSharedPreferenceChangeListener(prefsListener)
                appearancePrefs().unregisterOnSharedPreferenceChangeListener(appearanceListener)
            }
            worker.shutdownNow()
            recycleLayers()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                moves = motionAllowed()
                refreshMotion()
                drawFrame()
            }
        }

        private fun appearancePrefs(): SharedPreferences =
            applicationContext.getSharedPreferences(
                AppearancePrefs.PREFS_NAME,
                Context.MODE_PRIVATE,
            )

        private fun refreshMotion() {
            motion = AppearancePrefs(applicationContext).liveMotion()
        }

        /** Lite o animaciones del sistema apagadas: la foto queda quieta. */
        private fun motionAllowed(): Boolean =
            !AppearancePrefs(applicationContext).liteMode() &&
                ValueAnimator.areAnimatorsEnabled()

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            reload()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            surfaceWidth: Int,
            surfaceHeight: Int,
        ) {
            super.onSurfaceChanged(holder, format, surfaceWidth, surfaceHeight)
            width = surfaceWidth
            height = surfaceHeight
            reload()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            loadToken++
            recycleLayers()
            super.onSurfaceDestroyed(holder)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame()
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            super.onOffsetsChanged(
                xOffset,
                yOffset,
                xOffsetStep,
                yOffsetStep,
                xPixelOffset,
                yPixelOffset,
            )
            this.xOffset = xOffset
            // Quieto no necesita repintar por cada desplazamiento del launcher.
            if (moves && motion != LiveMotion.STILL) drawFrame()
        }

        /** Relee la foto guardada y arma las dos capas fuera del hilo principal. */
        private fun reload() {
            if (width <= 0 || height <= 0) return
            val token = ++loadToken
            val targetWidth = width
            val targetHeight = height
            val file = LiveWallpaperStore(applicationContext).imageFile()
            runCatching {
                worker.execute {
                    val built = runCatching { buildLayers(file, targetWidth, targetHeight) }
                        .getOrNull()
                    handler.post { swapLayers(token, built) }
                }
            }
        }

        private fun swapLayers(token: Int, built: Layers?) {
            if (destroyed || token != loadToken) {
                built?.recycle()
                return
            }
            recycleLayers()
            layers = built
            drawFrame()
        }

        private fun buildLayers(file: File, targetWidth: Int, targetHeight: Int): Layers? {
            val foreground = decodeCover(file, targetWidth, targetHeight) ?: return null
            val backdrop = runCatching { createBackdrop(foreground) }.getOrNull()
            if (backdrop == null) {
                foreground.recycle()
                return null
            }
            return Layers(foreground, backdrop)
        }

        private fun decodeCover(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth &&
                bounds.outHeight / (sample * 2) >= targetHeight
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }
                .getOrNull()
        }

        /** Copia chica y desenfocada: solo asoma por los costados que descubre la foto. */
        private fun createBackdrop(source: Bitmap): Bitmap {
            val backdropWidth = (source.width / BackdropDivisor).coerceAtLeast(16)
            val backdropHeight = (source.height * backdropWidth / source.width)
                .coerceAtLeast(16)
            val small = Bitmap.createScaledBitmap(source, backdropWidth, backdropHeight, true)
            val pixels = IntArray(backdropWidth * backdropHeight)
            small.getPixels(pixels, 0, backdropWidth, 0, 0, backdropWidth, backdropHeight)
            if (small !== source) small.recycle()
            EditorBlur.boxBlur(
                pixels = pixels,
                width = backdropWidth,
                height = backdropHeight,
                radius = max(2, backdropWidth / 18),
            )
            return Bitmap.createBitmap(
                pixels,
                backdropWidth,
                backdropHeight,
                Bitmap.Config.ARGB_8888,
            )
        }

        private fun drawFrame() {
            if (!visible || destroyed || width <= 0 || height <= 0) return
            val holder = surfaceHolder ?: return
            val canvas: Canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                draw(canvas)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun draw(canvas: Canvas) {
            val surfaceWidth = width.toFloat()
            val surfaceHeight = height.toFloat()

            val current = layers
            if (current == null) {
                drawFallback(canvas, surfaceWidth, surfaceHeight)
                return
            }
            val foreground = current.foreground
            val scale = LiveWallpaperGeometry.coverScale(
                sourceWidth = foreground.width,
                sourceHeight = foreground.height,
                targetWidth = surfaceWidth,
                targetHeight = surfaceHeight,
            )
            if (scale <= 0f) return
            val foregroundWidth = foreground.width * scale
            val foregroundHeight = foreground.height * scale
            val activeMotion = if (moves) motion else LiveMotion.STILL
            val shift = LiveWallpaperGeometry.shift(
                surfaceWidth,
                xOffset,
                LiveWallpaperGeometry.ForegroundShift,
                activeMotion,
            )
            val centerX = surfaceWidth / 2f + shift
            foregroundDst.set(
                centerX - foregroundWidth / 2f,
                (surfaceHeight - foregroundHeight) / 2f,
                centerX + foregroundWidth / 2f,
                (surfaceHeight + foregroundHeight) / 2f,
            )

            val backdrop = current.backdrop
            val backdropScale = LiveWallpaperGeometry.coverScale(
                sourceWidth = backdrop.width,
                sourceHeight = backdrop.height,
                targetWidth = surfaceWidth * (1f + LiveWallpaperGeometry.BackgroundSlack),
                targetHeight = surfaceHeight * (1f + LiveWallpaperGeometry.BackgroundSlack),
            )
            if (backdropScale > 0f) {
                val backdropWidth = backdrop.width * backdropScale
                val backdropHeight = backdrop.height * backdropScale
                val backdropShift = LiveWallpaperGeometry.shift(
                    surfaceWidth,
                    xOffset,
                    LiveWallpaperGeometry.BackgroundShift,
                    activeMotion,
                )
                val backdropCenterX = surfaceWidth / 2f + backdropShift
                backdropDst.set(
                    backdropCenterX - backdropWidth / 2f,
                    (surfaceHeight - backdropHeight) / 2f,
                    backdropCenterX + backdropWidth / 2f,
                    (surfaceHeight + backdropHeight) / 2f,
                )
                canvas.drawBitmap(backdrop, null, backdropDst, paint)
            }
            canvas.drawBitmap(foreground, null, foregroundDst, paint)
        }

        /**
         * Sin foto no se dibuja negro plano: un degradado oscuro neutro evita
         * que la pantalla parezca rota. El shader se rehace solo al cambiar el
         * tamaño, nunca en cada frame.
         */
        private fun drawFallback(canvas: Canvas, surfaceWidth: Float, surfaceHeight: Float) {
            if (fallbackPaint.shader == null ||
                fallbackWidth != width ||
                fallbackHeight != height
            ) {
                fallbackWidth = width
                fallbackHeight = height
                fallbackPaint.shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    surfaceHeight,
                    FallbackTop,
                    FallbackBottom,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, surfaceWidth, surfaceHeight, fallbackPaint)
        }

        private fun recycleLayers() {
            layers?.recycle()
            layers = null
        }
    }

    private class Layers(
        val foreground: Bitmap,
        val backdrop: Bitmap,
    ) {
        fun recycle() {
            foreground.recycle()
            backdrop.recycle()
        }
    }

    private companion object {
        /** La copia de fondo se calcula enana: el desenfoque oculta el detalle. */
        const val BackdropDivisor = 8
        val FallbackTop: Int = Color.rgb(30, 32, 38)
        val FallbackBottom: Int = Color.rgb(8, 8, 10)
    }
}
