package com.dannx.walls.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.annotation.StringRes
import com.dannx.walls.BuildConfig
import com.dannx.walls.LoadState
import com.dannx.walls.MainViewModel
import com.dannx.walls.MessageAction
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.ui.components.DebugFpsOverlay
import com.dannx.walls.ui.components.ErrorState
import com.dannx.walls.ui.editor.WallpaperEditorScreen
import com.dannx.walls.ui.screens.AboutScreen
import com.dannx.walls.ui.screens.CollectionDetailScreen
import com.dannx.walls.ui.screens.CollectionsScreen
import com.dannx.walls.ui.screens.DecoyScreen
import com.dannx.walls.ui.screens.DetailScreen
import com.dannx.walls.ui.screens.FavoritesScreen
import com.dannx.walls.ui.screens.HomeScreen
import com.dannx.walls.ui.screens.OnboardingScreen
import com.dannx.walls.ui.screens.SettingsScreen
import com.dannx.walls.ui.screens.StudioScreen
import com.dannx.walls.ui.theme.LocalLiteMode
import com.dannx.walls.ui.update.UpdateState
import com.dannx.walls.ui.update.UpdateViewModel
import com.dannx.walls.util.WallpaperFiles

private data class TopLevelTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val tabs = listOf(
    TopLevelTab("home", R.string.home_section_explore, Icons.Rounded.Explore),
    TopLevelTab("collections", R.string.collections_title, Icons.Rounded.CollectionsBookmark),
    TopLevelTab("favorites", R.string.favorites_title, Icons.Rounded.FavoriteBorder),
)

@Composable
fun AppRoot(viewModel: MainViewModel, startTab: String = "home") {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = state.onboardingDone,
        transitionSpec = {
            (fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.97f,
                    animationSpec = tween(380, easing = FastOutSlowInEasing),
                ))
                .togetherWith(fadeOut(tween(240)))
        },
        label = "appRoot",
    ) { done ->
        if (done) {
            MainApp(viewModel = viewModel, state = state, startTab = startTab)
        } else {
            OnboardingScreen(
                liteMode = state.liteMode,
                onFinish = viewModel::completeOnboarding,
            )
        }
    }
}

