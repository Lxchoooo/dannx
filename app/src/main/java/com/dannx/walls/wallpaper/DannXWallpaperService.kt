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
import android.view.Choreographer
import android.view.SurfaceHolder
import com.dannx.walls.data.AppearancePrefs
import com.dannx.walls.data.LiveMotion
import com.dannx.walls.ui.editor.EditorBlur
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

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
        private val layerDst = RectF()
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
        private var animatorsEnabled = true
        private var motion: LiveMotion = LiveMotion.NORMAL
        private var displayOffset = 0.5f
        private var frameScheduled = false
        private var lastFrameNanos = 0L

        /**
         * El launcher puede mandar muchos offsets por segundo. Se guarda el
         * último y se dibuja una sola vez por frame, acercando la posición
         * dibujada a la real. Eso suaviza los launchers que informan saltos y
         * evita encolar dibujos que después se ven como tirones.
         */
        private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
            frameScheduled = false
            if (!visible || destroyed) return@FrameCallback
            if (!moves || motion == LiveMotion.STILL) {
                displayOffset = xOffset
                drawFrame()
                return@FrameCallback
            }
            val diff = xOffset - displayOffset
            if (abs(diff) <= OffsetEpsilon) {
                displayOffset = xOffset
                lastFrameNanos = 0L
                drawFrame()
                return@FrameCallback
            }
            val elapsed = if (lastFrameNanos == 0L) {
                NominalFrameMillis
            } else {
                ((frameTimeNanos - lastFrameNanos) / 1_000_000.0).coerceIn(1.0, 64.0)
            }
            lastFrameNanos = frameTimeNanos
            val alpha = (1.0 - exp(-elapsed / SmoothingTauMillis))
                .toFloat()
                .coerceIn(0.05f, 1f)
            displayOffset += diff * alpha
            drawFrame()
            scheduleMotion()
        }

        private fun scheduleMotion() {
            if (!visible || destroyed || frameScheduled) return
            if (!moves || motion == LiveMotion.STILL) return
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LiveWallpaperStore.KEY_VERSION) reload()
        }

        /**
         * La intensidad y el modo lite se aplican en caliente: alcanza con
         * redibujar o retomar el movimiento, sin recargar la foto.
         */
        private val appearanceListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    AppearancePrefs.KEY_LIVE_MOTION -> {
                        refreshMotion()
                        // Al volver de Quieto se persigue la posición real en
                        // vez de saltar; si queda quieto, se centra de una.
                        if (moves && motion != LiveMotion.STILL) {
                            scheduleMotion()
                        } else {
                            displayOffset = xOffset
                            drawFrame()
                        }
                    }
                    AppearancePrefs.KEY_LITE -> syncMotion()
                }
            }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
            moves = motionAllowed()
            refreshMotion()
            LiveWallpaperStore.prefs(applicationContext)
                .registerOnSharedPreferenceChangeListener(prefsListener)
            appearancePrefs().registerOnSharedPreferenceChangeListener(appearanceListener)
            reload()
        }

        override fun onDestroy() {
            destroyed = true
            frameScheduled = false
            runCatching {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
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
                // El usuario pudo apagar las animaciones del sistema mientras
                // el fondo estaba oculto.
                animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
                moves = motionAllowed()
                refreshMotion()
                // Al volver no se anima desde una posición vieja: se entra
                // directo a la que dejó el launcher.
                displayOffset = xOffset
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

        /**
         * Lite, o animaciones del sistema apagadas, dejan la foto quieta.
         * La escala del sistema se guarda en un campo para no leerla en cada
         * frame; se refresca al mostrar el fondo y cuando cambia el ajuste.
         */
        private fun motionAllowed(): Boolean =
            !AppearancePrefs(applicationContext).liteMode() && animatorsEnabled

        /** Aplica el cambio de "hay movimiento" sin saltos ni repintados de más. */
        private fun syncMotion() {
            val allowed = motionAllowed()
            if (allowed == moves) return
            moves = allowed
            if (allowed) {
                scheduleMotion()
            } else {
                displayOffset = xOffset
                drawFrame()
            }
        }

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
            // Con el fondo ya en pantalla, apagar las animaciones del sistema no
            // recrea el servicio: se detecta acá y se frena en el acto.
            val enabled = ValueAnimator.areAnimatorsEnabled()
            if (enabled != animatorsEnabled) {
                animatorsEnabled = enabled
                syncMotion()
            }
            // Quieto o lite no cambian el frame: no hay que repintar.
            scheduleMotion()
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
            val decoded = decodeCover(file, targetWidth, targetHeight) ?: return null
            val foreground = coverTo(decoded, targetWidth, targetHeight)
            val backdrop = runCatching { createBackdrop(foreground, targetWidth, targetHeight) }
                .getOrNull()
            if (backdrop == null) {
                foreground.recycle()
                return null
            }
            return Layers(foreground, backdrop)
        }

        /**
         * Escala y recorta una sola vez al tamaño de la pantalla. El dibujo por
         * frame queda como una copia de píxeles, sin filtros ni escalados.
         * Se mantiene la proporción: lo que sobra se recorta centrado.
         */
        private fun coverTo(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            if (source.width == targetWidth && source.height == targetHeight) return source
            val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val scale = max(
                targetWidth / source.width.toFloat(),
                targetHeight / source.height.toFloat(),
            )
            val drawWidth = source.width * scale
            val drawHeight = source.height * scale
            val left = (targetWidth - drawWidth) / 2f
            val top = (targetHeight - drawHeight) / 2f
            canvas.drawBitmap(
                source,
                null,
                RectF(left, top, left + drawWidth, top + drawHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            source.recycle()
            return output
        }

        /** Escala una capa ya calculada; se usa al cargar, no por frame. */
        private fun scaleTo(source: Bitmap, width: Int, height: Int): Bitmap {
            if (source.width == width && source.height == height) return source
            val scaled = Bitmap.createScaledBitmap(source, width, height, true)
            if (scaled !== source) source.recycle()
            return scaled
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
        private fun createBackdrop(
            source: Bitmap,
            targetWidth: Int,
            targetHeight: Int,
        ): Bitmap {
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
            val blurred = Bitmap.createBitmap(
                pixels,
                backdropWidth,
                backdropHeight,
                Bitmap.Config.ARGB_8888,
            )
            val scaledWidth = (targetWidth * (1f + LiveWallpaperGeometry.BackgroundSlack))
                .roundToInt()
                .coerceAtLeast(1)
            val scaledHeight = (targetHeight * (1f + LiveWallpaperGeometry.BackgroundSlack))
                .roundToInt()
                .coerceAtLeast(1)
            return scaleTo(blurred, scaledWidth, scaledHeight)
        }

        private fun drawFrame() {
            if (!visible || destroyed || width <= 0 || height <= 0) return
            val holder = surfaceHolder ?: return
            val canvas: Canvas = lockSurface(holder) ?: return
            try {
                draw(canvas)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        /**
         * El canvas acelerado evita que cada copia de la foto pase por el
         * renderer por software. Si el surface no lo admite, se cae al normal.
         */
        private fun lockSurface(holder: SurfaceHolder): Canvas? =
            runCatching { holder.lockHardwareCanvas() }.getOrNull()
                ?: runCatching { holder.lockCanvas() }.getOrNull()

        private fun draw(canvas: Canvas) {
            val surfaceWidth = width.toFloat()
            val surfaceHeight = height.toFloat()

            val current = layers
            if (current == null) {
                drawFallback(canvas, surfaceWidth, surfaceHeight)
                return
            }
            val activeMotion = if (moves) motion else LiveMotion.STILL
            drawLayer(
                canvas = canvas,
                bitmap = current.backdrop,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                fraction = LiveWallpaperGeometry.BackgroundShift,
                motion = activeMotion,
            )
            drawLayer(
                canvas = canvas,
                bitmap = current.foreground,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                fraction = LiveWallpaperGeometry.ForegroundShift,
                motion = activeMotion,
            )
        }

        /** Dibuja una capa ya preescalada: solo se mueve, no se vuelve a escalar. */
        private fun drawLayer(
            canvas: Canvas,
            bitmap: Bitmap,
            surfaceWidth: Float,
            surfaceHeight: Float,
            fraction: Float,
            motion: LiveMotion,
        ) {
            val layerWidth = bitmap.width.toFloat()
            val layerHeight = bitmap.height.toFloat()
            if (layerWidth <= 0f || layerHeight <= 0f) return
            val shift = LiveWallpaperGeometry.shift(
                surfaceWidth,
                displayOffset,
                fraction,
                motion,
            )
            val centerX = surfaceWidth / 2f + shift
            layerDst.set(
                centerX - layerWidth / 2f,
                (surfaceHeight - layerHeight) / 2f,
                centerX + layerWidth / 2f,
                (surfaceHeight + layerHeight) / 2f,
            )
            canvas.drawBitmap(bitmap, null, layerDst, paint)
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

        /** Diferencia mínima para considerar que el fondo llegó al destino. */
        const val OffsetEpsilon = 0.001f

        /** Duración típica de un frame; se usa solo en el primero del gesto. */
        const val NominalFrameMillis = 16.0

        /** Constante del suavizado: con ~42 ms alcanza el destino en ~150 ms. */
        const val SmoothingTauMillis = 42.0

        val FallbackTop: Int = Color.rgb(30, 32, 38)
        val FallbackBottom: Int = Color.rgb(8, 8, 10)
    }
}
