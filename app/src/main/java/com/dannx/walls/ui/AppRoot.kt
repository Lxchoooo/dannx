package com.dannx.walls.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.ui.components.BackdropGlassState
import com.dannx.walls.ui.components.DebugFpsOverlay
import com.dannx.walls.ui.components.DockItem
import com.dannx.walls.ui.components.ErrorState
import com.dannx.walls.ui.components.FloatingDock
import com.dannx.walls.ui.components.LocalBackdropGlass
import com.dannx.walls.ui.components.LocalLastAccent
import com.dannx.walls.ui.components.LocalSharedTransitionScope
import com.dannx.walls.ui.components.ProvideSharedVisibilityScope
import com.dannx.walls.ui.components.backdropGlass
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
import com.dannx.walls.ui.theme.EmphasizedEasing
import com.dannx.walls.ui.theme.LocalReduceMotion
import com.dannx.walls.ui.theme.MotionDuration
import com.dannx.walls.ui.update.UpdateState
import com.dannx.walls.ui.update.UpdateViewModel
import com.dannx.walls.util.WallpaperFiles

private data class TopLevelTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val tabs = listOf(
    TopLevelTab(
        "home",
        R.string.home_section_explore,
        Icons.Outlined.Explore,
        Icons.Rounded.Explore,
    ),
    TopLevelTab(
        "collections",
        R.string.collections_title,
        Icons.Outlined.CollectionsBookmark,
        Icons.Rounded.CollectionsBookmark,
    ),
    TopLevelTab(
        "favorites",
        R.string.favorites_title,
        Icons.Rounded.FavoriteBorder,
        Icons.Rounded.Favorite,
    ),
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

@OptIn(ExperimentalSharedTransitionApi::class)
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
    val reduceMotion = LocalReduceMotion.current
    // Vidrio: el fondo se difumina detrás de las hojas y los menús que se
    // abran por encima. Vive acá para que lo alcance todo el NavHost.
    val backdropGlass = remember { BackdropGlassState() }
    // Última pestaña visitada: da dirección al contenido de la siguiente. Se
    // actualiza después de componer para no pisar la entrada en curso.
    var previousTabRoute by remember { mutableStateOf(currentRoute) }
    LaunchedEffect(currentRoute) {
        if (currentRoute in topTabRoutes) previousTabRoute = currentRoute
    }
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
                    .padding(bottom = if (showBar) 96.dp else 0.dp),
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .backdropGlass(
                    state = backdropGlass,
                    enabled = !state.liteMode,
                    reduceMotion = reduceMotion,
                ),
        ) {
            SharedTransitionLayout(Modifier.fillMaxSize()) {
                // El último acento extraído vive acá, por encima del NavHost:
                // así, al pasar de un fondo a otro, la pantalla nueva arranca
                // desde el color anterior y lo transforma en el suyo.
                val lastAccent = remember { mutableStateOf<Color?>(null) }
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this,
                    LocalLastAccent provides lastAccent,
                    LocalBackdropGlass provides backdropGlass,
                ) {
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
                                reduceMotion -> fadeIn(tween(MotionDuration.Fast))
                                randomDetail -> fadeIn(tween(340))
                                detailToDetail -> fadeIn(tween(300))
                                isDetailRoute(targetRoute) -> {
                                    // La foto viaja sola de la tarjeta al detalle;
                                    // el resto de la pantalla solo aparece.
                                    fadeIn(tween(MotionDuration.Medium))
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
                                reduceMotion -> fadeOut(tween(MotionDuration.Fast))
                                // Al pasar de un fondo a otro el anterior se
                                // aleja mientras el nuevo se asienta, para que
                                // la foto se transforme y no se funda en seco.
                                detailToDetail -> {
                                    fadeOut(tween(300)) +
                                        scaleOut(
                                            animationSpec = tween(300, easing = EmphasizedEasing),
                                            targetScale = 1.04f,
                                        )
                                }
                                randomDetail -> fadeOut(tween(300))
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
                                reduceMotion -> fadeIn(tween(MotionDuration.Fast))
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
                                reduceMotion -> fadeOut(tween(MotionDuration.Fast))
                                isDetailRoute(leaving) -> {
                                    // La foto vuelve a la tarjeta; el detalle se apaga.
                                    fadeOut(tween(MotionDuration.Medium))
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
                            } else if (reduceMotion) {
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

                                reduceMotion -> fadeOut(tween(MotionDuration.Fast))
                                isDetailRoute(leaving) -> {
                                    fadeOut(tween(MotionDuration.Medium))
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
                        ProvideSharedVisibilityScope {
                            TopLevelScaffold(
                                selectedRoute = "home",
                                enterFrom = tabDirection(previousTabRoute, "home"),
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
                    }
                    composable(
                        route = "favorites",
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None },
                    ) {
                        ProvideSharedVisibilityScope {
                            TopLevelScaffold(
                                selectedRoute = "favorites",
                                enterFrom = tabDirection(previousTabRoute, "favorites"),
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
                            enterFrom = tabDirection(previousTabRoute, "collections"),
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
                        ProvideSharedVisibilityScope {
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
                    }
                    composable("settings") {
                        SettingsScreen(
                            state = state,
                            updateViewModel = updateViewModel,
                            onBack = { navController.popBackStack() },
                            onThemeModeChange = viewModel::setThemeMode,
                            onLiteModeChange = viewModel::setLiteMode,
                            onLiveMotionChange = viewModel::setLiveMotion,
                            onOpenEditorOnApplyChange = viewModel::setOpenEditorOnApply,
                            onColorSeedChange = viewModel::setColorSeed,
                            onPaletteStyleChange = viewModel::setPaletteStyle,
                            onContrastLevelChange = viewModel::setContrastLevel,
                            onAmoledChange = viewModel::setAmoled,
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
                        ProvideSharedVisibilityScope {
                            val id = entry.arguments?.getString("id").orEmpty()
                            LaunchedEffect(id) {
                                viewModel.loadWallpaperDetail(id)
                            }
                            val resolved = state.detailWallpaper
                                ?.takeIf { state.detailWallpaperId == id }
                            // La pantalla que sale conserva su foto mientras la
                            // nueva resuelve: si no, la anterior se reemplaza por
                            // el indicador de carga a mitad de la transición y el
                            // cambio de fondo se corta en seco.
                            var latched by remember(id) {
                                mutableStateOf<LoadState.Content<Wallpaper>?>(null)
                            }
                            LaunchedEffect(resolved) {
                                if (resolved is LoadState.Content) latched = resolved
                            }
                            val detail = resolved ?: latched
                            when (detail) {
                                is LoadState.Content -> {
                                    val wallpaper = detail.data
                                    LaunchedEffect(wallpaper.id) {
                                        viewModel.loadRelatedWallpapers(wallpaper)
                                    }
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
                                        liveReadyTick = state.liveReadyTick,
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
                                        onSetLiveWallpaper = { viewModel.prepareLiveWallpaper(wallpaper) },
                                        onLiveWallpaperApplied = viewModel::onLiveWallpaperApplied,
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
                                        related = state.detailRelated,
                                        onOpenRelated = { target ->
                                            navController.navigate("detail/${target.id}") {
                                                launchSingleTop = true
                                                popUpTo(detailDestinationId) { inclusive = true }
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
                                    onAppliedLive = {
                                        viewModel.onLiveWallpaperApplied()
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
                }
            }
            DebugFpsOverlay(Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RouteLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
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
    enterFrom: Int = 0,
    content: @Composable () -> Unit,
) {
    val labels = tabs.map { stringResource(it.labelRes) }
    val dockItems = remember(labels) {
        tabs.mapIndexed { index, tab ->
            DockItem(
                route = tab.route,
                icon = tab.icon,
                selectedIcon = tab.selectedIcon,
                label = labels[index],
            )
        }
    }
    val reduceMotion = LocalReduceMotion.current
    // Entrada direccional del contenido: la barra se queda quieta y el cuerpo
    // se desliza desde el lado de la pestaña que se dejó atrás.
    val appear = remember { Animatable(if (enterFrom == 0 || reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            appear.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = MotionDuration.Medium,
                    easing = EmphasizedEasing,
                ),
            )
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // La píldora flota sobre el fondo: el Scaffold reserva su altura
            // y el contenido no queda tapado por la barra.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                FloatingDock(
                    items = dockItems,
                    selectedRoute = selectedRoute,
                    onNavigate = { route ->
                        if (route != selectedRoute) onNavigate(route)
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val remaining = 1f - appear.value
                        translationX = enterFrom * size.width * 0.08f * remaining
                        alpha = 0.82f + 0.18f * appear.value
                    },
            ) {
                content()
            }
        }
    }
}

/** Pantallas que se abren desde una pestaña y vuelven con gesto de atrás. */
private fun isDetailRoute(route: String?): Boolean = route?.startsWith("detail") == true

/** Posición de la pestaña en la barra, o -1 si la ruta no es una pestaña. */
private fun tabOrder(route: String?): Int = tabs.indexOfFirst { it.route == route }

/**
 * Sentido del cambio de pestaña: 1 cuando la nueva está a la derecha de la
 * anterior y -1 al revés. 0 para el primer arranque o cuando no hay cambio.
 */
private fun tabDirection(from: String?, to: String?): Int {
    val start = tabOrder(from)
    val end = tabOrder(to)
    return when {
        start < 0 || end < 0 || start == end -> 0
        end > start -> 1
        else -> -1
    }
}

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
