package com.dannx.walls.util

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import coil.size.Size
import coil.transform.Transformation

/**
 * Redondea las esquinas de la imagen al decodificarla, en vez de usar
 * `Modifier.clip` durante el dibujado.
 *
 * Ventaja: el recorte no se recalcula en cada frame del scroll.
 *
 * Contras (importante): en Coil 2 una transformacion obliga a decodificar en
 * software (no se pueden usar bitmaps por hardware), y en un equipo modesto
 * eso cuesta mas.scroll que el `clip` de Compose. Por eso queda disponible pero
 * no se aplica por defecto en las imagenes grandes.
 */
class RoundedCornersTransformation(
    private val radiusPx: Float,
) : Transformation {
    override val cacheKey: String = "rounded-corners-$radiusPx"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (input.width <= 0 || input.height <= 0 || radiusPx <= 0f) {
            return input
        }
        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(input, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val bounds = RectF(0f, 0f, input.width.toFloat(), input.height.toFloat())
        val radius = radiusPx.coerceAtMost(minOf(input.width, input.height) / 2f)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        return output
    }
}
