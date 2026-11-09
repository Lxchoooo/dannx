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
    private const val BlurPreviewMaxSide = 480
    private const val BlurFinalMaxSide = 512
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

    /**
     * Pila de niveles desenfocados, del más suave al más fuerte. Se calcula una
     * sola vez por imagen: la intensidad solo elige qué niveles se mezclan.
     */
    fun createBlurLevels(
        source: Bitmap,
        maxSide: Int = BlurPreviewMaxSide,
    ): List<Bitmap> {
        val scaled = downscalePixels(source, maxSide)
        val radii = EditorBlur.radiiFor(min(scaled.width, scaled.height))
        val levels = ArrayList<Bitmap>(radii.size)
        try {
            radii.forEach { radius ->
                val pixels = scaled.pixels.copyOf()
                EditorBlur.boxBlur(pixels, scaled.width, scaled.height, radius)
                levels += bitmapFrom(pixels, scaled.width, scaled.height)
            }
        } catch (error: Throwable) {
            levels.forEach { it.recycle() }
            throw error
        }
        return levels
    }

    /**
     * Dibuja el desenfoque de una intensidad concreta sobre [canvas]. Genera
     * cada nivel, lo pinta y lo recicla enseguida: nunca hay dos bitmaps
     * grandes vivos a la vez.
     */
    fun drawBlurred(
        canvas: Canvas,
        source: Bitmap,
        blur: Float,
        maxSide: Int = BlurFinalMaxSide,
    ) {
        val mix = EditorBlur.mixFor(blur) ?: return
        val destination = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        val scaled = downscalePixels(source, maxSide)
        val radii = EditorBlur.radiiFor(min(scaled.width, scaled.height))
        val indices = listOfNotNull(mix.baseLevel, mix.overlayLevel).distinct().sorted()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        indices.forEach { index ->
            val pixels = scaled.pixels.copyOf()
            EditorBlur.boxBlur(pixels, scaled.width, scaled.height, radii[index])
            val bitmap = bitmapFrom(pixels, scaled.width, scaled.height)
            if (index == mix.overlayLevel) {
                paint.alpha = (mix.overlayAlpha * 255f).roundToInt().coerceIn(0, 255)
            }
            canvas.drawBitmap(bitmap, null, destination, paint)
            paint.alpha = 255
            bitmap.recycle()
        }
    }

    private class ScaledPixels(val pixels: IntArray, val width: Int, val height: Int)

    private fun downscalePixels(source: Bitmap, maxSide: Int): ScaledPixels {
        val longest = max(source.width, source.height)
        val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        val pixels = IntArray(width * height)
        if (width == source.width && height == source.height) {
            source.getPixels(pixels, 0, width, 0, 0, width, height)
        } else {
            val small = source.scale(width, height, true)
            small.getPixels(pixels, 0, width, 0, 0, width, height)
            if (small !== source) small.recycle()
        }
        return ScaledPixels(pixels, width, height)
    }

    private fun bitmapFrom(pixels: IntArray, width: Int, height: Int): Bitmap =
        createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
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
        blurLevels: List<Bitmap>,
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
            // La capa base se dibuja opaca; la siguiente se mezcla encima con
            // alfa, así los pesos suman 1 y no queda imagen doble.
            val mix = if (showOriginal) null else EditorBlur.mixFor(settings.blur)
            val base = mix?.baseLevel?.let { blurLevels.getOrNull(it) } ?: source
            canvas.drawBitmap(base, null, destination, paint)

            val overlay = mix?.overlayLevel?.let { blurLevels.getOrNull(it) }
            if (!showOriginal && overlay != null && mix != null && mix.overlayAlpha > 0f) {
                paint.alpha = (mix.overlayAlpha * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawBitmap(overlay, null, destination, paint)
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
            drawBlurred(canvas, output, settings.blur)
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
}
