package com.dannx.walls.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
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
import androidx.compose.ui.unit.dp
import com.dannx.walls.UiState
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.ui.components.WallpaperCard

@Composable
fun FavoritesScreen(
    state: UiState,
    onOpenWallpaper: (Wallpaper) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onExplore: () -> Unit,
) {
    val items = remember(state.wallpapers, state.favorites) { state.favoriteWallpapers }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
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

        if (items.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Tu colección empieza aquí",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Toca el corazón de cualquier fondo para guardarlo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onExplore) {
                        Text("Explorar fondos")
                    }
                }
            }
        } else {
            itemsIndexed(items = items, key = { _, wallpaper -> wallpaper.id }) { index, wallpaper ->
                WallpaperCard(
                    wallpaper = wallpaper,
                    isFavorite = true,
                    onClick = { onOpenWallpaper(wallpaper) },
                    onToggleFavorite = { onToggleFavorite(wallpaper.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (index % 3 == 0) 278.dp else 218.dp),
                )
            }
        }
    }
}
