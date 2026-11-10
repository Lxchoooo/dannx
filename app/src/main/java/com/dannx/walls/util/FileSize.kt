package com.dannx.walls.util

import java.util.Locale

/** Formatea un tamaño en bytes con la unidad más legible. */
fun formatFileSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val safe = bytes.coerceAtLeast(0L)
    if (safe < 1024L) return "$safe B"

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = safe.toDouble()
    var index = -1
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    val pattern = if (value >= 100.0 || value % 1.0 == 0.0) "%.0f %s" else "%.1f %s"
    return String.format(locale, pattern, value, units[index])
}
