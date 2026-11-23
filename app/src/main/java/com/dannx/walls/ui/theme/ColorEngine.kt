package com.dannx.walls.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.dannx.walls.data.ColorContrast
import com.dannx.walls.data.ColorPaletteStyle
import com.materialkolor.InternalMaterialKolorApi
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeCmf
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/** Semilla cálida para equipos sin color dinámico ni fondo legible. */
internal val DefaultSeed = Color(0xFFFF8A00)

/** Convierte el hex guardado en Settings en un color; null si no es válido. */
internal fun parseSeedColor(value: String): Color? {
    val hex = value.removePrefix("#").trim()
    if (hex.length != 6) return null
    val rgb = hex.toIntOrNull(16) ?: return null
    return Color(0xFF000000.toInt() or rgb)
}

/**
 * Genera el esquema completo a partir de un color semilla y un estilo de
 * paleta, usando el motor oficial de Material Color Utilities.
 *
 * CMF trabaja con dos fuentes: la semilla y un análogo a 60° de tono. Así el
 * esquema conserva la identidad del color elegido y gana el contraste cálido
 * que distingue ese estilo.
 */
@OptIn(InternalMaterialKolorApi::class)
internal fun materialScheme(
    seed: Color,
    dark: Boolean,
    style: ColorPaletteStyle,
    contrast: ColorContrast,
    amoled: Boolean,
): ColorScheme {
    val spec = ColorSpec.SpecVersion.SPEC_2025
    val source = Hct.fromInt(seed.toArgb())
    val sources = if (style == ColorPaletteStyle.CMF) {
        listOf(
            source,
            Hct.from(source.hue + 60.0, source.chroma, source.tone),
        )
    } else {
        listOf(source)
    }
    val level = contrast.value
    val scheme: DynamicScheme = when (style) {
        ColorPaletteStyle.TONAL_SPOT -> SchemeTonalSpot(sources, dark, level, spec)
        ColorPaletteStyle.VIBRANT -> SchemeVibrant(sources, dark, level, spec)
        ColorPaletteStyle.EXPRESSIVE -> SchemeExpressive(sources, dark, level, spec)
        ColorPaletteStyle.NEUTRAL -> SchemeNeutral(sources, dark, level, spec)
        ColorPaletteStyle.MONOCHROME -> SchemeMonochrome(sources, dark, level, spec)
        ColorPaletteStyle.RAINBOW -> SchemeRainbow(sources, dark, level, spec)
        ColorPaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(sources, dark, level, spec)
        ColorPaletteStyle.FIDELITY -> SchemeFidelity(sources, dark, level, spec)
        ColorPaletteStyle.CONTENT -> SchemeContent(sources, dark, level, spec)
        ColorPaletteStyle.CMF -> SchemeCmf(sources, dark, level, ColorSpec.SpecVersion.SPEC_2026)
    }
    return scheme.toComposeScheme(amoled)
}

/**
 * Pasa un [DynamicScheme] a los roles que consume Compose Material 3.
 */
private fun DynamicScheme.toComposeScheme(amoled: Boolean): ColorScheme {
    val colors = MaterialDynamicColors()
    fun role(value: DynamicColor): Color = Color(value.getArgb(this))
    val scheme = ColorScheme(
        primary = role(colors.primary),
        onPrimary = role(colors.onPrimary),
        primaryContainer = role(colors.primaryContainer),
        onPrimaryContainer = role(colors.onPrimaryContainer),
        inversePrimary = role(colors.inversePrimary),
        secondary = role(colors.secondary),
        onSecondary = role(colors.onSecondary),
        secondaryContainer = role(colors.secondaryContainer),
        onSecondaryContainer = role(colors.onSecondaryContainer),
        tertiary = role(colors.tertiary),
        onTertiary = role(colors.onTertiary),
        tertiaryContainer = role(colors.tertiaryContainer),
        onTertiaryContainer = role(colors.onTertiaryContainer),
        background = role(colors.background),
        onBackground = role(colors.onBackground),
        surface = role(colors.surface),
        onSurface = role(colors.onSurface),
        surfaceVariant = role(colors.surfaceVariant),
        onSurfaceVariant = role(colors.onSurfaceVariant),
        surfaceTint = role(colors.surfaceTint),
        inverseSurface = role(colors.inverseSurface),
        inverseOnSurface = role(colors.inverseOnSurface),
        error = role(colors.error),
        onError = role(colors.onError),
        errorContainer = role(colors.errorContainer),
        onErrorContainer = role(colors.onErrorContainer),
        outline = role(colors.outline),
        outlineVariant = role(colors.outlineVariant),
        scrim = role(colors.scrim),
        surfaceBright = role(colors.surfaceBright),
        surfaceDim = role(colors.surfaceDim),
        surfaceContainer = role(colors.surfaceContainer),
        surfaceContainerHigh = role(colors.surfaceContainerHigh),
        surfaceContainerHighest = role(colors.surfaceContainerHighest),
        surfaceContainerLow = role(colors.surfaceContainerLow),
        surfaceContainerLowest = role(colors.surfaceContainerLowest),
        primaryFixed = role(colors.primaryFixed),
        primaryFixedDim = role(colors.primaryFixedDim),
        onPrimaryFixed = role(colors.onPrimaryFixed),
        onPrimaryFixedVariant = role(colors.onPrimaryFixedVariant),
        secondaryFixed = role(colors.secondaryFixed),
        secondaryFixedDim = role(colors.secondaryFixedDim),
        onSecondaryFixed = role(colors.onSecondaryFixed),
        onSecondaryFixedVariant = role(colors.onSecondaryFixedVariant),
        tertiaryFixed = role(colors.tertiaryFixed),
        tertiaryFixedDim = role(colors.tertiaryFixedDim),
        onTertiaryFixed = role(colors.onTertiaryFixed),
        onTertiaryFixedVariant = role(colors.onTertiaryFixedVariant),
    )
    if (!amoled) return scheme
    // Negro puro: el fondo y las superficies más bajas dejan de iluminar la
    // pantalla al leer de noche, sin tocar los roles de acento.
    return scheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
    )
}

