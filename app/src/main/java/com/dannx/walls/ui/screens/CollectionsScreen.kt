package com.dannx.walls.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.dannx.walls.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.dannx.walls.LoadState
import com.dannx.walls.UiState
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.ui.components.EmptyState
import com.dannx.walls.ui.components.ErrorState
import com.dannx.walls.ui.components.FeedSkeleton
import com.dannx.walls.ui.components.StatefulImage
import com.dannx.walls.ui.components.screenImageSize
import com.dannx.walls.ui.theme.LocalLiteMode

@Composable
fun CollectionsScreen(
    state: UiState,
    onOpenCollection: (WallpaperCollection) -> Unit,
    onCreateCollection: (String, String, Boolean, Boolean) -> Unit,
    onRefresh: () -> Unit = {},
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.collections_title),
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    text = stringResource(R.string.collections_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showCreate = true },
                    shape = CircleShape,
                    enabled = !state.collectionBusy,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.collections_create))
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        when {
            state.collections is LoadState.Loading && state.collectionItems.isEmpty() -> {
                item {
                    FeedSkeleton()
                }
            }

            state.collections is LoadState.Error && state.collectionItems.isEmpty() -> {
                item {
                    ErrorState(
                        message = (state.collections as LoadState.Error).message,
                        onRetry = onRefresh,
                    )
                }
            }

            state.collectionItems.isEmpty() -> {
                item {
                    EmptyState(
                        title = stringResource(R.string.state_empty_collections_title),
                        message = stringResource(R.string.state_empty_collections_body),
                        icon = Icons.Rounded.CollectionsBookmark,
                    )
                }
            }
        }

        if (state.collectionItems.isNotEmpty()) {
            items(items = state.collectionItems, key = { it.id }) { collection ->
                CollectionCard(
                    state = state,
                    collection = collection,
                    onClick = { onOpenCollection(collection) },
                )
            }
        }
    }

    if (showCreate) {
        CreateCollectionDialog(
            busy = state.collectionBusy,
            canChooseDestination = state.session != null,
            onDismiss = { showCreate = false },
            onCreate = { name, description, isPublic, onDevice ->
                onCreateCollection(name, description, isPublic, onDevice)
                showCreate = false
            },
        )
    }
}

@Composable
private fun CollectionCard(
    state: UiState,
    collection: WallpaperCollection,
    onClick: () -> Unit,
) {
    val wallpapers = state.wallpapers
    val visibleWallpapers = remember(collection.id, collection.wallpaperIds, wallpapers) {
        val byId = wallpapers.associateBy { it.id }
        collection.wallpaperIds.mapNotNull { byId[it] }
    }
    val cover = visibleWallpapers.firstOrNull()
    val mine = state.canEdit(collection) && !state.isDemo
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (cover != null) {
                val context = LocalContext.current
                val lite = LocalLiteMode.current
                val coverSize = screenImageSize(190.dp)
                val coverRequest = remember(cover.id, cover.thumbnailUrl, coverSize, lite) {
                    // En lite la portada va un poco más chica: se estira igual
                    // al ancho de la tarjeta sin que se note en un uso liviano.
                    val scale = if (lite) 0.7f else 1f
                    ImageRequest.Builder(context)
                        .data(cover.thumbnailUrl)
                        .size((coverSize.width * scale).toInt(), (coverSize.height * scale).toInt())
                        .build()
                }
                StatefulImage(
                    model = coverRequest,
                    contentDescription = collection.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CollectionsBookmark,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!collection.isPublic) {
                    Badge(icon = Icons.Rounded.Lock, label = stringResource(R.string.collection_private_badge))
                }
                if (mine) {
                    Badge(icon = null, label = stringResource(R.string.collection_yours_badge))
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val total = collection.wallpaperIds.size
                val visible = visibleWallpapers.size
                val countText = if (visible == total) {
                    pluralStringResource(
                        R.plurals.collection_meta,
                        total,
                        total,
                        collection.ownerName,
                    )
                } else {
                    stringResource(
                        R.string.collection_meta_visible,
                        visible,
                        total,
                        collection.ownerName,
                    )
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.22f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CreateCollectionDialog(
    busy: Boolean,
    canChooseDestination: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(false) }
    var onDevice by rememberSaveable { mutableStateOf(!canChooseDestination) }
    // Una colección local no puede ser pública: no existe en el servidor.
    val accountDestination = canChooseDestination && !onDevice

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collection_new)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_name)) },
                    placeholder = { Text(stringResource(R.string.collection_name_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_description)) },
                    placeholder = { Text(stringResource(R.string.field_optional)) },
                    minLines = 2,
                )
                if (canChooseDestination) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.collection_destination),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDevice = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = onDevice, onClick = { onDevice = true })
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.collection_destination_device),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.collection_destination_device_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDevice = false },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = !onDevice, onClick = { onDevice = false })
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.collection_destination_account),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.collection_destination_account_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.collection_destination_device_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (accountDestination) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isPublic) {
                                    stringResource(R.string.collection_public)
                                } else {
                                    stringResource(R.string.collection_private_badge)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (isPublic) {
                                    stringResource(R.string.collection_public_hint)
                                } else {
                                    stringResource(R.string.collection_private_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, accountDestination && isPublic, onDevice) },
                enabled = name.isNotBlank() && !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.action_create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
