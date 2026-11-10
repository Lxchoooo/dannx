package com.dannx.walls.ui.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.StringRes
import com.dannx.walls.R
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.ui.components.ApplyWallpaperSheet
import com.dannx.walls.util.WallpaperTarget
import com.dannx.walls.util.wallpaperTargetSize
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private enum class EditorTool(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    CROP(R.string.editor_tool_crop, Icons.Rounded.Crop),
    ADJUSTMENTS(R.string.editor_tool_adjust, Icons.Rounded.Tune),
    FILTERS(R.string.editor_tool_filters, Icons.Rounded.FilterVintage),
    BLUR(R.string.editor_tool_blur, Icons.Rounded.BlurOn),
}

private enum class EditorPreviewMode {
    NONE,
    HOME,
    LOCK,
}

private const val ControlsHideDelayMillis = 3_000L

/**
 * Editor de una imagen fija. No usa parallax ni APIs de Android 12: el
 * desenfoque se hace sobre una copia reducida y el color con ColorMatrix.
 */
@Composable
fun WallpaperEditorScreen(
    wallpaper: Wallpaper,
    onBack: () -> Unit,
    onApplied: (WallpaperTarget) -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val editorViewModel: WallpaperEditorViewModel = viewModel(
        key = "wallpaper-editor-${wallpaper.id}",
        factory = WallpaperEditorViewModel.factory(application, wallpaper),
    )
    val state by editorViewModel.state.collectAsStateWithLifecycle()
    var showTargetSheet by rememberSaveable { mutableStateOf(false) }
    val targetSize = remember(application) { wallpaperTargetSize(application) }
    val frameAspect = targetSize.x.toFloat() / targetSize.y.toFloat()
    val activity = LocalActivity.current

    // La foto va de borde a borde: los iconos del sistema tienen que verse
    // claros incluso cuando el fondo es claro.
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

    LaunchedEffect(state.appliedTarget) {
        state.appliedTarget?.let(onApplied)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            state.loading -> LoadingState(state.loadProgress)
            state.loadError != null -> ErrorState(
                message = state.loadError ?: stringResource(R.string.editor_open_error),
                onRetry = editorViewModel::load,
            )
            else -> EditorBody(
                state = state,
                frameAspect = frameAspect,
                onBack = {
                    editorViewModel.cancelApply()
                    onBack()
                },
                onCropChange = editorViewModel::updateCrop,
                onAdjustmentsChange = editorViewModel::updateAdjustments,
                onFilterPicked = editorViewModel::selectFilter,
                onFilterIntensity = editorViewModel::updateFilterIntensity,
                onBlurChange = editorViewModel::updateBlur,
                onShowOriginalChange = editorViewModel::setShowOriginal,
                onUndo = editorViewModel::undo,
                onRedo = editorViewModel::redo,
                onReset = editorViewModel::reset,
                onApply = { showTargetSheet = true },
            )
        }

        if (state.loading || state.loadError != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp),
            ) {
                FloatingIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = {
                        editorViewModel.cancelApply()
                        onBack()
                    },
                )
            }
        }

        if (state.applying) {
            ApplyProgressOverlay(state.applyProgress)
        }
    }

    if (showTargetSheet) {
        ApplyWallpaperSheet(
            onDismiss = { showTargetSheet = false },
            onPick = { target ->
                showTargetSheet = false
                editorViewModel.apply(target)
            },
        )
    }
}

@Composable
private fun LoadingState(progress: Int?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.editor_preparing))
            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.width(220.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text("$progress%", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.state_retry))
            }
        }
    }
}

