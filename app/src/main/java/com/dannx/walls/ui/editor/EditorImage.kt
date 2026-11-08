package com.dannx.walls.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import com.dannx.walls.util.CropRect
import com.dannx.walls.util.calculateInSampleSize
import com.dannx.walls.util.centerCropRect
import java.io.File
import java.io.IOException
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Miniatura de un filtro para el selector. */
@Immutable
internal data class EditorFilterThumb(
    val preset: FilterPreset,
    val bitmap: Bitmap,
)

/**
 * Renderer compartido por la vista previa y la imagen final.
 *
 * La preview dibuja directo sobre el Canvas de Compose, sin crear un bitmap
 * nuevo por cada cambio: el color se resuelve con ColorMatrixColorFilter. La
 * imagen final se genera una sola vez al confirmar.
 */
internal object EditorImage {
    private const val PreviewMaxSide = 1080
    private const val BlurMaxSide = 320
    private const val ThumbWidth = 84
    private const val ThumbHeight = 140

    fun decodePreview(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            throw IOException("La imagen no se pudo decodificar.")
        }
        var sample = 1
        while (max(width, height) / sample > PreviewMaxSide) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IOException("La imagen no se pudo decodificar.")
    }

    /** Copia chica y desenfocada. Se dibuja encima de la original con alfa. */
    fun createBlurred(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        val scale = if (longest > BlurMaxSide) BlurMaxSide.toFloat() / longest else 1f
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        val small = if (width == source.width && height == source.height) {
            source.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IOException("No se pudo preparar el desenfoque.")
        } else {
            source.scale(width, height, true)
        }
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        boxBlur(pixels, width, height, max(1, min(width, height) / 40))
        val output = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== source) {
            small.recycle()
        }
        return output
    }

    fun createGrainTile(size: Int = 128): Bitmap {
        val random = Random(7L)
        val pixels = IntArray(size * size)
        for (index in pixels.indices) {
            val value = random.nextInt(256)
            pixels[index] = Color.argb(255, value, value, value)
        }
        return createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    fun createFilterThumbs(source: Bitmap): List<EditorFilterThumb> {
        val sourceRect = centerCropRect(source.width, source.height, ThumbWidth, ThumbHeight)
        return EditorPresets.all.map { preset ->
            val bitmap = createBitmap(ThumbWidth, ThumbHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                if (preset.id != "original") {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix(preset.values))
                }
            }
            canvas.drawBitmap(
                source,
                Rect(sourceRect.left, sourceRect.top, sourceRect.right, sourceRect.bottom),
                RectF(0f, 0f, ThumbWidth.toFloat(), ThumbHeight.toFloat()),
                paint,
            )
            EditorFilterThumb(preset, bitmap)
        }
    }

    fun drawPreview(
        canvas: Canvas,
        width: Float,
        height: Float,
        source: Bitmap,
        blurred: Bitmap?,
        grain: Bitmap?,
        settings: EditorSettings,
        showOriginal: Boolean,
    ) {
        if (width <= 0f || height <= 0f) return
        canvas.withClip(0f, 0f, width, height) {
            canvas.drawColor(Color.BLACK)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (!showOriginal) {
                paint.colorFilter = ColorMatrixColorFilter(EditorPresets.colorMatrix(settings))
            }
            val destination = destinationRect(
                source.width,
                source.height,
                width,
                height,
                settings.crop,
            )
            canvas.drawBitmap(source, null, destination, paint)

            if (!showOriginal && settings.blur > 0f && blurred != null) {
                paint.alpha = (settings.blur * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawBitmap(blurred, null, destination, paint)
                paint.alpha = 255
            }
            if (!showOriginal) {
                drawVignette(canvas, width, height, settings.adjustments.vignette)
                drawGrain(canvas, width, height, grain, settings.adjustments.grain)
            }
        }
    }

    /**
     * Genera una sola vez el bitmap que se aplica al sistema. La región se
     * decodifica desde el archivo original y se escala al tamaño de pantalla.
     */
    fun renderFinal(
        file: File,
        targetWidth: Int,
        targetHeight: Int,
        settings: EditorSettings,
        onProgress: (Int) -> Unit = {},
    ): Bitmap {
        onProgress(8)
        val region = decodeCropRegion(file, targetWidth, targetHeight, settings.crop)
        onProgress(30)
        val output = createBitmap(
            targetWidth.coerceAtLeast(1),
            targetHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(EditorPresets.colorMatrix(settings))
        }
        canvas.drawBitmap(
            region,
            null,
            RectF(0f, 0f, output.width.toFloat(), output.height.toFloat()),
            paint,
        )
        region.recycle()
        onProgress(52)

        if (settings.blur > 0f) {
            val blurred = createBlurred(output)
            val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (settings.blur * 255f).roundToInt().coerceIn(0, 255)
            }
            canvas.drawBitmap(
                blurred,
                null,
                RectF(0f, 0f, output.width.toFloat(), output.height.toFloat()),
                blurPaint,
            )
            blurred.recycle()
        }
        onProgress(72)

        drawVignette(
            canvas,
            output.width.toFloat(),
            output.height.toFloat(),
            settings.adjustments.vignette,
        )
        val grain = createGrainTile()
        drawGrain(
            canvas,
            output.width.toFloat(),
            output.height.toFloat(),
            grain,
            settings.adjustments.grain,
        )
        grain.recycle()
        onProgress(94)
        return output
    }

    private fun destinationRect(
        sourceWidth: Int,
        sourceHeight: Int,
        frameWidth: Float,
        frameHeight: Float,
        crop: EditorCrop,
    ): RectF {
        val cover = max(frameWidth / sourceWidth, frameHeight / sourceHeight)
        val drawScale = cover * crop.zoom.coerceIn(1f, EditorMaxZoom)
        val displayWidth = sourceWidth * drawScale
        val displayHeight = sourceHeight * drawScale
        val maxX = ((displayWidth - frameWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((displayHeight - frameHeight) / 2f).coerceAtLeast(0f)
        val left = ((frameWidth - displayWidth) / 2f + crop.offsetX * frameWidth)
            .coerceIn(-maxX, 0f)
        val top = ((frameHeight - displayHeight) / 2f + crop.offsetY * frameHeight)
            .coerceIn(-maxY, 0f)
        return RectF(left, top, left + displayWidth, top + displayHeight)
    }

    private fun cropRect(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        crop: EditorCrop,
    ): CropRect {
        val cover = max(
            targetWidth.toFloat() / sourceWidth,
            targetHeight.toFloat() / sourceHeight,
        )
        val drawScale = cover * crop.zoom.coerceIn(1f, EditorMaxZoom)
        val displayWidth = sourceWidth * drawScale
        val displayHeight = sourceHeight * drawScale
        val maxX = ((displayWidth - targetWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((displayHeight - targetHeight) / 2f).coerceAtLeast(0f)
        val left = ((targetWidth - displayWidth) / 2f + crop.offsetX * targetWidth)
            .coerceIn(-maxX, 0f)
        val top = ((targetHeight - displayHeight) / 2f + crop.offsetY * targetHeight)
            .coerceIn(-maxY, 0f)
        val sourceLeft = (-left / drawScale).coerceIn(0f, sourceWidth.toFloat())
        val sourceTop = (-top / drawScale).coerceIn(0f, sourceHeight.toFloat())
        val sourceRight = ((targetWidth - left) / drawScale)
            .coerceIn(0f, sourceWidth.toFloat())
        val sourceBottom = ((targetHeight - top) / drawScale)
            .coerceIn(0f, sourceHeight.toFloat())
        val leftInt = sourceLeft.roundToInt().coerceIn(0, sourceWidth - 1)
        val topInt = sourceTop.roundToInt().coerceIn(0, sourceHeight - 1)
        val rightInt = sourceRight.roundToInt().coerceIn(leftInt + 1, sourceWidth)
        val bottomInt = sourceBottom.roundToInt().coerceIn(topInt + 1, sourceHeight)
        return CropRect(leftInt, topInt, rightInt, bottomInt)
    }

    private fun decodeCropRegion(
        file: File,
        targetWidth: Int,
        targetHeight: Int,
        crop: EditorCrop,
    ): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw IOException("La imagen no se pudo decodificar.")
        }
        val regionRect = cropRect(
            sourceWidth,
            sourceHeight,
            targetWidth,
            targetHeight,
            crop,
        )
        val sample = calculateInSampleSize(
            regionRect.width,
            regionRect.height,
            targetWidth,
            targetHeight,
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = runCatchingRegion(file, regionRect, options)
            ?: decodeWholeAndCrop(file, regionRect, sample, options)
        if (decoded.width == targetWidth && decoded.height == targetHeight) {
            return decoded
        }
        val scaled = decoded.scale(targetWidth, targetHeight, true)
        if (scaled !== decoded) {
            decoded.recycle()
        }
        return scaled
    }

    private fun runCatchingRegion(
        file: File,
        rect: CropRect,
        options: BitmapFactory.Options,
    ): Bitmap? {
        return try {
            val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false)
            try {
                decoder.decodeRegion(
                    Rect(rect.left, rect.top, rect.right, rect.bottom),
                    options,
                )
            } finally {
                @Suppress("DEPRECATION")
                runCatching { decoder.recycle() }
            }
        } catch (error: Exception) {
            null
        }
    }

    private fun decodeWholeAndCrop(
        file: File,
        rect: CropRect,
        sample: Int,
        options: BitmapFactory.Options,
    ): Bitmap {
        val full = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IOException("La imagen no se pudo decodificar.")
        val x = (rect.left / sample).coerceIn(0, (full.width - 1).coerceAtLeast(0))
        val y = (rect.top / sample).coerceIn(0, (full.height - 1).coerceAtLeast(0))
        val width = (rect.width / sample).coerceIn(1, full.width - x)
        val height = (rect.height / sample).coerceIn(1, full.height - y)
        val cropped = Bitmap.createBitmap(full, x, y, width, height)
        if (cropped !== full) {
            full.recycle()
        }
        return cropped
    }

    private fun drawVignette(canvas: Canvas, width: Float, height: Float, amount: Float) {
        val strength = amount.coerceIn(0f, 1f)
        if (strength <= 0f) return
        val alpha = (strength * 210f).roundToInt().coerceIn(0, 255)
        val radius = max(width, height) * 0.72f
        val shader = RadialGradient(
            width / 2f,
            height / 2f,
            radius,
            intArrayOf(0x00000000, 0x00000000, alpha shl 24),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun drawGrain(
        canvas: Canvas,
        width: Float,
        height: Float,
        grain: Bitmap?,
        amount: Float,
    ) {
        val strength = amount.coerceIn(0f, 1f)
        if (grain == null || strength <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            alpha = (strength * 70f).roundToInt().coerceIn(0, 255)
        }
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    /** Box blur de una pasada por eje sobre una copia chica. */
    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0 || width <= 0 || height <= 0) return
        val diameter = radius * 2 + 1
        val temporary = IntArray(pixels.size)

        for (y in 0 until height) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val pixel = pixels[y * width + offset.coerceIn(0, width - 1)]
                alpha += (pixel ushr 24) and 0xFF
                red += (pixel ushr 16) and 0xFF
                green += (pixel ushr 8) and 0xFF
                blue += pixel and 0xFF
            }
            for (x in 0 until width) {
                temporary[y * width + x] =
                    ((alpha / diameter) shl 24) or
                        ((red / diameter) shl 16) or
                        ((green / diameter) shl 8) or
                        (blue / diameter)
                val remove = pixels[
                    y * width + (x - radius).coerceIn(0, width - 1)
                ]
                val add = pixels[
                    y * width + (x + radius + 1).coerceIn(0, width - 1)
                ]
                alpha += ((add ushr 24) and 0xFF) - ((remove ushr 24) and 0xFF)
                red += ((add ushr 16) and 0xFF) - ((remove ushr 16) and 0xFF)
                green += ((add ushr 8) and 0xFF) - ((remove ushr 8) and 0xFF)
                blue += (add and 0xFF) - (remove and 0xFF)
            }
        }

        for (x in 0 until width) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val pixel = temporary[offset.coerceIn(0, height - 1) * width + x]
                alpha += (pixel ushr 24) and 0xFF
                red += (pixel ushr 16) and 0xFF
                green += (pixel ushr 8) and 0xFF
                blue += pixel and 0xFF
            }
            for (y in 0 until height) {
                pixels[y * width + x] =
                    ((alpha / diameter) shl 24) or
                        ((red / diameter) shl 16) or
                        ((green / diameter) shl 8) or
                        (blue / diameter)
                val remove = temporary[
                    (y - radius).coerceIn(0, height - 1) * width + x
                ]
                val add = temporary[
                    (y + radius + 1).coerceIn(0, height - 1) * width + x
                ]
                alpha += ((add ushr 24) and 0xFF) - ((remove ushr 24) and 0xFF)
                red += ((add ushr 16) and 0xFF) - ((remove ushr 16) and 0xFF)
                green += ((add ushr 8) and 0xFF) - ((remove ushr 8) and 0xFF)
                blue += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }
}
