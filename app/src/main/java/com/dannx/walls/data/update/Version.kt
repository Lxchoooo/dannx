package com.dannx.walls.data.update

/**
 * Versión semántica simple, suficiente para comparar releases de GitHub.
 * Acepta `1.6.0`, `v1.6.0` y sufijos como `1.6.0-beta.1`.
 */
data class AppVersion(
    val parts: List<Int>,
    val raw: String,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (index in 0 until size) {
            val left = parts.getOrElse(index) { 0 }
            val right = other.parts.getOrElse(index) { 0 }
            val difference = left.compareTo(right)
            if (difference != 0) return difference
        }
        return 0
    }

    fun isNewerThan(other: AppVersion): Boolean = compareTo(other) > 0

    companion object {
        fun parse(value: String): AppVersion? {
            val clean = value.trim().removePrefix("v").removePrefix("V")
            if (clean.isBlank()) return null
            val numeric = clean.substringBefore('-').substringBefore('+')
            val pieces = numeric.split('.')
            if (pieces.isEmpty()) return null
            val numbers = ArrayList<Int>(pieces.size)
            for (piece in pieces) {
                numbers += piece.toIntOrNull() ?: return null
            }
            return AppVersion(
                parts = numbers,
                raw = clean,
            )
        }
    }
}
