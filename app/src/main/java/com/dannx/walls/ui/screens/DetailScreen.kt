package com.dannx.walls.ui.screens

import android.text.format.DateFormat as AndroidDateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.dannx.walls.ui.components.WallpaperCard
import com.dannx.walls.ui.theme.EmphasizedEasing
import com.dannx.walls.ui.components.rememberFavoriteBounce
import com.dannx.walls.ui.components.sharedWallpaperImage
import com.dannx.walls.ui.components.rememberWallpaperAccent
import com.dannx.walls.ui.components.wallpaperThumbRequest
import com.dannx.walls.ui.theme.LocalLiteMode
import com.dannx.walls.ui.theme.LocalReduceMotion
import com.dannx.walls.ui.theme.Motion
import com.dannx.walls.ui.theme.MotionDuration
import com.dannx.walls.util.DoubleTapZoom
import com.dannx.walls.util.MinZoom
import com.dannx.walls.util.PanOffset
import com.dannx.walls.util.WallpaperTarget
import com.dannx.walls.util.ZoomState
import com.dannx.walls.util.clampPan
import com.dannx.walls.util.clampScale
import com.dannx.walls.wallpaper.LiveWallpaperStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ZoomStateSaver = listSaver<ZoomState, Float>(
    save = { listOf(it.scale, it.offset.x, it.offset.y) },
    restore = { ZoomState(it[0], PanOffset(it[1], it[2])) },
)

/** Estados visuales del botón Aplicar mientras el fondo se prepara. */
private enum class ApplyPhase { Idle, Loading, Done }