@Composable
private fun ApplyProgressOverlay(progress: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.editor_generating))
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.width(220.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text("$progress%", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EditorBody(
    state: EditorUiState,
    frameAspect: Float,
    onBack: () -> Unit,
    onCropChange: (EditorCrop) -> Unit,
    onAdjustmentsChange: (EditorAdjustments) -> Unit,
    onFilterPicked: (FilterPreset) -> Unit,
    onFilterIntensity: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
    onShowOriginalChange: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    val preview = state.preview ?: return
    var toolName by rememberSaveable { mutableStateOf(EditorTool.CROP.name) }
    val tool = EditorTool.entries.firstOrNull { it.name == toolName } ?: EditorTool.CROP
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var previewModeName by rememberSaveable { mutableStateOf(EditorPreviewMode.NONE.name) }
    val previewMode = EditorPreviewMode.entries
        .firstOrNull { it.name == previewModeName } ?: EditorPreviewMode.NONE
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }

    val revealControls: () -> Unit = {
        controlsVisible = true
        interactionTick++
    }
    val openTool: (EditorTool) -> Unit = { item ->
        toolName = item.name
        panelOpen = true
        controlsVisible = true
        interactionTick++
    }
    val closePanel: () -> Unit = {
        panelOpen = false
        controlsVisible = true
        interactionTick++
    }
    val changePreviewMode: (EditorPreviewMode) -> Unit = { mode ->
        previewModeName = mode.name
        controlsVisible = true
        interactionTick++
    }

    BackHandler(enabled = panelOpen || previewMode != EditorPreviewMode.NONE) {
        if (panelOpen) {
            closePanel()
        } else {
            changePreviewMode(EditorPreviewMode.NONE)
        }
    }

    LaunchedEffect(
        controlsVisible,
        panelOpen,
        previewMode,
        state.showOriginal,
        state.applying,
        interactionTick,
    ) {
        if (!controlsVisible || panelOpen || state.showOriginal || state.applying) {
            return@LaunchedEffect
        }
        delay(ControlsHideDelayMillis)
        controlsVisible = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val panelMaxHeight = minOf(maxHeight * 0.52f, 440.dp)

        Column(Modifier.fillMaxSize()) {
            PreviewViewport(
                state = state,
                preview = preview,
                frameAspect = frameAspect,
                previewMode = previewMode,
                onCropChange = onCropChange,
                onShowOriginalChange = onShowOriginalChange,
                onRevealControls = revealControls,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            AnimatedVisibility(
                visible = panelOpen,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
            ) {
                ToolPanel(
                    selected = tool,
                    settings = state.settings,
                    thumbs = state.thumbs,
                    onSelect = { openTool(it) },
                    onClose = closePanel,
                    onApply = onApply,
                    onCropChange = onCropChange,
                    onAdjustmentsChange = onAdjustmentsChange,
                    onFilterPicked = onFilterPicked,
                    onFilterIntensity = onFilterIntensity,
                    onBlurChange = onBlurChange,
                    applyError = state.applyError,
                    enabled = !state.applying,
                    modifier = Modifier.heightIn(max = panelMaxHeight),
                )
            }
        }

        // Oscurece apenas la zona de los iconos del sistema y de los controles.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.42f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            EditorTopControls(
                resetEnabled = !state.applying,
                undoEnabled = state.canUndo && !state.applying,
                redoEnabled = state.canRedo && !state.applying,
                onBack = {
                    onShowOriginalChange(false)
                    onBack()
                },
                onUndo = {
                    onShowOriginalChange(false)
                    revealControls()
                    onUndo()
                },
                onRedo = {
                    onShowOriginalChange(false)
                    revealControls()
                    onRedo()
                },
                onReset = {
                    onShowOriginalChange(false)
                    revealControls()
                    onReset()
                },
            )
        }

        if (!panelOpen) {
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            ) {
                BottomFloatingControls(
                    previewMode = previewMode,
                    applyError = state.applyError,
                    enabled = !state.applying,
                    onToolSelected = openTool,
                    onPreviewModeChange = changePreviewMode,
                    onApply = onApply,
                )
            }
        }
    }
}

