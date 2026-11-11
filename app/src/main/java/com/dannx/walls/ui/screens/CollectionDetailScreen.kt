package com.dannx.walls.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.ui.components.EmptyState
import com.dannx.walls.ui.components.SelectionToolbar
import com.dannx.walls.ui.components.WallpaperCard

@Composable
fun CollectionDetailScreen(
    state: UiState,
    collection: WallpaperCollection,
    items: List<Wallpaper>,
    onBack: () -> Unit,
    onOpenWallpaper: (Wallpaper) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDelete: () -> Unit,
    onExplore: () -> Unit,
    onDownloadMany: (List<Wallpaper>) -> Unit = {},
    onShareMany: (List<Wallpaper>) -> Unit = {},
    onRemoveMany: (Set<String>) -> Unit = {},
) {
    val total = collection.wallpaperIds.size
    val canEdit = state.canEdit(collection)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectedSet = selectedIds.toSet()
    val selectedItems = items.filter { it.id in selectedSet }
    val allSelected = items.isNotEmpty() && items.all { it.id in selectedSet }
    fun clearSelection() {
        selectionMode = false
        selectedIds = emptyList()
    }
    BackHandler(enabled = selectionMode) { clearSelection() }
    fun toggleSelection(id: String) {
        val updated = if (id in selectedSet) selectedIds - id else selectedIds + id
        selectedIds = updated
        if (updated.isEmpty()) selectionMode = false
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = collection.name,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = if (items.size == total) {
                                pluralStringResource(
                                    R.plurals.collection_meta,
                                    items.size,
                                    items.size,
                                    collection.ownerName,
                                )
                            } else {
                                stringResource(
                                    R.string.collection_meta_visible,
                                    items.size,
                                    total,
                                    collection.ownerName,
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (items.isNotEmpty() && !selectionMode) {
                        TextButton(
                            onClick = {
                                selectionMode = true
                                selectedIds = emptyList()
                            },
                        ) {
                            Text(stringResource(R.string.selection_enter))
                        }
                    }
                    if (canEdit) {
                        FilledTonalIconButton(
                            onClick = { confirmDelete = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.collection_delete))
                        }
                    }
                }
                if (collection.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = collection.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!collection.isPublic) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.collection_private_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (selectionMode) {
                    Spacer(Modifier.height(10.dp))
                    SelectionToolbar(
                        count = selectedIds.size,
                        onCancel = { clearSelection() },
                        onDownload = if (selectedItems.isNotEmpty()) {
                            {
                                onDownloadMany(selectedItems)
                                clearSelection()
                            }
                        } else {
                            null
                        },
                        onShare = if (selectedItems.isNotEmpty()) {
                            {
                                onShareMany(selectedItems)
                                clearSelection()
                            }
                        } else {
                            null
                        },
                        onRemove = if (canEdit && selectedIds.isNotEmpty()) {
                            {
                                onRemoveMany(selectedIds.toSet())
                                clearSelection()
                            }
                        } else {
                            null
                        },
                        allSelected = allSelected,
                        onToggleSelectAll = if (items.isEmpty()) {
                            null
                        } else {
                            {
                                selectedIds = if (allSelected) {
                                    emptyList()
                                } else {
                                    items.map { it.id }
                                }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        if (items.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                EmptyState(
                    title = stringResource(R.string.collection_empty_title),
                    message = if (collection.isPublic) {
                        null
                    } else {
                        stringResource(R.string.collection_empty_body)
                    },
                    icon = Icons.Rounded.CollectionsBookmark,
                    actionLabel = stringResource(R.string.state_explore),
                    onAction = onExplore,
                )
            }
        } else {
            itemsIndexed(
                items = items,
                key = { _, wallpaper -> wallpaper.id },
                contentType = { _, _ -> "wall" },
            ) { _, wallpaper ->
                val isSelected = wallpaper.id in selectedSet
                Box {
                    WallpaperCard(
                        wallpaper = wallpaper,
                        isFavorite = wallpaper.id in state.favorites,
                        onClick = {
                            if (selectionMode) {
                                toggleSelection(wallpaper.id)
                            } else {
                                onOpenWallpaper(wallpaper)
                            }
                        },
                        onToggleFavorite = { onToggleFavorite(wallpaper.id) },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds = listOf(wallpaper.id)
                            }
                        },
                        selected = isSelected,
                        showFavorite = !selectionMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f),
                    )
                    if (canEdit && !selectionMode) {
                        FilledTonalIconButton(
                            onClick = { onRemove(wallpaper.id) },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.4f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RemoveCircleOutline,
                                contentDescription = stringResource(R.string.collection_remove_item),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.collection_delete)) },
            text = {
                Text(
                    stringResource(R.string.collection_delete_body, collection.name),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
