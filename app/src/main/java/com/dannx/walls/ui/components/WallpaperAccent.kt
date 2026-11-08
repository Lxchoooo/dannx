package com.dannx.walls.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.core.graphics.get
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Saca un color representativo de la pared para teñir detalles de la pantalla.
 * Se calcula una sola vez por imagen y arranca con el color de la app.
 */
@Composable
fun rememberWallpaperAccent(url: String, fallback: Color): Color {
    val context = LocalContext.current
    var accent by remember(url) { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        accent = extractAccent(context, url) ?: fallback
    }
    return accent
}

private suspend fun extractAccent(context: Context, url: String): Color? {
    if (url.isBlank()) return null
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(64)
        .allowHardware(false)
        .build()
    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
    val bitmap = (result as? SuccessResult)?.drawable?.toBitmapOrNull() ?: return null
    return withContext(Dispatchers.Default) { averageColor(bitmap) }
}

private fun averageColor(bitmap: Bitmap): Color {
    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var samples = 0
    val stepX = max(1, bitmap.width / 24)
    val stepY = max(1, bitmap.height / 24)
    var y = stepY / 2
    while (y < bitmap.height) {
        var x = stepX / 2
        while (x < bitmap.width) {
            val pixel = bitmap[x, y]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha > 40) {
                red += (pixel shr 16) and 0xFF
                green += (pixel shr 8) and 0xFF
                blue += pixel and 0xFF
                samples++
            }
            x += stepX
        }
        y += stepY
    }
    if (samples == 0) return Color(0xFF4C5BD4)

    val base = Color(
        red = (red / samples / 255.0).toFloat(),
        green = (green / samples / 255.0).toFloat(),
        blue = (blue / samples / 255.0).toFloat(),
    )
    // Se sube el color para que se note sobre la foto sin perder legibilidad.
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgbInt(), hsv)
    hsv[1] = max(hsv[1], 0.45f)
    hsv[2] = hsv[2].coerceIn(0.55f, 0.85f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