@Composable
private fun MainApp(
    viewModel: MainViewModel,
    state: UiState,
    startTab: String,
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val updateViewModel: UpdateViewModel = viewModel(
        factory = UpdateViewModel.factory(application),
    )
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val updateAvailable = updateState is UpdateState.Available
    LaunchedEffect(Unit) {
        updateViewModel.checkInBackground(BuildConfig.VERSION_NAME)
    }
    val wallpaperFiles = remember(context) { WallpaperFiles(context) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // El Estudio no aparece en la barra: se entra con el gesto secreto de la
    // pantalla de Explorar (mantener presionado el título).
    val topTabs = tabs
    val topTabRoutes: Set<String?> = topTabs.map { it.route }.toSet()
    val showBar = currentRoute in topTabRoutes
    val startDestination = if (startTab in topTabs.map { it.route }) startTab else "home"
    val liteMode = LocalLiteMode.current
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    // Tacto de confirmación cuando el fondo terminó de aplicarse. Se guarda el
    // último aviso visto para que no vibre al volver a entrar a la pantalla.
    var seenApplyTick by remember { mutableIntStateOf(state.applyTick) }
    LaunchedEffect(state.applyTick) {
        if (state.applyTick > seenApplyTick) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            seenApplyTick = state.applyTick
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshIfOnline()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            val action = state.messageAction
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = action?.label,
                withDismissAction = action == null,
            )
            var openFailed = false
            if (result == SnackbarResult.ActionPerformed && action != null) {
                when (action) {
                    is MessageAction.OpenFile -> {
                        openFailed = !wallpaperFiles.open(action.uri)
                    }

                    is MessageAction.RetryDownload -> {
                        viewModel.downloadAgain(action.wallpaperId)
                    }
                }
            }
            if (openFailed) {
                viewModel.notifyFileOpenFailed()
            } else {
                viewModel.clearMessage()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // La barra ya no vive acá: ahora cada pestaña dibuja la suya. El
        // snackbar se corre hacia arriba cuando hay barra para no taparla.
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = if (showBar) 80.dp else 0.dp),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = {
                    val targetRoute = targetState.destination.route
                    val initialRoute = initialState.destination.route
                    val randomDetail = isDetailRoute(targetRoute) &&
                        targetState.arguments?.getBoolean("random") == true
                    val detailToDetail = isDetailRoute(initialRoute) && isDetailRoute(targetRoute)
                    when {
                        liteMode -> fadeIn(tween(120))
                        randomDetail -> fadeIn(tween(340))
                        detailToDetail -> fadeIn(tween(300))
                        isDetailRoute(targetRoute) -> {
                            // Del feed al detalle: entra desde la derecha con resorte.
                            slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) { it } + fadeIn(tween(200))
                        }

                        isSecondaryRoute(targetRoute) -> {
                            // Ajustes, colecciones y editor: aparecen sin empujar.
                            fadeIn(tween(240)) +
                                scaleIn(
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    initialScale = 0.95f,
                                )
                        }

                        else -> {
                            fadeIn(tween(240)) +
                                slideInHorizontally(
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                ) { it / 14 } +
                                scaleIn(
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                    initialScale = 0.985f,
                                )
                        }
                    }
                },
                exitTransition = {
                    val targetRoute = targetState.destination.route
                    val randomDetail = isDetailRoute(targetRoute) &&
                        targetState.arguments?.getBoolean("random") == true
                    val detailToDetail = isDetailRoute(initialState.destination.route) &&
                        isDetailRoute(targetRoute)
                    when {
                        liteMode -> fadeOut(tween(90))
                        randomDetail || detailToDetail -> fadeOut(tween(300))
                        isDetailRoute(targetRoute) -> fadeOut(tween(180))
                        isSecondaryRoute(targetRoute) -> {
                            fadeOut(tween(180)) +
                                scaleOut(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    targetScale = 0.98f,
                                )
                        }

                        else -> {
                            fadeOut(tween(160)) +
                                scaleOut(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    targetScale = 0.99f,
                                )
                        }
                    }
                },
                popEnterTransition = {
                    when {
                        liteMode -> fadeIn(tween(120))
                        targetState.destination.route in topTabRoutes -> {
                            // La pantalla de atrás ya está quieta y visible; la
                            // que sale es la que hace el movimiento.
                            EnterTransition.None
                        }

                        isSecondaryRoute(initialState.destination.route) -> {
                            fadeIn(tween(220)) +
                                scaleIn(
                                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                                    initialScale = 0.98f,
                                )
                        }

                        else -> EnterTransition.None
                    }
                },
                popExitTransition = {
                    val leaving = initialState.destination.route
                    when {
                        liteMode -> fadeOut(tween(90))
                        isDetailRoute(leaving) -> {
                            // Al volver, el detalle se va hacia la derecha.
                            slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) { it } + fadeOut(tween(200))
                        }

                        isSecondaryRoute(leaving) -> {
                            fadeOut(tween(180)) +
                                scaleOut(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    targetScale = 0.97f,
                                )
                        }

                        else -> {
                            scaleOut(
                                animationSpec = tween(220, easing = LinearEasing),
                                targetScale = 0.9f,
                                transformOrigin = TransformOrigin.Center,
                            )
                        }
                    }
                },
                predictivePopEnterTransition = {
                    if (targetState.destination.route in topTabRoutes) {
                        // Volver a una pestaña no mueve la barra.
                        EnterTransition.None
                    } else if (liteMode) {
                        fadeIn(tween(120))
                    } else {
                        EnterTransition.None
                    }
                },
                predictivePopExitTransition = {
                    val leaving = initialState.destination.route
                    when {
                        leaving in topTabRoutes -> {
                            // Salir de una pestaña hacia otra no escala la barra.
                            ExitTransition.None
                        }

                        liteMode -> fadeOut(tween(90))
                        isDetailRoute(leaving) -> {
                            slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) { it } + fadeOut(tween(200))
                        }

                        isSecondaryRoute(leaving) -> {
                            fadeOut(tween(180)) +
                                scaleOut(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    targetScale = 0.97f,
                                )
                        }

                        else -> {
                            scaleOut(
                                animationSpec = tween(220, easing = LinearEasing),
                                targetScale = 0.9f,
                                transformOrigin = TransformOrigin.Center,
                            )
                        }
                    }
                },
            ) {
            composable(
                route = "home",
                // La barra de cada pestaña no se anima al cambiar de pestaña:
                // se queda quieta y solo cambia el ítem marcado.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                TopLevelScaffold(
                    selectedRoute = "home",
                    onNavigate = { route -> navController.navigateTopLevel(route) },
                ) {
                    HomeScreen(
                        state = state,
                        onClearFilters = {
                            viewModel.setQuery("")
                            viewModel.setCategory(null)
                            viewModel.toggleTag(null)
                            viewModel.setColor(null)
                        },
                        onQueryChange = viewModel::setQuery,
                        onCategoryChange = viewModel::setCategory,
                        onTagChange = viewModel::toggleTag,
                        onColorChange = viewModel::setColor,
                        onRefresh = viewModel::refresh,
                        onOpenWallpaper = { wallpaper ->
                            viewModel.rememberCurrentSearch()
                            navController.navigate("detail/${wallpaper.id}") {
                                launchSingleTop = true
                            }
                        },
                        onRandomWallpaper = { wallpaper ->
                            viewModel.rememberCurrentSearch()
                            navController.navigate("detail/${wallpaper.id}?random=true") {
                                launchSingleTop = true
                            }
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onOpenStudio = {
                            if (state.session != null || state.isDemo) {
                                navController.navigateTopLevel("studio")
                            } else {
                                viewModel.rememberDecoyOpened()
                                navController.navigate("decoy") {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenSettings = { navController.navigate("settings") },
                        updateAvailable = updateAvailable,
                        onSearchSubmit = viewModel::rememberCurrentSearch,
                        onClearRecentSearches = viewModel::clearRecentSearches,
                        onLoadMore = viewModel::loadMore,
                    )
                }
            }
            composable(
                route = "favorites",
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                TopLevelScaffold(
                    selectedRoute = "favorites",
                    onNavigate = { route -> navController.navigateTopLevel(route) },
                ) {
                    FavoritesScreen(
                        state = state,
                        onRefresh = viewModel::refresh,
                        onOpenWallpaper = { wallpaper ->
                            navController.navigate("detail/${wallpaper.id}") {
                                launchSingleTop = true
                            }
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onExplore = { navController.navigateTopLevel("home") },
                        onDownloadMany = viewModel::downloadMany,
                        onShareMany = viewModel::shareMany,
                        onRemoveFavorites = viewModel::removeFavorites,
                    )
                }
            }
            composable(
                route = "collections",
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                TopLevelScaffold(
                    selectedRoute = "collections",
                    onNavigate = { route -> navController.navigateTopLevel(route) },
                ) {
                    CollectionsScreen(
                        state = state,
                        onRefresh = viewModel::refresh,
                        onOpenCollection = { collection ->
                            navController.navigate("collection/${collection.id}") {
                                launchSingleTop = true
                            }
                        },
                        onCreateCollection = { name, description, isPublic, onDevice ->
                            viewModel.createCollection(name, description, isPublic, onDevice)
                        },
                    )
                }
            }
            composable(
                route = "collection/{id}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                LaunchedEffect(id) {
                    viewModel.loadCollectionDetail(id)
                }
                val detail = state.detailCollection
                    ?.takeIf { state.detailCollectionId == id }
                when (detail) {
                    is LoadState.Content -> {
                        val data = detail.data
                        CollectionDetailScreen(
                            state = state,
                            collection = data.collection,
                            items = data.wallpapers,
                            onBack = {
                                viewModel.clearCollectionDetail()
                                navController.popBackStack()
                            },
                            onOpenWallpaper = { wallpaper ->
                                navController.navigate("detail/${wallpaper.id}") {
                                    launchSingleTop = true
                                }
                            },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onRemove = { wallpaperId ->
                                viewModel.removeFromCollection(data.collection.id, wallpaperId)
                            },
                            onDelete = { viewModel.deleteCollection(data.collection.id) },
                            onExplore = {
                                viewModel.clearCollectionDetail()
                                navController.navigateTopLevel("home")
                            },
                            onDownloadMany = viewModel::downloadMany,
                            onShareMany = viewModel::shareMany,
                            onRemoveMany = { ids ->
                                viewModel.setCollectionWallpapers(
                                    data.collection.id,
                                    data.collection.wallpaperIds.filterNot { it in ids },
                                )
                            },
                        )
                    }

                    is LoadState.Error -> RouteError(
                        message = detail.message,
                        onRetry = { viewModel.loadCollectionDetail(id, force = true) },
                        onBack = { navController.popBackStack() },
                    )

                    else -> RouteLoading()
                }
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    updateViewModel = updateViewModel,
                    onBack = { navController.popBackStack() },
                    onThemeModeChange = viewModel::setThemeMode,
                    onLiteModeChange = viewModel::setLiteMode,
                    onOpenEditorOnApplyChange = viewModel::setOpenEditorOnApply,
                    onRefreshCacheSize = viewModel::refreshCacheSize,
                    onClearCache = viewModel::clearCache,
                    onOpenAbout = { navController.navigate("about") },
                    onResetAppearance = viewModel::resetAppearance,
                    onSignOut = {
                        viewModel.signOut()
                        navController.popBackStack()
                    },
                )
            }
            composable("about") {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable("decoy") {
                if (viewModel.decoyVisited) {
                    DecoyScreen(
                        onBack = { navController.popBackStack() },
                        onOpenStudio = {
                            navController.navigate("studio") {
                                launchSingleTop = true
                            }
                        },
                    )
                } else {
                    // Restaurada desde estado guardado de un proceso anterior:
                    // el señuelo nunca se restaura, siempre vuelve al Inicio.
                    LaunchedEffect(Unit) {
                        navController.popBackStack("home", inclusive = false)
                    }
                }
            }
            composable("studio") {
                // Al restaurar la app el Estudio se conserva solo con sesión
                // iniciada o si se entró por el señuelo durante esta corrida
                // del proceso. Sin eso, se vuelve al Inicio.
                val enteredThroughDecoy = viewModel.decoyVisited
                if (!state.isDemo && state.session == null && !enteredThroughDecoy) {
                    LaunchedEffect(Unit) {
                        navController.popBackStack("home", inclusive = false)
                    }
                } else {
                    StudioScreen(
                        state = state,
                        onSignIn = viewModel::signIn,
                        onSignOut = viewModel::signOut,
                        onUpload = viewModel::uploadWallpaper,
                        onRefresh = viewModel::refresh,
                        onSelectUploadUri = { uri -> viewModel.setSelectedUploadUri(uri?.toString()) },
                        onBack = { navController.popBackStack() },
                        onLoadStudio = viewModel::loadStudio,
                        onCreateCategory = viewModel::createCategory,
                        onUpdateCategory = viewModel::updateCategory,
                        onMoveCategory = viewModel::moveCategory,
                        onDeleteCategory = viewModel::deleteCategory,
                        onUpdateWallpaper = viewModel::updateWallpaper,
                        onUpdateWallpaperNow = { wallpaperId, title, category, categoryId, author, tags, featured ->
                            viewModel.updateWallpaperNow(
                                wallpaperId = wallpaperId,
                                title = title,
                                categoryInput = category,
                                categoryId = categoryId,
                                author = author,
                                tags = tags,
                                featured = featured,
                            )
                            Unit
                        },
                        onBulkEditTags = viewModel::bulkEditTags,
                        onClearBulkResult = viewModel::clearBulkResult,
                        onReplaceImage = viewModel::replaceWallpaperImage,
                        onArchiveWallpaper = viewModel::archiveWallpaper,
                        onDeleteWallpaper = viewModel::deleteWallpaper,
                        onRestoreWallpapers = viewModel::restoreWallpapers,
                        onPurgeWallpaper = viewModel::purgeWallpaper,
                        onEmptyTrash = viewModel::emptyTrash,
                        onBulkSetFeatured = viewModel::bulkSetFeatured,
                        onBulkSetArchived = viewModel::bulkSetArchived,
                        onBulkDeleteWallpapers = viewModel::bulkDeleteWallpapers,
                        onBulkSetCategory = viewModel::bulkSetCategory,
                        onSaveAppSettings = viewModel::saveAppSettings,
                        onMoveFeatured = viewModel::moveFeatured,
                        onCreateCollection = { name, description, isPublic ->
                            viewModel.createCollection(name, description, isPublic, onDevice = false)
                        },
                        onUpdateCollection = viewModel::updateCollection,
                        onDeleteCollection = viewModel::deleteCollection,
                        onSetCollectionWallpapers = viewModel::setCollectionWallpapers,
                        onReorderCollection = viewModel::reorderCollection,
                        onAddTag = viewModel::addTag,
                        onUpdateTagGroup = viewModel::updateTagGroup,
                        onClearTagRule = viewModel::clearTagRule,
                        onDeleteTag = viewModel::deleteTag,
                        onOpenCollection = { collection ->
                            navController.navigate("collection/${collection.id}") {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
            composable(
                route = "detail/{id}?random={random}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("random") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                LaunchedEffect(id) {
                    viewModel.loadWallpaperDetail(id)
                }
                val detail = state.detailWallpaper
                    ?.takeIf { state.detailWallpaperId == id }
                when (detail) {
                    is LoadState.Content -> {
                        val wallpaper = detail.data
                        val detailIndex = state.wallpapers.indexOfFirst { it.id == wallpaper.id }
                        val previous = state.wallpapers.getOrNull(detailIndex - 1)
                        val next = state.wallpapers.getOrNull(detailIndex + 1)
                        val detailDestinationId = entry.destination.id
                        DetailScreen(
                            wallpaper = wallpaper,
                            isFavorite = wallpaper.id in state.favorites,
                            collections = state.myCollections,
                            canManageCollections = true,
                            canChooseCollectionDestination = state.session != null,
                            collectionBusy = state.collectionBusy,
                            actionState = actionState,
                            applySuccessTick = state.applyTick,
                            downloadPrompt = state.downloadPrompt
                                ?.takeIf { it.wallpaperId == wallpaper.id },
                            alreadyDownloaded = wallpaper.id in state.downloadedIds,
                            offline = state.offline,
                            openEditorOnApply = state.openEditorOnApply,
                            onBack = {
                                viewModel.clearWallpaperDetail()
                                navController.popBackStack()
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(wallpaper.id) },
                            onDownload = { viewModel.download(wallpaper) },
                            onOpenEditor = {
                                navController.navigate("editor/${wallpaper.id}") {
                                    launchSingleTop = true
                                }
                            },
                            onSetWallpaper = { target -> viewModel.setWallpaper(wallpaper, target) },
                            onShare = { viewModel.share(wallpaper) },
                            onCancelAction = { viewModel.cancelAction() },
                            onOpenFile = { uri ->
                                if (!wallpaperFiles.open(uri)) {
                                    viewModel.notifyFileOpenFailed()
                                }
                            },
                            onDownloadAgain = { id -> viewModel.downloadAgain(id) },
                            onDismissDownloadPrompt = { viewModel.dismissDownloadPrompt() },
                            onAddToCollection = { collectionId ->
                                viewModel.addToCollection(collectionId, wallpaper)
                            },
                            onCreateCollection = { name, description, isPublic, onDevice ->
                                viewModel.createCollection(
                                    name,
                                    description,
                                    isPublic,
                                    onDevice,
                                )
                            },
                            onPrevious = previous?.let { target ->
                                {
                                    navController.navigate("detail/${target.id}") {
                                        launchSingleTop = true
                                        popUpTo(detailDestinationId) { inclusive = true }
                                    }
                                }
                            },
                            onNext = next?.let { target ->
                                {
                                    navController.navigate("detail/${target.id}") {
                                        launchSingleTop = true
                                        popUpTo(detailDestinationId) { inclusive = true }
                                    }
                                }
                            },
                        )
                    }

                    is LoadState.Error -> RouteError(
                        message = detail.message,
                        onRetry = { viewModel.loadWallpaperDetail(id, force = true) },
                        onBack = { navController.popBackStack() },
                    )

                    else -> RouteLoading()
                }
            }
            composable(
                route = "editor/{id}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                LaunchedEffect(id) {
                    viewModel.loadWallpaperDetail(id)
                }
                val detail = state.detailWallpaper
                    ?.takeIf { state.detailWallpaperId == id }
                when (detail) {
                    is LoadState.Content -> {
                        WallpaperEditorScreen(
                            wallpaper = detail.data,
                            onBack = { navController.popBackStack() },
                            onApplied = { target ->
                                viewModel.onWallpaperApplied(target)
                                navController.popBackStack()
                            },
                        )
                    }

                    is LoadState.Error -> RouteError(
                        message = detail.message,
                        onRetry = { viewModel.loadWallpaperDetail(id, force = true) },
                        onBack = { navController.popBackStack() },
                    )

                    else -> RouteLoading()
                }
            }
        }
            DebugFpsOverlay(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun RouteLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun RouteError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ErrorState(message = message, onRetry = onRetry)
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun TopLevelScaffold(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                tabs.forEach { tab ->
                    val selected = selectedRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                onNavigate(tab.route)
                            }
                        },
                        icon = {
                            Icon(tab.icon, contentDescription = null)
                        },
                        label = {
                            Text(stringResource(tab.labelRes))
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            content()
        }
    }
}

/** Pantallas que se abren desde una pestaña y vuelven con gesto de atrás. */
private fun isDetailRoute(route: String?): Boolean = route?.startsWith("detail") == true

/** Ajustes, colecciones, editor y Estudio: aparecen con fade + escala. */
private fun isSecondaryRoute(route: String?): Boolean =
    route == "settings" ||
        route == "decoy" ||
        route == "studio" ||
        route?.startsWith("editor/") == true ||
        route?.startsWith("collection/") == true

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
