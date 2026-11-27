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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.LoadState
import com.dannx.walls.UiState
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.filterWallpapers
import com.dannx.walls.ui.LocalTabScroll
import com.dannx.walls.ui.LocalWindowSizeClass
import com.dannx.walls.ui.components.EmptyState
import com.dannx.walls.ui.components.ErrorState
import com.dannx.walls.ui.components.ExpressiveFilterChip
import com.dannx.walls.ui.components.FeedSkeleton
import com.dannx.walls.ui.components.OfflineBanner
import com.dannx.walls.ui.components.PullRefreshBox
import com.dannx.walls.ui.components.SelectionToolbar
import com.dannx.walls.ui.components.StaggeredAppear
import com.dannx.walls.ui.components.WallpaperCard
import com.dannx.walls.ui.components.animateItemMotion
import java.util.Locale

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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    // La búsqueda recorre el mismo filtro que la portada (título, categoría,
    // autor y etiquetas) y el orden se aplica encima, sin tocar la lista base.
    val visibleItems = remember(items, searchQuery, sortByName) {
        val matched = if (searchQuery.isBlank()) {
            items
        } else {
            filterWallpapers(items, searchQuery, null)
        }
        if (sortByName) {
            matched.sortedBy { it.title.lowercase(Locale.ROOT) }
        } else {
            matched
        }
    }
    val selectedSet = selectedIds.toSet()
    val selectedItems = remember(visibleItems, selectedIds) {
        visibleItems.filter { it.id in selectedSet }
    }
    val allSelected = visibleItems.isNotEmpty() && visibleItems.all { it.id in selectedSet }
    fun clearSelection() {
        selectionMode = false
        selectedIds = emptyList()
    }
    BackHandler(enabled = selectionMode) { clearSelection() }
    val haptics = LocalHapticFeedback.current
    fun toggleSelection(id: String) {
        val updated = if (id in selectedSet) selectedIds - id else selectedIds + id
        selectedIds = updated
        if (updated.isEmpty()) selectionMode = false
    }
    // La selección vive sobre lo que se ve: cambiar la búsqueda la descarta.
    fun onSearchChange(value: String) {
        searchQuery = value
        if (selectionMode) clearSelection()
    }
    // Mismo criterio que el feed de portada: más columnas en pantallas anchas.
    val gridColumns = when (LocalWindowSizeClass.current.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }
    val gridState = rememberLazyGridState()
    val tabScroll = LocalTabScroll.current
    // Tocar la pestaña activa en la barra vuelve la cuadrícula al principio.
    LaunchedEffect(tabScroll.tick) {
        if (tabScroll.tick > 0) gridState.animateScrollToItem(0)
    }
    PullRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(gridColumns) }) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val refreshLabel = stringResource(R.string.action_refresh)
                            Text(
                                text = stringResource(R.string.favorites_title),
                                style = MaterialTheme.typography.displayMediumEmphasized,
                                modifier = Modifier.semantics {
                                    heading()
                                    customActions = listOf(
                                        CustomAccessibilityAction(refreshLabel) {
                                            onRefresh()
                                            true
                                        },
                                    )
                                },
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
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            onToggleSelectAll = if (visibleItems.isEmpty()) {
                                null
                            } else {
                                {
                                    selectedIds = if (allSelected) {
                                        emptyList()
                                    } else {
                                        visibleItems.map { it.id }
                                    }
                                }
                            },
                        )
                    }
                    if (items.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { onSearchChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.favorites_search)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(
                                                R.string.home_clear_search,
                                            ),
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(end = 12.dp),
                        ) {
                            item(key = "fav-sort-label") {
                                Text(
                                    text = stringResource(R.string.favorites_sort),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item(key = "fav-sort-recent") {
                                ExpressiveFilterChip(
                                    selected = !sortByName,
                                    onClick = {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove,
                                        )
                                        sortByName = false
                                    },
                                    label = {
                                        Text(stringResource(R.string.favorites_sort_recent))
                                    },
                                )
                            }
                            item(key = "fav-sort-name") {
                                ExpressiveFilterChip(
                                    selected = sortByName,
                                    onClick = {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove,
                                        )
                                        sortByName = true
                                    },
                                    label = {
                                        Text(stringResource(R.string.favorites_sort_name))
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (state.offline) {
                item(span = { GridItemSpan(gridColumns) }) {
                    OfflineBanner()
                }
            }

            if (state.feed is LoadState.Loading && items.isEmpty()) {
                item(span = { GridItemSpan(gridColumns) }) {
                    FeedSkeleton()
                }
            } else if (state.feed is LoadState.Error && items.isEmpty()) {
                item(span = { GridItemSpan(gridColumns) }) {
                    ErrorState(
                        message = (state.feed as LoadState.Error).message,
                        onRetry = onRefresh,
                    )
                }
            } else if (items.isEmpty()) {
                item(span = { GridItemSpan(gridColumns) }) {
                    EmptyState(
                        title = stringResource(R.string.state_empty_favorites_title),
                        message = stringResource(R.string.state_empty_favorites_body),
                        icon = Icons.Rounded.FavoriteBorder,
                        actionLabel = stringResource(R.string.state_explore),
                        onAction = onExplore,
                    )
                }
            } else if (visibleItems.isEmpty()) {
                item(span = { GridItemSpan(gridColumns) }) {
                    EmptyState(
                        title = stringResource(R.string.state_empty_filter_title),
                        message = stringResource(R.string.state_empty_filter_body),
                        icon = Icons.Rounded.SearchOff,
                        actionLabel = stringResource(R.string.home_clear_search),
                        onAction = { onSearchChange("") },
                    )
                }
            } else {
                itemsIndexed(
                    items = visibleItems,
                    key = { _, wallpaper -> wallpaper.id },
                    contentType = { _, _ -> "wall" },
                ) { index, wallpaper ->
                    val isSelected = wallpaper.id in selectedSet
                    StaggeredAppear(
                        index = index,
                        modifier = animateItemMotion(),
                    ) {
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
}
