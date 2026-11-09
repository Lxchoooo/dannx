package com.dannx.walls.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.dannx.walls.R
import com.dannx.walls.DownloadPrompt
import com.dannx.walls.ActionState
import com.dannx.walls.UiAction
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.ui.components.ApplyWallpaperSheet
import com.dannx.walls.ui.components.rememberWallpaperAccent
import com.dannx.walls.ui.components.wallpaperThumbRequest
import com.dannx.walls.ui.theme.LocalLiteMode
import com.dannx.walls.util.DoubleTapZoom
import com.dannx.walls.util.MinZoom
import com.dannx.walls.util.PanOffset
import com.dannx.walls.util.WallpaperTarget
import com.dannx.walls.util.ZoomState
import com.dannx.walls.util.clampPan
import com.dannx.walls.util.clampScale
import kotlinx.coroutines.launch

private val ZoomStateSaver = listSaver<ZoomState, Float>(
    save = { listOf(it.scale, it.offset.x, it.offset.y) },
    restore = { ZoomState(it[0], PanOffset(it[1], it[2])) },
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailScreen(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    collections: List<WallpaperCollection>,
    canManageCollections: Boolean,
    collectionBusy: Boolean,
    actionState: ActionState = ActionState(),
    downloadPrompt: DownloadPrompt? = null,
    alreadyDownloaded: Boolean = false,
    offline: Boolean = false,
    openEditorOnApply: Boolean = true,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onOpenEditor: () -> Unit,
    onSetWallpaper: (WallpaperTarget) -> Unit,
    onShare: () -> Unit,
    onCancelAction: () -> Unit,
    onOpenFile: (String) -> Unit,
    onDownloadAgain: (String) -> Unit,
    onDismissDownloadPrompt: () -> Unit,
    onAddToCollection: (String) -> Unit,
    onCreateCollection: (String, String, Boolean) -> Unit,
) {
    var zoom by rememberSaveable(stateSaver = ZoomStateSaver) { mutableStateOf(ZoomState()) }
    var showCollections by rememberSaveable { mutableStateOf(false) }
    var showCreateCollection by rememberSaveable { mutableStateOf(false) }
    var showApplySheet by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // El botón principal toma el color de la imagen para sentirse parte de ella.
    val accent = rememberWallpaperAccent(wallpaper.imageUrl, MaterialTheme.colorScheme.primary)
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val scaleAnimation = remember { Animatable(1f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = clampScale(zoom.scale * zoomChange)
        val newOffset = if (newScale <= MinZoom) {
            PanOffset(0f, 0f)
        } else {
            PanOffset(zoom.offset.x + panChange.x, zoom.offset.y + panChange.y)
        }
        zoom = ZoomState(
            scale = newScale,
            offset = clampPan(
                offset = newOffset,
                scale = newScale,
                containerWidth = containerSize.width.toFloat(),
                containerHeight = containerSize.height.toFloat(),
            ),
        )
    }
    val activity = LocalActivity.current
    val liteMode = LocalLiteMode.current
    var entered by remember { mutableStateOf(false) }
    var fullImageLoaded by remember(wallpaper.id) { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val fullImageAlpha by animateFloatAsState(
        targetValue = if (fullImageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "detailFullImageAlpha",
    )
    val enterScale by animateFloatAsState(
        targetValue = if (entered || liteMode) 1f else 1.04f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "detailEnterScale",
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered || liteMode) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "detailEnterAlpha",
    )

    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Neutro mientras no hay imagen: nunca un rectángulo negro.
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { containerSize = it },
    ) {
        val context = LocalContext.current
        val windowSize = LocalWindowInfo.current.containerSize
        // Se pide exactamente la misma miniatura que usa la grilla, con su
        // misma clave de memoria. Si no está cacheada, no se descarga: solo se
        // ve el fondo neutro hasta que llega la imagen completa.
        val thumbnailRequest = remember(
            wallpaper.id,
            wallpaper.imageUrl,
            wallpaper.thumbnailUrl,
        ) {
            wallpaperThumbRequest(context, wallpaper)
                .newBuilder()
                .networkCachePolicy(CachePolicy.DISABLED)
                .build()
        }
        val imageRequest = remember(
            wallpaper.id,
            wallpaper.imageUrl,
            windowSize,
        ) {
            ImageRequest.Builder(context)
                .data(wallpaper.imageUrl)
                .size(windowSize.width, windowSize.height)
                .build()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale * enterScale
                    scaleY = zoom.scale * enterScale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                    alpha = enterAlpha
                }
                .transformable(transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val target = if (zoom.scale > MinZoom) MinZoom else DoubleTapZoom
                            scope.launch {
                                val start = zoom
                                scaleAnimation.snapTo(start.scale)
                                scaleAnimation.animateTo(
                                    targetValue = target,
                                    animationSpec = tween(durationMillis = 220),
                                ) {
                                    val current = value
                                    val progress = if (target == start.scale) {
                                        1f
                                    } else {
                                        ((current - start.scale) / (target - start.scale)).coerceIn(0f, 1f)
                                    }
                                    val offset = PanOffset(
                                        x = start.offset.x * (1f - progress),
                                        y = start.offset.y * (1f - progress),
                                    )
                                    zoom = ZoomState(
                                        scale = current,
                                        offset = clampPan(
                                            offset = offset,
                                            scale = current,
                                            containerWidth = containerSize.width.toFloat(),
                                            containerHeight = containerSize.height.toFloat(),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                },
        ) {
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            AsyncImage(
                model = imageRequest,
                contentDescription = wallpaper.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fullImageAlpha },
                onSuccess = { fullImageLoaded = true },
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.28f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.9f),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canManageCollections) {
                    FilledIconButton(
                        onClick = { showCollections = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.35f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.BookmarkAdd,
                            contentDescription = stringResource(R.string.detail_save_collection),
                        )
                    }
                }
                FilledIconButton(
                    onClick = onToggleFavorite,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.35f),
                        contentColor = if (isFavorite) accent else Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                        contentDescription = if (isFavorite) {
                            stringResource(R.string.action_remove_favorite)
                        } else {
                            stringResource(R.string.action_add_favorite)
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = wallpaper.category.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = wallpaper.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.detail_by_author, wallpaper.author),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
            )
            if (wallpaper.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = wallpaper.tags.joinToString("   ") { "#$it" },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            Spacer(Modifier.height(20.dp))
            if (offline) {
                Text(
                    text = stringResource(R.string.state_offline_detail),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDownload()
                    },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = onAccent,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    enabled = actionState.active == null && !offline,
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (alreadyDownloaded) {
                            stringResource(R.string.download_again)
                        } else {
                            stringResource(R.string.download_action)
                        },
                    )
                }
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (openEditorOnApply) {
                            onOpenEditor()
                        } else {
                            showApplySheet = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    enabled = actionState.active == null && !offline,
                ) {
                    Icon(Icons.Rounded.Wallpaper, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_apply))
                }
                FilledIconButton(
                    onClick = onShare,
                    modifier = Modifier.size(52.dp),
                    enabled = actionState.active == null && !offline,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.action_share),
                    )
                }
            }
            if (actionState.active == UiAction.APPLY) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.apply_in_progress),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                        Text(
                            text = stringResource(R.string.apply_in_progress_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    TextButton(onClick = onCancelAction) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            color = Color.White,
                        )
                    }
                }
            }
            if (actionState.active == UiAction.DOWNLOAD) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.download_in_progress,
                            actionState.progress ?: 0,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancelAction) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            color = Color.White,
                        )
                    }
                }
                LinearWavyProgressIndicator(
                    progress = { (actionState.progress ?: 0).coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.30f),
                )
            }
        }
    }

    if (showApplySheet) {
        ApplyWallpaperSheet(
            onDismiss = { showApplySheet = false },
            onPick = { target ->
                showApplySheet = false
                onSetWallpaper(target)
            },
        )
    }

    if (downloadPrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissDownloadPrompt,
            title = { Text(stringResource(R.string.download_duplicate_title)) },
            text = { Text(stringResource(R.string.download_duplicate_body)) },
            confirmButton = {
                TextButton(onClick = { onDownloadAgain(downloadPrompt.wallpaperId) }) {
                    Text(stringResource(R.string.download_again))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onOpenFile(downloadPrompt.uri) }) {
                        Text(stringResource(R.string.action_open))
                    }
                    TextButton(onClick = onDismissDownloadPrompt) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
        )
    }

    if (showCollections) {
        CollectionPickerDialog(
            collections = collections,
            busy = collectionBusy,
            onDismiss = { showCollections = false },
            onPick = { collectionId ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                showCollections = false
                onAddToCollection(collectionId)
            },
            onCreateNew = {
                showCollections = false
                showCreateCollection = true
            },
        )
    }

    if (showCreateCollection) {
        NewCollectionDialog(
            busy = collectionBusy,
            onDismiss = { showCreateCollection = false },
            onCreate = { name, description, isPublic ->
                onCreateCollection(name, description, isPublic)
                showCreateCollection = false
            },
        )
    }
}

@Composable
private fun CollectionPickerDialog(
    collections: List<WallpaperCollection>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreateNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_save_collection)) },
        text = {
            Column {
                if (collections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_no_collections),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                    ) {
                        items(items = collections, key = { it.id }) { collection ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) { onPick(collection.id) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CollectionsBookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = collection.name,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.wallpapers_count,
                                            collection.wallpaperIds.size,
                                            collection.wallpaperIds.size,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCreateNew, enabled = !busy) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.collection_create_new))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun NewCollectionDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(false) }

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
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isPublic) {
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
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, isPublic) },
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
