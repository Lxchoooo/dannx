package com.dannx.walls.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.data.ColorContrast
import com.dannx.walls.data.ColorPaletteStyle
import com.dannx.walls.data.WALLPAPER_COLOR_SEED
import com.dannx.walls.ui.theme.LocalReduceMotion
import com.dannx.walls.ui.theme.Motion
import com.dannx.walls.ui.theme.MotionDuration
import com.dannx.walls.ui.theme.materialScheme
import com.dannx.walls.ui.theme.rememberSeedColor

/** Un color de partida para generar la paleta. */
private data class ColorPreset(
    val key: String,
    val color: Color,
    @StringRes val label: Int,
)

private val ColorPresets = listOf(
    ColorPreset(WALLPAPER_COLOR_SEED, Color.Transparent, R.string.color_preset_wallpaper),
    ColorPreset("#FF8A00", Color(0xFFFF8A00), R.string.color_preset_amber),
    ColorPreset("#FF6F61", Color(0xFFFF6F61), R.string.color_preset_coral),
    ColorPreset("#9C6BFF", Color(0xFF9C6BFF), R.string.color_preset_violet),
    ColorPreset("#22C7D6", Color(0xFF22C7D6), R.string.color_preset_cyan),
    ColorPreset("#5BCB6A", Color(0xFF5BCB6A), R.string.color_preset_green),
    ColorPreset("#FF77A8", Color(0xFFFF77A8), R.string.color_preset_rose),
    ColorPreset("#5B8DEF", Color(0xFF5B8DEF), R.string.color_preset_blue),
    ColorPreset("#E4572E", Color(0xFFE4572E), R.string.color_preset_red),
)

/**
 * Tarjeta de Ajustes con la semilla de color, el estilo de paleta, el
 * contraste y el negro puro. El selector de estilos se abre en una hoja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSettingsCard(
    colorSeed: String,
    paletteStyle: ColorPaletteStyle,
    contrastLevel: ColorContrast,
    amoled: Boolean,
    darkTheme: Boolean,
    onColorSeedChange: (String) -> Unit,
    onPaletteStyleChange: (ColorPaletteStyle) -> Unit,
    onContrastLevelChange: (ColorContrast) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
) {
    // La muestra "Fondo" siempre enseña el color real del fondo, aunque haya
    // un preset elegido: así se puede volver a él viendo lo que se recupera.
    val wallpaperSeed = rememberSeedColor(WALLPAPER_COLOR_SEED, darkTheme)
    var showStyles by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_color),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Text(
                stringResource(R.string.settings_color_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(ColorPresets, key = { it.key }) { preset ->
                    ColorSwatch(
                        preset = preset,
                        wallpaperSeed = wallpaperSeed,
                        darkTheme = darkTheme,
                        paletteStyle = paletteStyle,
                        selected = colorSeed == preset.key,
                        onClick = { onColorSeedChange(preset.key) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = { showStyles = true },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StylePreviewDot(
                        seed = wallpaperSeed,
                        style = paletteStyle,
                        darkTheme = darkTheme,
                        size = 36.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_color_style),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(paletteStyle.labelRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.settings_color_contrast),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ColorContrast.entries.forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = contrastLevel == level,
                        onClick = { onContrastLevelChange(level) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ColorContrast.entries.size,
                        ),
                        label = { Text(stringResource(level.labelRes())) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = amoled,
                        role = Role.Switch,
                        onValueChange = onAmoledChange,
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_color_amoled),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.settings_color_amoled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = amoled, onCheckedChange = null)
            }
        }
    }
    if (showStyles) {
        ColorStyleSheet(
            seed = wallpaperSeed,
            darkTheme = darkTheme,
            selected = paletteStyle,
            onSelect = onPaletteStyleChange,
            onDismiss = { showStyles = false },
        )
    }
}

@Composable
private fun ColorSwatch(
    preset: ColorPreset,
    wallpaperSeed: Color,
    darkTheme: Boolean,
    paletteStyle: ColorPaletteStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(preset.label)
    val reduceMotion = LocalReduceMotion.current
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 1.dp,
        animationSpec = Motion.fade(reduceMotion, MotionDuration.Medium),
        label = "swatchRingWidth",
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = Motion.fade(reduceMotion, MotionDuration.Medium),
        label = "swatchRingColor",
    )
    val fill = if (preset.key == WALLPAPER_COLOR_SEED) {
        val scheme = remember(preset.key, wallpaperSeed, darkTheme, paletteStyle) {
            materialScheme(
                seed = wallpaperSeed,
                dark = darkTheme,
                style = paletteStyle,
                contrast = ColorContrast.STANDARD,
                amoled = false,
            )
        }
        Brush.sweepGradient(
            listOf(
                scheme.primary,
                scheme.tertiary,
                scheme.secondary,
                scheme.primaryContainer,
            ),
        )
    } else {
        Brush.linearGradient(listOf(preset.color, preset.color))
    }
    Column(
        modifier = Modifier.width(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = label
                }
                .border(
                    width = ringWidth,
                    color = ringColor,
                    shape = CircleShape,
                )
                .padding(3.dp)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .background(fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val tone = if (preset.color == Color.Transparent) {
                MaterialTheme.colorScheme.onPrimary
            } else if (preset.color.luminance() > 0.5f) {
                Color.Black
            } else {
                Color.White
            }
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(Motion.fade(reduceMotion, MotionDuration.Fast)) +
                    scaleIn(
                        animationSpec = Motion.bouncy(reduceMotion),
                        initialScale = 0.4f,
                    ),
                exit = fadeOut(Motion.fade(reduceMotion, MotionDuration.Fast)) +
                    scaleOut(
                        animationSpec = Motion.fade(reduceMotion, MotionDuration.Fast),
                        targetScale = 0.4f,
                    ),
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorStyleSheet(
    seed: Color,
    darkTheme: Boolean,
    selected: ColorPaletteStyle,
    onSelect: (ColorPaletteStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_color_style),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.settings_color_style_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorPaletteStyle.entries.forEach { style ->
                    StyleCard(
                        style = style,
                        seed = seed,
                        darkTheme = darkTheme,
                        selected = selected == style,
                        onClick = {
                            onSelect(style)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleCard(
    style: ColorPaletteStyle,
    seed: Color,
    darkTheme: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StylePreviewDot(seed = seed, style = style, darkTheme = darkTheme, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(style.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(style.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Círculo de cuatro cuadrantes que resume la paleta de un estilo. */
