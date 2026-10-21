package com.dannx.walls.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dannx.walls.LoadState
import com.dannx.walls.UiState
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.ui.components.EmptyState
import com.dannx.walls.ui.components.ErrorState
import com.dannx.walls.ui.components.FeedSkeleton
import com.dannx.walls.ui.components.WallpaperCard

@Composable
fun FavoritesScreen(
    state: UiState,
    onOpenWallpaper: (Wallpaper) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onExplore: () -> Unit,
    onRefresh: () -> Unit = {},
) {
    val items = remember(state.wallpapers, state.favorites) { state.favoriteWallpapers }
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
                Text(
                    text = "Favoritos",
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    text = "${items.size} fondos guardados",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            ) { _, wallpaper ->
                WallpaperCard(
                    wallpaper = wallpaper,
                    isFavorite = true,
                    onClick = { onOpenWallpaper(wallpaper) },
                    onToggleFavorite = { onToggleFavorite(wallpaper.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f),
                )
            }
        }
    }
}