/** Verde suave para confirmar que el fondo se aplicó. */
private val ApplySuccessGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    collections: List<WallpaperCollection>,
    canManageCollections: Boolean,
    canChooseCollectionDestination: Boolean,
    collectionBusy: Boolean,
    actionState: ActionState = ActionState(),
    applySuccessTick: Int = 0,
    downloadPrompt: DownloadPrompt? = null,
    alreadyDownloaded: Boolean = false,
    offline: Boolean = false,
    openEditorOnApply: Boolean = true,
    liveReadyTick: Int = 0,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onOpenEditor: () -> Unit,
    onSetWallpaper: (WallpaperTarget) -> Unit,
    onSetLiveWallpaper: () -> Unit = {},
    onLiveWallpaperApplied: () -> Unit = {},
    onShare: () -> Unit,
    onCancelAction: () -> Unit,
    onOpenFile: (String) -> Unit,
    onDownloadAgain: (String) -> Unit,
    onDismissDownloadPrompt: () -> Unit,
    onAddToCollection: (String) -> Unit,
    onCreateCollection: (String, String, Boolean, Boolean) -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    related: List<Wallpaper> = emptyList(),
    onOpenRelated: (Wallpaper) -> Unit = {},
) {
    var zoom by rememberSaveable(wallpaper.id, stateSaver = ZoomStateSaver) {
        mutableStateOf(ZoomState())
    }
    var showCollections by rememberSaveable { mutableStateOf(false) }
    var showCreateCollection by rememberSaveable { mutableStateOf(false) }
    var showApplySheet by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable(wallpaper.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val favoriteScale = rememberFavoriteBounce(isFavorite)
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
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    BackHandler(enabled = showPreview) { showPreview = false }
    var applyPhase by remember { mutableStateOf(ApplyPhase.Idle) }
    var seenApplyTick by remember { mutableIntStateOf(applySuccessTick) }
    LaunchedEffect(applySuccessTick) {
        if (applySuccessTick > seenApplyTick) {
            seenApplyTick = applySuccessTick
            applyPhase = ApplyPhase.Done
            delay(2_000)
            if (applyPhase == ApplyPhase.Done) applyPhase = ApplyPhase.Idle
        }
    }
    LaunchedEffect(actionState.active) {
        if (actionState.active == UiAction.APPLY) {
            applyPhase = ApplyPhase.Loading
        } else if (applyPhase == ApplyPhase.Loading) {
            // Si el fondo no se pudo aplicar, el botón vuelve solo.
            delay(800)
            if (applyPhase == ApplyPhase.Loading) applyPhase = ApplyPhase.Idle
        }
    }

    val appContext = LocalContext.current
    val liveStore = remember(appContext) { LiveWallpaperStore(appContext) }
    var seenLiveTick by remember { mutableIntStateOf(liveReadyTick) }
    val liveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // El selector no devuelve un resultado confiable: al volver se
        // consulta qué fondo animado quedó puesto.
        if (liveStore.isActive()) {
            applyPhase = ApplyPhase.Loading
            onLiveWallpaperApplied()
        } else {
            applyPhase = ApplyPhase.Idle
        }
    }
    LaunchedEffect(liveReadyTick) {
        if (liveReadyTick <= seenLiveTick) return@LaunchedEffect
        seenLiveTick = liveReadyTick
        val opened = runCatching { liveLauncher.launch(liveStore.chooserIntent()) }.isSuccess
        if (!opened) applyPhase = ApplyPhase.Idle
    }
    var entered by remember { mutableStateOf(false) }
    var fullImageLoaded by remember(wallpaper.id) { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val fullImageAlpha by animateFloatAsState(
        targetValue = if (fullImageLoaded) 1f else 0f,
        animationSpec = Motion.fade(reduceMotion, MotionDuration.Medium),
        label = "detailFullImageAlpha",
    )
    val enterScale by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 1.04f,
        animationSpec = Motion.fade(reduceMotion, MotionDuration.ExtraSlow),
        label = "detailEnterScale",
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 0f,
        animationSpec = Motion.fade(reduceMotion, MotionDuration.Slow),
        label = "detailEnterAlpha",
    )
    // Los controles entran un instante después de la foto, con una subida corta.
    val controlsAlpha by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 0f,
        animationSpec = Motion.fade(
            reduceMotion = reduceMotion,
            durationMillis = MotionDuration.Slow,
            delayMillis = 70,
        ),
        label = "detailControlsAlpha",
    )
    val controlsRisePx = with(density) { 28.dp.toPx() }
    val controlsRise by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 0f else controlsRisePx,
        animationSpec = Motion.fade(
            reduceMotion = reduceMotion,
            durationMillis = MotionDuration.Slow,
            delayMillis = 70,
        ),
        label = "detailControlsRise",
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
            liteMode,
        ) {
            wallpaperThumbRequest(context, wallpaper, liteMode)
                .newBuilder()
                .networkCachePolicy(CachePolicy.DISABLED)
                .build()
        }
        val imageRequest = remember(
            wallpaper.id,
            wallpaper.imageUrl,
            windowSize,
            liteMode,
        ) {
            // En lite la imagen de pantalla completa se decodifica más chica:
            // pesa la mitad en memoria a costa de algo de nitidez al hacer zoom.
            val scale = if (liteMode) 0.7f else 1f
            ImageRequest.Builder(context)
                .data(wallpaper.imageUrl)
                .size(
                    (windowSize.width * scale).toInt(),
                    (windowSize.height * scale).toInt(),
                )
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
                                    animationSpec = Motion.spatial(reduceMotion),
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
            // Esta capa es la que viaja desde la tarjeta hasta acá y vuelve al
            // salir; el zoom y el fundido viven en el contenedor de arriba.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .sharedWallpaperImage(wallpaper.id),
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

        // Mientras se simula la pantalla de bloqueo se oculta el contenido del
        // detalle para que el reloj y los accesos no se superpongan con el
        // título, las etiquetas, los botones ni la fila de relacionados.
        if (!showPreview) {
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
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showPreview = true
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.35f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.PhoneAndroid,
                            contentDescription = stringResource(R.string.detail_preview),
                        )
                    }
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
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleFavorite()
                        },
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
                            modifier = Modifier.graphicsLayer {
                                scaleX = favoriteScale
                                scaleY = favoriteScale
                            },
                        )
                    }
                }
            }

            if (zoom.scale <= MinZoom + 0.01f) {
                onPrevious?.let { previous ->
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            previous()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.35f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = stringResource(R.string.detail_previous),
                        )
                    }
                }
                onNext?.let { next ->
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            next()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.35f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = stringResource(R.string.detail_next),
                        )
                    }
                }
            }

            // Halo con el color de la foto detrás de los controles. Es un
            // degradado, no un desenfoque: cuesta una fracción de frame.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to accent.copy(alpha = 0.14f),
                            1f to accent.copy(alpha = 0.32f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = controlsAlpha
                        translationY = controlsRise
                    }
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailPill(wallpaper.category.uppercase(Locale.ROOT))
                    if (wallpaper.width > 0 && wallpaper.height > 0) {
                        DetailPill(
                            stringResource(
                                R.string.detail_resolution,
                                wallpaper.width,
                                wallpaper.height,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = wallpaper.title,
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.detail_by_author, wallpaper.author),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
                if (wallpaper.tags.isNotEmpty()) {
                    val visibleTags = wallpaper.tags.take(MaxTagChips)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        visibleTags.forEach { tag ->
                            DetailPill("#$tag")
                        }
                        val hidden = wallpaper.tags.size - visibleTags.size
                        if (hidden > 0) {
                            DetailPill("+$hidden")
                        }
                    }
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
                val applyContainer by animateColorAsState(
                    targetValue = if (applyPhase == ApplyPhase.Done) {
                        ApplySuccessGreen
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    animationSpec = Motion.fade(reduceMotion, MotionDuration.Medium),
                    label = "applyButtonColor",
                )
                val applyContent = if (applyPhase == ApplyPhase.Done) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
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
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(
                                animationSpec = Motion.spatial(reduceMotion),
                            ),
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = applyContainer,
                            contentColor = applyContent,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        enabled = actionState.active == null && !offline,
                    ) {
                        AnimatedContent(
                            targetState = applyPhase,
                            transitionSpec = {
                                if (reduceMotion) {
                                    fadeIn(tween(MotionDuration.Fast)) togetherWith
                                        fadeOut(tween(MotionDuration.Fast))
                                } else {
                                    (
                                        fadeIn(tween(160, easing = EmphasizedEasing)) +
                                            scaleIn(spring(), initialScale = 0.9f)
                                        ) togetherWith fadeOut(tween(100))
                                }
                            },
                            label = "applyPhase",
                        ) { phase ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (phase) {
                                    ApplyPhase.Idle -> {
                                        Icon(Icons.Rounded.Wallpaper, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.action_apply))
                                    }

                                    ApplyPhase.Loading -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = applyContent,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.apply_button_loading))
                                    }

                                    ApplyPhase.Done -> {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.apply_button_done))
                                    }
                                }
                            }
                        }
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
                val relatedItems = related.filterNot { it.id == wallpaper.id }.take(5)
                if (relatedItems.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.detail_related_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 4.dp),
                    ) {
                        items(items = relatedItems, key = { it.id }) { item ->
                            WallpaperCard(
                                wallpaper = item,
                                isFavorite = false,
                                onClick = { onOpenRelated(item) },
                                onToggleFavorite = {},
                                modifier = Modifier
                                    .width(116.dp)
                                    .height(174.dp),
                                showFavorite = false,
                                compact = true,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPreview,
            enter = if (reduceMotion) {
                fadeIn(tween(MotionDuration.Fast))
            } else {
                fadeIn(tween(MotionDuration.Medium)) +
                    scaleIn(
                        tween(MotionDuration.Slow, easing = EmphasizedEasing),
                        initialScale = 0.92f,
                    )
            },
            exit = fadeOut(tween(if (reduceMotion) MotionDuration.Fast else 160)),
            modifier = Modifier.fillMaxSize(),
        ) {
            LockScreenPreview(onDismiss = { showPreview = false })
        }
    }

    if (showApplySheet) {
        ApplyWallpaperSheet(
            onDismiss = { showApplySheet = false },
            onPick = { target ->
                showApplySheet = false
                applyPhase = ApplyPhase.Loading
                onSetWallpaper(target)
            },
            onPickLive = {
                showApplySheet = false
                applyPhase = ApplyPhase.Loading
                onSetLiveWallpaper()
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
            canChooseDestination = canChooseCollectionDestination,
            onDismiss = { showCreateCollection = false },
            onCreate = { name, description, isPublic, onDevice ->
                onCreateCollection(name, description, isPublic, onDevice)
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
    canChooseDestination: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(false) }
    var onDevice by rememberSaveable { mutableStateOf(!canChooseDestination) }
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

/** Cuántas etiquetas se muestran como píldoras antes de resumir el resto. */
private const val MaxTagChips = 6

/** Píldora translúcida sobre la foto: categoría, resolución y etiquetas. */
@Composable
private fun DetailPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.10f), CircleShape)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.30f),
                shape = CircleShape,
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
        )
    }
}

