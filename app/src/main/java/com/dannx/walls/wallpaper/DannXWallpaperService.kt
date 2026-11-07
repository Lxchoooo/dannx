package com.dannx.walls.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.compose.ui.graphics.toArgb
import com.dannx.walls.ui.theme.DarkColors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Fondo animado con manchas de color que se mueven lento.
 *
 * Corre a pocos cuadros por segundo y se detiene cuando no se está viendo, así
 * no gasta batería de más.
 */
class DannXWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = AuroraEngine()

    private inner class AuroraEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var visible = false
        private var startedAt = 0L

        private val blobs = listOf(
            Blob(
                color = withAlpha(DarkColors.primary.toArgb(), 0x66),
                baseX = 0.30f,
                baseY = 0.26f,
                periodMs = 16_000L,
                radiusFactor = 0.58f,
            ),
            Blob(
                color = withAlpha(DarkColors.tertiary.toArgb(), 0x5A),
                baseX = 0.72f,
                baseY = 0.42f,
                periodMs = 21_000L,
                radiusFactor = 0.50f,
            ),
            Blob(
                color = withAlpha(DarkColors.secondary.toArgb(), 0x50),
                baseX = 0.46f,
                baseY = 0.80f,
                periodMs = 26_000L,
                radiusFactor = 0.54f,
            ),
        )

        private val frame = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, FRAME_MS)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(frame)
            if (visible) {
                if (startedAt == 0L) startedAt = System.currentTimeMillis()
                handler.post(frame)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            super.onDestroy()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            val canvas: Canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                val width = canvas.width.toFloat().coerceAtLeast(1f)
                val height = canvas.height.toFloat().coerceAtLeast(1f)
                canvas.drawColor(BASE_COLOR)
                val elapsed = (System.currentTimeMillis() - startedAt).toFloat()
                blobs.forEachIndexed { index, blob ->
                    val phase = elapsed / blob.periodMs
                    val centerX = (blob.baseX + 0.12f * sin(phase * TAU + index)) * width
                    val centerY = (blob.baseY + 0.10f * cos(phase * TAU * 0.8f + index)) * height
                    val radius = max(width, height) * blob.radiusFactor
                    paint.shader = RadialGradient(
                        centerX,
                        centerY,
                        radius,
                        intArrayOf(blob.color, TRANSPARENT),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }

    private data class Blob(
        val color: Int,
        val baseX: Float,
        val baseY: Float,
        val periodMs: Long,
        val radiusFactor: Float,
    )

    private companion object {
        const val FRAME_MS = 50L
        const val TRANSPARENT = 0x00000000
        val TAU = (Math.PI * 2).toFloat()
        val BASE_COLOR: Int = Color.rgb(10, 11, 15)

        fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
