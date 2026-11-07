package com.dannx.walls.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.dannx.walls.R
import com.dannx.walls.LoadState
import com.dannx.walls.UiState
import com.dannx.walls.data.DefaultCategories
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.ui.components.EmptyState
import com.dannx.walls.ui.components.ErrorState
import com.dannx.walls.ui.components.FeaturedCard
import com.dannx.walls.ui.components.HeroHeight
import com.dannx.walls.ui.components.FeedSkeleton
import com.dannx.walls.ui.components.OfflineBanner
import com.dannx.walls.ui.components.WallpaperCard
import com.dannx.walls.ui.components.wallpaperThumbRequest
import com.dannx.walls.ui.LocalWindowSizeClass
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Segundos que hay que mantener presionado el título para la puerta secreta. */
private const val SECRET_HOLD_MS = 9_000L

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onTagChange: (String?) -> Unit,
    onRefresh: () -> Unit,
    onOpenWallpaper: (Wallpaper) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onClearFilters: () -> Unit = {
        onQueryChange("")
        onCategoryChange(null)
        onTagChange(null)
    },
    onOpenStudio: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val favorites = state.favorites
    val featured = remember(state.wallpapers) { state.featured }
    val filtered = remember(state.wallpapers, state.query, state.category, state.tag) {
        state.filtered
    }
    val availableTags = remember(state.wallpapers) { state.availableTags }
    val context = LocalContext.current
    // Solo se precargan las miniaturas una vez por carga del feed: al volver a
    // la pestaña no se relanza, para no competir con las imagenes visibles.
    var precached by rememberSaveable(state.wallpapers) { mutableStateOf(false) }
    LaunchedEffect(state.wallpapers) {
        if (precached) return@LaunchedEffect
        delay(300)
        state.wallpapers.take(12).forEach { wallpaper ->
            context.imageLoader.enqueue(
                wallpaperThumbRequest(context, wallpaper),
            )
        }
        precached = true
    }

    val showHero = state.query.isBlank() && state.category == null && state.tag == null &&
        featured.isNotEmpty()
    val categories = remember(state.wallpapers) {
        listOf("Todos", "Destacados") +
            (DefaultCategories + state.wallpapers.map { it.category }).distinct().sorted()
    }
    // Se recrea cuando cambia la cantidad de destacados para que el carrusel
    // siempre conozca el total actual.
    val carouselState = key(featured.size) { rememberCarouselState { featured.size } }
    val windowWidth = LocalWindowSizeClass.current.widthSizeClass
    val columns = when (windowWidth) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }
    // En pantallas bajas el hero no debe comerse todo el alto: se limita a
    // poco más de la mitad de la ventana.
    val heroHeight = with(LocalConfiguration.current) {
        min(HeroHeight.value, screenHeightDp * 0.55f).dp
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        // Puerta secreta: mantener presionado el título unos
                        // segundos sin mover el dedo abre la puerta del Estudio.
                        val haptics = LocalHapticFeedback.current
                        Text(
                            text = "DannX",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.pointerInput(onOpenStudio) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val slop = viewConfiguration.touchSlop
                                    val deadline = down.uptimeMillis + SECRET_HOLD_MS
                                    var lastUptime = down.uptimeMillis
                                    var triggered = false
                                    var canceled = false
                                    while (!canceled && !triggered) {
                                        val remaining = deadline - lastUptime
                                        val event =
                                            withTimeoutOrNull(remaining) { awaitPointerEvent() }
                                        if (event == null) {
                                            // Llegó la hora y el dedo sigue quieto.
                                            triggered = true
                                            break
                                        }
                                        val change = event.changes.firstOrNull() ?: continue
                                        lastUptime = change.uptimeMillis
                                        val position = change.position
                                        val dx = position.x - down.position.x
                                        val dy = position.y - down.position.y
                                        if (dx * dx + dy * dy > slop * slop) {
                                            canceled = true
                                            break
                                        }
                                        if (!change.pressed) {
                                            canceled = true
                                            break
                                        }
                                    }
                                    if (triggered) {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.LongPress,
                                        )
                                        onOpenStudio()
                                    }
                                }
                            },
                        )
                        Text(
                            text = "Wallpapers que se sienten tuyos.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Ajustes")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar feed")
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar fondos o tags") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        if (state.refreshing && state.feed is LoadState.Content) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(
                contentPadding = PaddingValues(end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories, key = { it }) { label ->
                    val selected = (state.category ?: "Todos") == label
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onCategoryChange(if (label == "Todos") null else label)
                        },
                        label = { Text(label) },
                        shape = CircleShape,
                    )
                }
            }
        }

        if (availableTags.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        text = "Etiquetas",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        contentPadding = PaddingValues(end = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "all-tags") {
                            FilterChip(
                                selected = state.tag == null,
                                onClick = { onTagChange(null) },
                                label = { Text("Todas") },
                                shape = CircleShape,
                            )
                        }
                        items(availableTags, key = { "tag-$it" }) { tag ->
                            FilterChip(
                                selected = state.tag == tag,
                                onClick = {
                                    onTagChange(if (state.tag == tag) null else tag)
                                },
                                label = { Text("#$tag") },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }
        }

        if (state.isDemo) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Guardá los fondos que más te gusten y creá tu primera colección.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        if (showHero) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    HorizontalMultiBrowseCarousel(
                        state = carouselState,
                        preferredItemWidth = heroHeight * 0.72f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight),
                        itemSpacing = 12.dp,
                        minSmallItemWidth = 42.dp,
                        maxSmallItemWidth = 84.dp,
                    ) { page ->
                        val wallpaper = featured[page]
                        FeaturedCard(
                            wallpaper = wallpaper,
                            isFavorite = wallpaper.id in favorites,
                            onClick = { onOpenWallpaper(wallpaper) },
                            onToggleFavorite = { onToggleFavorite(wallpaper.id) },
                            heroHeight = heroHeight,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = if (state.query.isBlank()) "Explorar" else "Resultados",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${filtered.size} fondos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.offline) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OfflineBanner()
            }
        }

        when (val feed = state.feed) {
            is LoadState.Loading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FeedSkeleton()
                }
            }

            is LoadState.Error -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorState(message = feed.message, onRetry = onRefresh)
                }
            }

            is LoadState.Empty -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = stringResource(R.string.state_empty_feed_title),
                        message = stringResource(R.string.state_empty_feed_body),
                    )
                }
            }

            is LoadState.Content -> {
                if (filtered.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            title = stringResource(R.string.state_empty_filter_title),
                            message = stringResource(R.string.state_empty_filter_body),
                            actionLabel = stringResource(R.string.state_clear_filters),
                            onAction = onClearFilters,
                        )
                    }
                }
            }
        }

        if (state.feed is LoadState.Content && filtered.isNotEmpty()) {
            itemsIndexed(
                items = filtered,
                key = { _, wallpaper -> wallpaper.id },
                contentType = { _, _ -> "wall" },
            ) { _, wallpaper ->
                WallpaperCard(
                    wallpaper = wallpaper,
                    isFavorite = wallpaper.id in state.favorites,
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
