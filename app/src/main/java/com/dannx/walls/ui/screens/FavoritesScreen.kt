package com.dannx.walls.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.LoadState
import com.dannx.walls.UiState
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.ui.components.EmptyState
import com.dannx.walls.ui.components.ErrorState
import com.dannx.walls.ui.components.FeedSkeleton
import com.dannx.walls.ui.components.SelectionToolbar
import com.dannx.walls.ui.components.StaggeredAppear
import com.dannx.walls.ui.components.WallpaperCard

@Composable
fun FavoritesScreen(
    state: UiState,
    onOpenWallpaper: (Wallpaper) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onExplore: () -> Unit,
    onRefresh: () -> Unit = {},
    onDownloadMany: (List<Wallpaper>) -> Unit = {},
    onShareMany: (List<Wallpaper>) -> Unit = {},
    onRemoveFavorites: (Set<String>) -> Unit = {},
) {
    val items = remember(state.wallpapers, state.favorites) { state.favoriteWallpapers }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectedSet = selectedIds.toSet()
    val selectedItems = remember(items, selectedIds) {
        items.filter { it.id in selectedSet }
    }
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.favorites_title),
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.favorites_saved_count,
                                items.size,
                                items.size,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!selectionMode && items.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectionMode = true
                                selectedIds = emptyList()
                            },
                        ) {
                            Text(stringResource(R.string.selection_enter))
                        }
                    }
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
                        onRemove = if (selectedIds.isNotEmpty()) {
                            {
                                onRemoveFavorites(selectedIds.toSet())
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
                Spacer(Modifier.height(12.dp))
            }
        }

        if (state.feed is LoadState.Loading && items.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                FeedSkeleton()
            }
        } else if (state.feed is LoadState.Error && items.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                ErrorState(
                    message = (state.feed as LoadState.Error).message,
                    onRetry = onRefresh,
                )
            }
        } else if (items.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                EmptyState(
                    title = stringResource(R.string.state_empty_favorites_title),
                    message = stringResource(R.string.state_empty_favorites_body),
                    icon = Icons.Rounded.FavoriteBorder,
                    actionLabel = stringResource(R.string.state_explore),
                    onAction = onExplore,
                )
            }
        } else {
            itemsIndexed(
                items = items,
                key = { _, wallpaper -> wallpaper.id },
                contentType = { _, _ -> "wall" },
            ) { index, wallpaper ->
                val isSelected = wallpaper.id in selectedSet
                StaggeredAppear(index = index) {
                    WallpaperCard(
                        wallpaper = wallpaper,
                        isFavorite = true,
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
                }
            }
        }
    }
}