@Composable
private fun PreviewViewport(
    state: EditorUiState,
    preview: Bitmap,
    frameAspect: Float,
    previewMode: EditorPreviewMode,
    onCropChange: (EditorCrop) -> Unit,
    onShowOriginalChange: (Boolean) -> Unit,
    onRevealControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aspect = frameAspect.coerceAtLeast(0.1f)
    val currentCrop by rememberUpdatedState(state.settings.crop)
    val currentOnShowOriginal by rememberUpdatedState(onShowOriginalChange)
    val currentOnRevealControls by rememberUpdatedState(onRevealControls)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val frameWidth = minOf(maxWidth, maxHeight * aspect).coerceAtLeast(1.dp)
        val frameHeight = (frameWidth / aspect).coerceAtLeast(1.dp)
        val density = LocalDensity.current
        val frameWidthPx = with(density) { frameWidth.toPx() }
        val frameHeightPx = with(density) { frameHeight.toPx() }

        LaunchedEffect(frameWidthPx, frameHeightPx, preview.width, preview.height) {
            if (frameWidthPx > 0f && frameHeightPx > 0f) {
                val clamped = EditorPresets.clampCrop(
                    crop = currentCrop,
                    sourceWidth = preview.width,
                    sourceHeight = preview.height,
                    frameWidth = frameWidthPx,
                    frameHeight = frameHeightPx,
                )
                if (clamped != currentCrop) {
                    onCropChange(clamped)
                }
            }
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            if (frameWidthPx > 0f && frameHeightPx > 0f) {
                val updated = currentCrop.copy(
                    zoom = (currentCrop.zoom * zoomChange).coerceIn(1f, EditorMaxZoom),
                    offsetX = currentCrop.offsetX + panChange.x / frameWidthPx,
                    offsetY = currentCrop.offsetY + panChange.y / frameHeightPx,
                )
                onCropChange(
                    EditorPresets.clampCrop(
                        crop = updated,
                        sourceWidth = preview.width,
                        sourceHeight = preview.height,
                        frameWidth = frameWidthPx,
                        frameHeight = frameHeightPx,
                    ),
                )
                currentOnRevealControls()
            }
        }

        Box(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .transformable(transformState)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var canceled = false
                        var released = false
                        val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    canceled = true
                                    return@withTimeoutOrNull false
                                }
                                if (!change.pressed) {
                                    released = true
                                    return@withTimeoutOrNull true
                                }
                                if (change.isConsumed ||
                                    change.position.minus(down.position)
                                        .getDistance() > viewConfiguration.touchSlop
                                ) {
                                    canceled = true
                                    return@withTimeoutOrNull false
                                }
                            }
                            @Suppress("UNREACHABLE_CODE")
                            false
                        }
                        when {
                            result == true && released -> currentOnRevealControls()
                            result == null && !canceled -> {
                                currentOnShowOriginal(true)
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) break
                                    }
                                } finally {
                                    currentOnShowOriginal(false)
                                }
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                EditorImage.drawPreview(
                    canvas = drawContext.canvas.nativeCanvas,
                    width = size.width,
                    height = size.height,
                    source = preview,
                    blurLevels = state.blurLevels,
                    grain = state.grain,
                    settings = state.settings,
                    showOriginal = state.showOriginal,
                )
            }

            when (previewMode) {
                EditorPreviewMode.LOCK -> LockPreviewOverlay()
                EditorPreviewMode.HOME -> HomePreviewOverlay()
                EditorPreviewMode.NONE -> Unit
            }
        }
    }
}

@Composable
private fun BoxScope.LockPreviewOverlay() {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "9:41",
            color = Color.White,
            style = MaterialTheme.typography.displayLarge.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.55f),
                    offset = Offset(0f, 2f),
                    blurRadius = 14f,
                ),
            ),
        )
        Text(
            text = stringResource(R.string.editor_preview_day),
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelMedium.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.55f),
                    blurRadius = 10f,
                ),
            ),
        )
    }
}

@Composable
private fun BoxScope.HomePreviewOverlay() {
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.75f)),
            )
        }
    }
}

