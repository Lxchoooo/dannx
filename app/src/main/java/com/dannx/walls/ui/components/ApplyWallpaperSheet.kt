package com.dannx.walls.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.util.WallpaperTarget

/** Selector de destino reutilizado por la ficha y por el editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplyWallpaperSheet(
    onDismiss: () -> Unit,
    onPick: (WallpaperTarget) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.apply_sheet_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(12.dp))
            ApplyOption(
                icon = Icons.Rounded.Smartphone,
                title = stringResource(R.string.apply_system),
                hint = stringResource(R.string.apply_system_hint),
                onClick = { onPick(WallpaperTarget.SYSTEM) },
            )
            Spacer(Modifier.height(8.dp))
            ApplyOption(
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.apply_lock),
                hint = stringResource(R.string.apply_lock_hint),
                onClick = { onPick(WallpaperTarget.LOCK) },
            )
            Spacer(Modifier.height(8.dp))
            ApplyOption(
                icon = Icons.Rounded.Wallpaper,
                title = stringResource(R.string.apply_both),
                hint = stringResource(R.string.apply_both_hint),
                onClick = { onPick(WallpaperTarget.BOTH) },
            )
        }
    }
}

@Composable
private fun ApplyOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