/** Mezcla dos esquemas para animar el cambio de color sin saltos. */
internal fun interpolateScheme(
    from: ColorScheme,
    to: ColorScheme,
    fraction: Float,
): ColorScheme {
    fun mix(a: Color, b: Color): Color = lerp(a, b, fraction)
    return to.copy(
        primary = mix(from.primary, to.primary),
        onPrimary = mix(from.onPrimary, to.onPrimary),
        primaryContainer = mix(from.primaryContainer, to.primaryContainer),
        onPrimaryContainer = mix(from.onPrimaryContainer, to.onPrimaryContainer),
        inversePrimary = mix(from.inversePrimary, to.inversePrimary),
        secondary = mix(from.secondary, to.secondary),
        onSecondary = mix(from.onSecondary, to.onSecondary),
        secondaryContainer = mix(from.secondaryContainer, to.secondaryContainer),
        onSecondaryContainer = mix(from.onSecondaryContainer, to.onSecondaryContainer),
        tertiary = mix(from.tertiary, to.tertiary),
        onTertiary = mix(from.onTertiary, to.onTertiary),
        tertiaryContainer = mix(from.tertiaryContainer, to.tertiaryContainer),
        onTertiaryContainer = mix(from.onTertiaryContainer, to.onTertiaryContainer),
        background = mix(from.background, to.background),
        onBackground = mix(from.onBackground, to.onBackground),
        surface = mix(from.surface, to.surface),
        onSurface = mix(from.onSurface, to.onSurface),
        surfaceVariant = mix(from.surfaceVariant, to.surfaceVariant),
        onSurfaceVariant = mix(from.onSurfaceVariant, to.onSurfaceVariant),
        surfaceTint = mix(from.surfaceTint, to.surfaceTint),
        inverseSurface = mix(from.inverseSurface, to.inverseSurface),
        inverseOnSurface = mix(from.inverseOnSurface, to.inverseOnSurface),
        error = mix(from.error, to.error),
        onError = mix(from.onError, to.onError),
        errorContainer = mix(from.errorContainer, to.errorContainer),
        onErrorContainer = mix(from.onErrorContainer, to.onErrorContainer),
        outline = mix(from.outline, to.outline),
        outlineVariant = mix(from.outlineVariant, to.outlineVariant),
        scrim = mix(from.scrim, to.scrim),
        surfaceBright = mix(from.surfaceBright, to.surfaceBright),
        surfaceDim = mix(from.surfaceDim, to.surfaceDim),
        surfaceContainer = mix(from.surfaceContainer, to.surfaceContainer),
        surfaceContainerHigh = mix(from.surfaceContainerHigh, to.surfaceContainerHigh),
        surfaceContainerHighest = mix(from.surfaceContainerHighest, to.surfaceContainerHighest),
        surfaceContainerLow = mix(from.surfaceContainerLow, to.surfaceContainerLow),
        surfaceContainerLowest = mix(from.surfaceContainerLowest, to.surfaceContainerLowest),
        primaryFixed = mix(from.primaryFixed, to.primaryFixed),
        primaryFixedDim = mix(from.primaryFixedDim, to.primaryFixedDim),
        onPrimaryFixed = mix(from.onPrimaryFixed, to.onPrimaryFixed),
        onPrimaryFixedVariant = mix(from.onPrimaryFixedVariant, to.onPrimaryFixedVariant),
        secondaryFixed = mix(from.secondaryFixed, to.secondaryFixed),
        secondaryFixedDim = mix(from.secondaryFixedDim, to.secondaryFixedDim),
        onSecondaryFixed = mix(from.onSecondaryFixed, to.onSecondaryFixed),
        onSecondaryFixedVariant = mix(from.onSecondaryFixedVariant, to.onSecondaryFixedVariant),
        tertiaryFixed = mix(from.tertiaryFixed, to.tertiaryFixed),
        tertiaryFixedDim = mix(from.tertiaryFixedDim, to.tertiaryFixedDim),
        onTertiaryFixed = mix(from.onTertiaryFixed, to.onTertiaryFixed),
        onTertiaryFixedVariant = mix(from.onTertiaryFixedVariant, to.onTertiaryFixedVariant),
    )
}
