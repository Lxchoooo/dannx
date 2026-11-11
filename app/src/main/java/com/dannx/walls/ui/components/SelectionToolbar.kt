package com.dannx.walls.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dannx.walls.R

/** Barra que aparece al mantener presionado un fondo en una grilla. */
@Composable
fun SelectionToolbar(
    count: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    allSelected: Boolean = false,
    onToggleSelectAll: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (count == 0) {
                    stringResource(R.string.selection_hint)
                } else {
                    pluralStringResource(R.plurals.selection_count, count, count)
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (onToggleSelectAll != null) {
                IconButton(onClick = onToggleSelectAll) {
                    Icon(
                        if (allSelected) Icons.Rounded.ClearAll else Icons.Rounded.SelectAll,
                        contentDescription = stringResource(
                            if (allSelected) {
                                R.string.selection_deselect_all
                            } else {
                                R.string.selection_select_all
                            },
                        ),
                    )
                }
            }
            if (onDownload != null) {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.selection_download),
                    )
                }
            }
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.selection_share),
                    )
                }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.selection_remove),
                    )
                }
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.selection_cancel),
                )
            }
        }
    }
}
