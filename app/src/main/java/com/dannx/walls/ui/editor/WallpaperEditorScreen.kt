package com.dannx.walls.ui.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.ui.components.ApplyWallpaperSheet
import com.dannx.walls.util.WallpaperTarget
import com.dannx.walls.util.wallpaperTargetSize
import kotlin.math.roundToInt

private enum class EditorTool(val label: String) {
    CROP("Encuadre"),
    ADJUSTMENTS("Ajustes"),
    FILTERS("Filtros"),
    BLUR("Desenfoque"),
}

/**
 * Editor de una imagen fija. No usa parallax ni APIs de Android 12: el
 * desenfoque se hace sobre una copia reducida y el color con ColorMatrix.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var toolName by rememberSaveable { mutableStateOf(EditorTool.CROP.name) }
    val tool = EditorTool.entries.firstOrNull { it.name == toolName } ?: EditorTool.CROP
    val targetSize = remember(application) { wallpaperTargetSize(application) }
    val frameAspect = targetSize.x.toFloat() / targetSize.y.toFloat()

    LaunchedEffect(state.appliedTarget) {
        state.appliedTarget?.let(onApplied)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editor") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            editorViewModel.cancelApply()
                            onBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = editorViewModel::reset,
                        enabled = !state.loading && !state.applying,
                    ) {
                        Text("Restablecer")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> LoadingState(state.loadProgress)
                state.loadError != null -> ErrorState(
                    message = state.loadError ?: "No se pudo abrir la imagen.",
                    onRetry = editorViewModel::load,
                )
                else -> EditorBody(
                    state = state,
                    frameAspect = frameAspect,
                    tool = tool,
                    onToolChange = { toolName = it.name },
                    onCropChange = editorViewModel::updateCrop,
                    onAdjustmentsChange = editorViewModel::updateAdjustments,
                    onFilterPicked = editorViewModel::selectFilter,
                    onFilterIntensity = editorViewModel::updateFilterIntensity,
                    onBlurChange = editorViewModel::updateBlur,
                    onShowOriginalChange = editorViewModel::setShowOriginal,
                    onApply = { showTargetSheet = true },
                )
            }

            if (state.applying) {
                ApplyProgressOverlay(state.applyProgress)
            }
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text("Preparando la imagen…")
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                Text("Reintentar")
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
                Text("Generando el fondo…")
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
    tool: EditorTool,
    onToolChange: (EditorTool) -> Unit,
    onCropChange: (EditorCrop) -> Unit,
    onAdjustmentsChange: (EditorAdjustments) -> Unit,
    onFilterPicked: (FilterPreset) -> Unit,
    onFilterIntensity: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
    onShowOriginalChange: (Boolean) -> Unit,
    onApply: () -> Unit,
) {
    val preview = state.preview ?: return
    Column(Modifier.fillMaxSize()) {
        PreviewSection(
            state = state,
            preview = preview,
            frameAspect = frameAspect,
            onCropChange = onCropChange,
            modifier = Modifier.weight(0.48f),
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HoldToCompareButton(
                active = state.showOriginal,
                onChange = onShowOriginalChange,
            )
        }
        ToolSelector(selected = tool, onSelect = onToolChange)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when (tool) {
                EditorTool.CROP -> CropPanel(
                    crop = state.settings.crop,
                    onChange = onCropChange,
                )
                EditorTool.ADJUSTMENTS -> AdjustmentsPanel(
                    adjustments = state.settings.adjustments,
                    onChange = onAdjustmentsChange,
                )
                EditorTool.FILTERS -> FiltersPanel(
                    thumbs = state.thumbs,
                    selectedId = state.settings.filterId,
                    intensity = state.settings.filterIntensity,
                    onSelect = onFilterPicked,
                    onIntensityChange = onFilterIntensity,
                )
                EditorTool.BLUR -> BlurPanel(
                    blur = state.settings.blur,
                    onChange = onBlurChange,
                )
            }
        }
        BottomBar(
            applyError = state.applyError,
            enabled = !state.applying,
            onApply = onApply,
        )
    }
}

@Composable
private fun PreviewSection(
    state: EditorUiState,
    preview: Bitmap,
    frameAspect: Float,
    onCropChange: (EditorCrop) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        val gap = 10.dp
        val frameWidth = minOf((maxWidth - gap) / 2f, maxHeight * frameAspect)
            .coerceAtLeast(1.dp)
        val frameHeight = (frameWidth / frameAspect).coerceAtLeast(1.dp)
        val density = LocalDensity.current
        val frameWidthPx = with(density) { frameWidth.toPx() }
        val frameHeightPx = with(density) { frameHeight.toPx() }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            if (frameWidthPx > 0f && frameHeightPx > 0f) {
                val current = state.settings.crop
                val updated = current.copy(
                    zoom = (current.zoom * zoomChange).coerceIn(1f, EditorMaxZoom),
                    offsetX = current.offsetX + panChange.x / frameWidthPx,
                    offsetY = current.offsetY + panChange.y / frameHeightPx,
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
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .transformable(transformState),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorPhoneFrame(
                label = "Inicio",
                lock = false,
                state = state,
                preview = preview,
                modifier = Modifier
                    .width(frameWidth)
                    .height(frameHeight),
            )
            EditorPhoneFrame(
                label = "Bloqueo",
                lock = true,
                state = state,
                preview = preview,
                modifier = Modifier
                    .width(frameWidth)
                    .height(frameHeight),
            )
        }
    }
}

@Composable
private fun EditorPhoneFrame(
    label: String,
    lock: Boolean,
    state: EditorUiState,
    preview: Bitmap,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            EditorImage.drawPreview(
                canvas = drawContext.canvas.nativeCanvas,
                width = size.width,
                height = size.height,
                source = preview,
                blurred = state.blurredPreview,
                grain = state.grain,
                settings = state.settings,
                showOriginal = state.showOriginal,
            )
        }
        if (lock) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "9:41",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    text = "Lunes 8",
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.58f)),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.46f),
            contentColor = Color.White,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun HoldToCompareButton(active: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onChange(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            onChange(false)
                        }
                    },
                )
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (active) "Soltá para volver" else "Mantené para comparar",
            color = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelMedium,
        )
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
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun CropPanel(crop: EditorCrop, onChange: (EditorCrop) -> Unit) {
    Text(
        text = "Pellizcá con dos dedos para acercar y arrastrá para mover.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Zoom ${(crop.zoom * 100f).roundToInt()}%",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange(EditorCrop()) }) {
            Text("Centrar")
        }
    }
}

@Composable
private fun AdjustmentsPanel(
    adjustments: EditorAdjustments,
    onChange: (EditorAdjustments) -> Unit,
) {
    EditorSlider("Brillo", adjustments.brightness, -1f..1f) {
        onChange(adjustments.copy(brightness = it))
    }
    EditorSlider("Contraste", adjustments.contrast, -1f..1f) {
        onChange(adjustments.copy(contrast = it))
    }
    EditorSlider("Saturación", adjustments.saturation, -1f..1f) {
        onChange(adjustments.copy(saturation = it))
    }
    EditorSlider("Calidez", adjustments.warmth, -1f..1f) {
        onChange(adjustments.copy(warmth = it))
    }
    EditorSlider("Matiz", adjustments.tint, -1f..1f) {
        onChange(adjustments.copy(tint = it))
    }
    EditorSlider("Viñeta", adjustments.vignette, 0f..1f) {
        onChange(adjustments.copy(vignette = it))
    }
    EditorSlider("Grano", adjustments.grain, 0f..1f) {
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
        Text("Preparando filtros…", style = MaterialTheme.typography.bodyMedium)
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(thumbs, key = { it.preset.id }) { thumb ->
            val image = remember(thumb.bitmap) { thumb.bitmap.asImageBitmap() }
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .clickable { onSelect(thumb.preset) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = image,
                    contentDescription = thumb.preset.name,
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
                    text = thumb.preset.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    if (selectedId != "original") {
        Spacer(Modifier.height(8.dp))
        EditorSlider("Intensidad", intensity, 0f..1f, onValueChange = onIntensityChange)
    }
}

@Composable
private fun BlurPanel(blur: Float, onChange: (Float) -> Unit) {
    Text(
        text = "Suaviza la imagen. Se calcula sobre una copia reducida para no gastar memoria.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    EditorSlider("Intensidad", blur, 0f..1f, onValueChange = onChange)
}

@Composable
private fun EditorSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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

@Composable
private fun BottomBar(
    applyError: String?,
    enabled: Boolean,
    onApply: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (applyError != null) {
                Text(
                    text = applyError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "El resultado se aplica al confirmar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onApply, enabled = enabled) {
                    Text("Aplicar")
                }
            }
        }
    }
}