@Composable
private fun EditorTopControls(
    resetEnabled: Boolean,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FloatingIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
        )
        Spacer(Modifier.weight(1f))
        FloatingIconButton(
            icon = Icons.AutoMirrored.Rounded.Undo,
            contentDescription = stringResource(R.string.editor_undo),
            enabled = undoEnabled,
            onClick = onUndo,
        )
        Spacer(Modifier.width(8.dp))
        FloatingIconButton(
            icon = Icons.AutoMirrored.Rounded.Redo,
            contentDescription = stringResource(R.string.editor_redo),
            enabled = redoEnabled,
            onClick = onRedo,
        )
        Spacer(Modifier.width(8.dp))
        FloatingIconButton(
            icon = Icons.Rounded.Refresh,
            contentDescription = stringResource(R.string.editor_reset),
            enabled = resetEnabled,
            onClick = onReset,
        )
    }
}

@Composable
private fun FloatingIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun BottomFloatingControls(
    previewMode: EditorPreviewMode,
    applyError: String?,
    enabled: Boolean,
    onToolSelected: (EditorTool) -> Unit,
    onPreviewModeChange: (EditorPreviewMode) -> Unit,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (applyError != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    text = applyError,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (previewMode != EditorPreviewMode.NONE) {
            PreviewModeSelector(
                mode = previewMode,
                onModeChange = onPreviewModeChange,
            )
            Spacer(Modifier.height(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.42f),
            contentColor = Color.White,
        ) {
            Text(
                text = stringResource(R.string.editor_hold_compare),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(6.dp))

        FloatingToolBar(
            previewMode = previewMode,
            onToolSelected = onToolSelected,
            onPreviewModeChange = onPreviewModeChange,
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onApply,
                enabled = enabled,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.action_apply))
            }
        }
    }
}

