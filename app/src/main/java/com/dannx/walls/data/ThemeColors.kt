package com.dannx.walls.data

/**
 * Estilo de la paleta Material 3. Se guarda por nombre para que el ajuste
 * sobreviva a futuras variantes nuevas.
 */
enum class ColorPaletteStyle {
    TONAL_SPOT,
    VIBRANT,
    EXPRESSIVE,
    NEUTRAL,
    MONOCHROME,
    RAINBOW,
    FRUIT_SALAD,
    FIDELITY,
    CONTENT,
    CMF;

    companion object {
        fun fromName(name: String?): ColorPaletteStyle =
            entries.firstOrNull { it.name == name } ?: TONAL_SPOT
    }
}

/** Nivel de contraste del esquema generado. */
enum class ColorContrast(val value: Double) {
    STANDARD(0.0),
    MEDIUM(0.5),
    HIGH(1.0);

    companion object {
        fun fromName(name: String?): ColorContrast =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

/** Semilla vacía: se usa el color del fondo de pantalla. */
const val WALLPAPER_COLOR_SEED = ""
