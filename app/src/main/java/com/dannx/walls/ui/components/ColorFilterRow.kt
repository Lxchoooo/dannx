package com.dannx.walls.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.data.WallpaperColor

/**
 * Fila de círculos para filtrar el feed por color. Tocar el color activo lo
 * deselecciona; la decisión vive en la pantalla para que el filtro se combine
 * con la búsqueda y las categorías.
 */
@Composable
fun ColorFilterRow(
    selected: WallpaperColor?,
    onSelect: (WallpaperColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(WallpaperColor.entries, key = { "color-filter-${it.key}" }) { color ->
            val label = stringResource(labelFor(color))
            val description = stringResource(R.string.home_color_filter_description, label)
            val isSelected = selected == color
            // El círculo mide 38 dp, pero el objetivo táctil llega a 48 dp.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(color) },
                    )
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(swatchFor(color))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = checkTintFor(color),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun labelFor(color: WallpaperColor): Int = when (color) {
    WallpaperColor.RED -> R.string.home_color_filter_red
    WallpaperColor.ORANGE -> R.string.home_color_filter_orange
    WallpaperColor.YELLOW -> R.string.home_color_filter_yellow
    WallpaperColor.GREEN -> R.string.home_color_filter_green
    WallpaperColor.BLUE -> R.string.home_color_filter_blue
    WallpaperColor.PURPLE -> R.string.home_color_filter_purple
    WallpaperColor.PINK -> R.string.home_color_filter_pink
    WallpaperColor.BLACK -> R.string.home_color_filter_black
    WallpaperColor.GRAY -> R.string.home_color_filter_gray
}

private fun swatchFor(color: WallpaperColor): Color = when (color) {
    WallpaperColor.RED -> Color(0xFFE53935)
    WallpaperColor.ORANGE -> Color(0xFFFB8C00)
    WallpaperColor.YELLOW -> Color(0xFFFDD835)
    WallpaperColor.GREEN -> Color(0xFF43A047)
    WallpaperColor.BLUE -> Color(0xFF1E88E5)
    WallpaperColor.PURPLE -> Color(0xFF8E24AA)
    WallpaperColor.PINK -> Color(0xFFEC407A)
    WallpaperColor.BLACK -> Color(0xFF111111)
    WallpaperColor.GRAY -> Color(0xFFBDBDBD)
}

private fun checkTintFor(color: WallpaperColor): Color = when (color) {
    WallpaperColor.YELLOW, WallpaperColor.GRAY -> Color(0xFF1B1B1B)
    else -> Color.White
}