@Composable
private fun FloatingToolBar(
    previewMode: EditorPreviewMode,
    onToolSelected: (EditorTool) -> Unit,
    onPreviewModeChange: (EditorPreviewMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Black.copy(alpha = 0.58f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorTool.entries.forEach { tool ->
                ToolButton(
                    icon = tool.icon,
                    label = stringResource(tool.labelRes),
                    selected = false,
                    onClick = { onToolSelected(tool) },
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(Color.White.copy(alpha = 0.22f)),
            )
            ToolButton(
                icon = Icons.Rounded.Visibility,
                label = stringResource(R.string.editor_preview),
                selected = previewMode != EditorPreviewMode.NONE,
                onClick = {
                    onPreviewModeChange(
                        if (previewMode == EditorPreviewMode.NONE) {
                            EditorPreviewMode.HOME
                        } else {
                            EditorPreviewMode.NONE
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White
    }
    Column(
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 9.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewModeSelector(
    mode: EditorPreviewMode,
    onModeChange: (EditorPreviewMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.58f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PreviewModeChip(
                label = stringResource(R.string.editor_preview_wallpaper),
                selected = mode == EditorPreviewMode.NONE,
                onClick = { onModeChange(EditorPreviewMode.NONE) },
            )
            PreviewModeChip(
                label = stringResource(R.string.editor_preview_home),
                selected = mode == EditorPreviewMode.HOME,
                onClick = { onModeChange(EditorPreviewMode.HOME) },
            )
            PreviewModeChip(
                label = stringResource(R.string.editor_preview_lock),
                selected = mode == EditorPreviewMode.LOCK,
                onClick = { onModeChange(EditorPreviewMode.LOCK) },
            )
        }
    }
}

@Composable
private fun PreviewModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                Color.White
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ToolPanel(
    selected: EditorTool,
    settings: EditorSettings,
    thumbs: List<EditorFilterThumb>,
    onSelect: (EditorTool) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onCropChange: (EditorCrop) -> Unit,
    onAdjustmentsChange: (EditorAdjustments) -> Unit,
    onFilterPicked: (FilterPreset) -> Unit,
    onFilterIntensity: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
    applyError: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f))
                    .align(Alignment.CenterHorizontally),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(selected.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onApply,
                    enabled = enabled,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.action_apply))
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.editor_close_tools),
                    )
                }
            }
            ToolSelector(selected = selected, onSelect = onSelect)
            if (applyError != null) {
                Text(
                    text = applyError,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 16.dp),
            ) {
                when (selected) {
                    EditorTool.CROP -> CropPanel(
                        crop = settings.crop,
                        onChange = onCropChange,
                    )
                    EditorTool.ADJUSTMENTS -> AdjustmentsPanel(
                        adjustments = settings.adjustments,
                        onChange = onAdjustmentsChange,
                    )
                    EditorTool.FILTERS -> FiltersPanel(
                        thumbs = thumbs,
                        selectedId = settings.filterId,
                        intensity = settings.filterIntensity,
                        onSelect = onFilterPicked,
                        onIntensityChange = onFilterIntensity,
                    )
                    EditorTool.BLUR -> BlurPanel(
                        blur = settings.blur,
                        onChange = onBlurChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolSelector(selected: EditorTool, onSelect: (EditorTool) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditorTool.entries.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}

@Composable
private fun CropPanel(crop: EditorCrop, onChange: (EditorCrop) -> Unit) {
    Text(
        text = stringResource(R.string.editor_pinch_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.editor_zoom, (crop.zoom * 100f).roundToInt()),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange(EditorCrop()) }) {
            Text(stringResource(R.string.editor_center))
        }
    }
}

@Composable
private fun AdjustmentsPanel(
    adjustments: EditorAdjustments,
    onChange: (EditorAdjustments) -> Unit,
) {
    EditorSlider(R.string.editor_brightness, adjustments.brightness, -1f..1f) {
        onChange(adjustments.copy(brightness = it))
    }
    EditorSlider(R.string.editor_contrast, adjustments.contrast, -1f..1f) {
        onChange(adjustments.copy(contrast = it))
    }
    EditorSlider(R.string.editor_saturation, adjustments.saturation, -1f..1f) {
        onChange(adjustments.copy(saturation = it))
    }
    EditorSlider(R.string.editor_warmth, adjustments.warmth, -1f..1f) {
        onChange(adjustments.copy(warmth = it))
    }
    EditorSlider(R.string.editor_tint, adjustments.tint, -1f..1f) {
        onChange(adjustments.copy(tint = it))
    }
    EditorSlider(R.string.editor_vignette, adjustments.vignette, 0f..1f) {
        onChange(adjustments.copy(vignette = it))
    }
    EditorSlider(R.string.editor_grain, adjustments.grain, 0f..1f) {
        onChange(adjustments.copy(grain = it))
    }
}

@Composable
private fun FiltersPanel(
    thumbs: List<EditorFilterThumb>,
    selectedId: String,
    intensity: Float,
    onSelect: (FilterPreset) -> Unit,
    onIntensityChange: (Float) -> Unit,
) {
    if (thumbs.isEmpty()) {
        Text(stringResource(R.string.editor_preparing_filters), style = MaterialTheme.typography.bodyMedium)
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(thumbs, key = { it.preset.id }) { thumb ->
            val image = remember(thumb.bitmap) { thumb.bitmap.asImageBitmap() }
            val presetName = stringResource(thumb.preset.nameRes)
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .clickable { onSelect(thumb.preset) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = image,
                    contentDescription = presetName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 84.dp, height = 140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (thumb.preset.id == selectedId) 2.dp else 1.dp,
                            color = if (thumb.preset.id == selectedId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(12.dp),
                        ),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = presetName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    if (selectedId != "original") {
        Spacer(Modifier.height(8.dp))
        EditorSlider(R.string.editor_intensity, intensity, 0f..1f, onValueChange = onIntensityChange)
    }
}

@Composable
private fun BlurPanel(blur: Float, onChange: (Float) -> Unit) {
    Text(
        text = stringResource(R.string.editor_blur_help),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    EditorSlider(R.string.editor_intensity, blur, 0f..1f, onValueChange = onChange)
}

@Composable
private fun EditorSlider(
    @StringRes labelRes: Int,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(value * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}
