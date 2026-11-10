package com.dannx.walls.data

import java.text.Normalizer
import java.util.Locale

/**
 * Paleta del filtro por color del feed. No es el color exacto de cada fondo:
 * es una categoría lo bastante amplia para que el filtro sea útil.
 */
enum class WallpaperColor(val key: String) {
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    GREEN("green"),
    BLUE("blue"),
    PURPLE("purple"),
    PINK("pink"),
    BLACK("black"),
    GRAY("gray"),
    ;

    companion object {
        fun fromKey(value: String?): WallpaperColor? =
            entries.firstOrNull { it.key == value }
    }
}

private val accents = "\\p{M}+".toRegex()

private fun normalizeColorText(value: String): String =
    Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(accents, "")

private val tagColors = mapOf(
    "rojo" to WallpaperColor.RED,
    "red" to WallpaperColor.RED,
    "granate" to WallpaperColor.RED,
    "carmesi" to WallpaperColor.RED,
    "naranja" to WallpaperColor.ORANGE,
    "orange" to WallpaperColor.ORANGE,
    "ambar" to WallpaperColor.ORANGE,
    "amarillo" to WallpaperColor.YELLOW,
    "yellow" to WallpaperColor.YELLOW,
    "dorado" to WallpaperColor.YELLOW,
    "gold" to WallpaperColor.YELLOW,
    "verde" to WallpaperColor.GREEN,
    "green" to WallpaperColor.GREEN,
    "esmeralda" to WallpaperColor.GREEN,
    "azul" to WallpaperColor.BLUE,
    "blue" to WallpaperColor.BLUE,
    "celeste" to WallpaperColor.BLUE,
    "marino" to WallpaperColor.BLUE,
    "morado" to WallpaperColor.PURPLE,
    "violeta" to WallpaperColor.PURPLE,
    "purple" to WallpaperColor.PURPLE,
    "lila" to WallpaperColor.PURPLE,
    "rosa" to WallpaperColor.PINK,
    "rosado" to WallpaperColor.PINK,
    "pink" to WallpaperColor.PINK,
    "magenta" to WallpaperColor.PINK,
    "negro" to WallpaperColor.BLACK,
    "black" to WallpaperColor.BLACK,
    "gris" to WallpaperColor.GRAY,
    "gray" to WallpaperColor.GRAY,
    "grey" to WallpaperColor.GRAY,
    "blanco" to WallpaperColor.GRAY,
    "white" to WallpaperColor.GRAY,
)

/**
 * Si el fondo ya trae un tag de color, ese manda y no hace falta analizar la
 * imagen. Así el filtro respeta la intención de quien lo subió.
 */
fun colorFromTags(tags: List<String>): WallpaperColor? =
    tags.firstNotNullOfOrNull { tagColors[normalizeColorText(it)] }

/** Clasifica un píxel ARGB en la paleta del filtro. */
fun colorBucketFor(argb: Int): WallpaperColor {
    val red = (argb shr 16 and 0xFF) / 255f
    val green = (argb shr 8 and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f

    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min

    // Los tonos muy oscuros se leen como negro aunque tengan algo de matiz.
    if (max <= 0.16f) return WallpaperColor.BLACK

    val saturation = if (max <= 0f) 0f else delta / max
    // Blancos, grises y tonos lavados comparten la misma categoría.
    if (saturation <= 0.14f) return WallpaperColor.GRAY

    val hue = when {
        delta <= 0f -> 0f
        max == red -> 60f * (((green - blue) / delta) % 6f)
        max == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    return when {
        hue < 12f || hue >= 350f -> WallpaperColor.RED
        hue < 40f -> WallpaperColor.ORANGE
        hue < 68f -> WallpaperColor.YELLOW
        hue < 165f -> WallpaperColor.GREEN
        hue < 260f -> WallpaperColor.BLUE
        hue < 300f -> WallpaperColor.PURPLE
        else -> WallpaperColor.PINK
    }
}

/**
 * Reduce una imagen a su color dominante. Agrupa los píxeles en cubos de 4 bits
 * por canal, elige el cubo con más presencia y promedia ese grupo: así una
 * fotografía con muchos matices no se convierte en un gris promedio.
 */
fun dominantColorBucket(pixels: IntArray): WallpaperColor? {
    if (pixels.isEmpty()) return null
    val bins = HashMap<Int, LongArray>(256)
    for (pixel in pixels) {
        if ((pixel ushr 24) < 128) continue
        val red = pixel shr 16 and 0xFF
        val green = pixel shr 8 and 0xFF
        val blue = pixel and 0xFF
        val key = (red shr 4 shl 8) or (green shr 4 shl 4) or (blue shr 4)
        val bin = bins.getOrPut(key) { LongArray(4) }
        bin[0]++
        bin[1] += red
        bin[2] += green
        bin[3] += blue
    }
    val best = bins.values.maxByOrNull { it[0] } ?: return null
    val count = best[0].coerceAtLeast(1)
    val average = (0xFF shl 24) or
        ((best[1] / count).toInt() shl 16) or
        ((best[2] / count).toInt() shl 8) or
        (best[3] / count).toInt()
    return colorBucketFor(average)
}