/**
 * Simulación de la pantalla de bloqueo sobre el fondo: hora, fecha y accesos
 * genéricos. Se cierra tocando en cualquier lado o con la X.
 */
@Composable
private fun LockScreenPreview(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000)
        }
    }
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    // Android no expone el reloj de su pantalla de bloqueo a las apps, así que
    // se imita el estilo usando los mismos ajustes del sistema: formato de 12 o
    // 24 horas y patrones de fecha del idioma actual.
    val is24Hour = AndroidDateFormat.is24HourFormat(context)
    val time = remember(now.hour, now.minute, is24Hour, locale) {
        val pattern = if (is24Hour) "HH:mm" else "h:mm"
        now.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    val period = remember(now.hour, now.minute, is24Hour, locale) {
        if (is24Hour) {
            null
        } else {
            now.format(DateTimeFormatter.ofPattern("a", locale))
                .replace(".", "")
                .replace(" ", "")
                .uppercase(locale)
        }
    }
    val date = remember(now.year, now.monthValue, now.dayOfMonth, locale) {
        val pattern = AndroidDateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        val formatted = now.format(DateTimeFormatter.ofPattern(pattern, locale))
        formatted.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
    }
    val dismissInteraction = remember { MutableInteractionSource() }
    val clockShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.35f),
            offset = Offset(0f, 2f),
            blurRadius = 14f,
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.5f),
                    0.3f to Color.Black.copy(alpha = 0.16f),
                    0.7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.6f),
                ),
            )
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                FilledIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.detail_preview_close),
                    )
                }
            }
            Spacer(Modifier.weight(0.3f))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 76.sp,
                            lineHeight = 78.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-2).sp,
                            shadow = clockShadow,
                        ),
                        color = Color.White,
                        maxLines = 1,
                    )
                    period?.let { suffix ->
                        Text(
                            text = suffix,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleMedium.copy(shadow = clockShadow),
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LockShortcut(Icons.Rounded.PhotoCamera)
                LockShortcut(Icons.Rounded.Phone)
                LockShortcut(Icons.Rounded.Message)
            }
        }
    }
}

@Composable
private fun LockShortcut(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}
