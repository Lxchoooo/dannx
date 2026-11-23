package com.dannx.walls.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dannx.walls.R

/**
 * Google Sans Flex es la tipografía de Material 3 Expressive y la licencia
 * OFL permite incluirla. Los tres pesos alcanzan para toda la escala: regular
 * para cuerpos, medium para títulos y bold para etiquetas y énfasis.
 */
private val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_bold, FontWeight.Bold),
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = GoogleSansFlex,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

private val Regular = FontWeight.Normal
private val Medium = FontWeight.Medium
private val Bold = FontWeight.Bold

/**
 * Escala tipográfica expresiva: tamaños y alturas de línea del spec, más los
 * quince estilos *emphasized* que crean contraste de peso sin cambiar el
 * layout.
 */
internal val DannXTypography: Typography = Typography().copy(
    displayLarge = style(57, 64, Regular),
    displayMedium = style(45, 52, Regular),
    displaySmall = style(36, 44, Regular),
    headlineLarge = style(32, 40, Regular),
    headlineMedium = style(28, 36, Regular),
    headlineSmall = style(24, 32, Regular),
    titleLarge = style(22, 28, Regular),
    titleMedium = style(16, 24, Medium),
    titleSmall = style(14, 20, Medium),
    bodyLarge = style(16, 24, Regular),
    bodyMedium = style(14, 20, Regular),
    bodySmall = style(12, 16, Regular, tracking = 0.1),
    labelLarge = style(14, 20, Medium),
    labelMedium = style(12, 16, Medium, tracking = 0.1),
    labelSmall = style(11, 16, Medium, tracking = 0.1),

    displayLargeEmphasized = style(57, 64, Medium),
    displayMediumEmphasized = style(45, 52, Medium),
    displaySmallEmphasized = style(36, 44, Medium),
    headlineLargeEmphasized = style(32, 40, Medium),
    headlineMediumEmphasized = style(28, 36, Medium),
    headlineSmallEmphasized = style(24, 32, Medium),
    titleLargeEmphasized = style(22, 28, Medium),
    titleMediumEmphasized = style(16, 24, Bold),
    titleSmallEmphasized = style(14, 20, Bold),
    bodyLargeEmphasized = style(16, 24, Medium),
    bodyMediumEmphasized = style(14, 20, Medium),
    bodySmallEmphasized = style(12, 16, Medium, tracking = 0.1),
    labelLargeEmphasized = style(14, 20, Bold),
    labelMediumEmphasized = style(12, 16, Bold, tracking = 0.1),
    labelSmallEmphasized = style(11, 16, Bold, tracking = 0.1),
)