@Composable
private fun StylePreviewDot(
    seed: Color,
    style: ColorPaletteStyle,
    darkTheme: Boolean,
    size: Dp,
) {
    val scheme = remember(seed, style, darkTheme) {
        materialScheme(
            seed = seed,
            dark = darkTheme,
            style = style,
            contrast = ColorContrast.STANDARD,
            amoled = false,
        )
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest),
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(scheme.primary),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(scheme.tertiary),
                )
            }
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(scheme.secondary),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(scheme.primaryContainer),
                )
            }
        }
    }
}

@StringRes
internal fun ColorPaletteStyle.labelRes(): Int = when (this) {
    ColorPaletteStyle.TONAL_SPOT -> R.string.color_style_tonal_spot
    ColorPaletteStyle.VIBRANT -> R.string.color_style_vibrant
    ColorPaletteStyle.EXPRESSIVE -> R.string.color_style_expressive
    ColorPaletteStyle.NEUTRAL -> R.string.color_style_neutral
    ColorPaletteStyle.MONOCHROME -> R.string.color_style_monochrome
    ColorPaletteStyle.RAINBOW -> R.string.color_style_rainbow
    ColorPaletteStyle.FRUIT_SALAD -> R.string.color_style_fruit_salad
    ColorPaletteStyle.FIDELITY -> R.string.color_style_fidelity
    ColorPaletteStyle.CONTENT -> R.string.color_style_content
    ColorPaletteStyle.CMF -> R.string.color_style_cmf
}

@StringRes
internal fun ColorPaletteStyle.descriptionRes(): Int = when (this) {
    ColorPaletteStyle.TONAL_SPOT -> R.string.color_style_tonal_spot_desc
    ColorPaletteStyle.VIBRANT -> R.string.color_style_vibrant_desc
    ColorPaletteStyle.EXPRESSIVE -> R.string.color_style_expressive_desc
    ColorPaletteStyle.NEUTRAL -> R.string.color_style_neutral_desc
    ColorPaletteStyle.MONOCHROME -> R.string.color_style_monochrome_desc
    ColorPaletteStyle.RAINBOW -> R.string.color_style_rainbow_desc
    ColorPaletteStyle.FRUIT_SALAD -> R.string.color_style_fruit_salad_desc
    ColorPaletteStyle.FIDELITY -> R.string.color_style_fidelity_desc
    ColorPaletteStyle.CONTENT -> R.string.color_style_content_desc
    ColorPaletteStyle.CMF -> R.string.color_style_cmf_desc
}

@StringRes
private fun ColorContrast.labelRes(): Int = when (this) {
    ColorContrast.STANDARD -> R.string.color_contrast_standard
    ColorContrast.MEDIUM -> R.string.color_contrast_medium
    ColorContrast.HIGH -> R.string.color_contrast_high
}
