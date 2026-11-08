package com.dannx.walls.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.annotation.StringRes
import com.dannx.walls.LoadState
import com.dannx.walls.MainViewModel
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.ui.components.DebugFpsOverlay
import com.dannx.walls.ui.editor.WallpaperEditorScreen
import com.dannx.walls.ui.screens.CollectionDetailScreen
import com.dannx.walls.ui.screens.CollectionsScreen
import com.dannx.walls.ui.screens.DetailScreen
import com.dannx.walls.ui.screens.FavoritesScreen
import com.dannx.walls.ui.screens.HomeScreen
import com.dannx.walls.ui.screens.OnboardingScreen
import com.dannx.walls.ui.screens.SettingsScreen
import com.dannx.walls.ui.screens.StudioScreen
import com.dannx.walls.ui.theme.LocalLiteMode
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
            if (result == SnackbarResult.ActionPerformed && action != null) {
                runCatching { wallpaperFiles.open(action.uri) }
            }
            viewModel.clearMessage()
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
                    if (liteMode) {
                        fadeIn(tween(120))
                    } else {
                        fadeIn(tween(240)) +
                            slideInHorizontally(
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                            ) { it / 14 } +
                            scaleIn(
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                                initialScale = 0.985f,
                            )
                    }
                },
                exitTransition = {
                    if (liteMode) {
                        fadeOut(tween(90))
                    } else {
                        fadeOut(tween(160)) +
                            scaleOut(
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                targetScale = 0.99f,
                            )
                    }
                },
                popEnterTransition = {
                    if (liteMode) {
                        fadeIn(tween(120))
                    } else {
                        // La pantalla de atrás ya está quieta y visible; la
                        // tarjeta que sale es la que hace el movimiento.
                        EnterTransition.None
                    }
                },
                popExitTransition = {
                    if (liteMode) {
                        fadeOut(tween(90))
                    } else {
                        scaleOut(
                            animationSpec = tween(220, easing = LinearEasing),
                            targetScale = 0.9f,
                            transformOrigin = TransformOrigin.Center,
                        )
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
                    if (initialState.destination.route in topTabRoutes) {
                        // Salir de una pestaña hacia otra no escala la barra.
                        ExitTransition.None
                    } else if (liteMode) {
                        fadeOut(tween(90))
                    } else {
                        scaleOut(
                            animationSpec = tween(220, easing = LinearEasing),
                            targetScale = 0.9f,
                            transformOrigin = TransformOrigin.Center,
                        )
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
                            viewModel.setTag(null)
                        },
                        onQueryChange = viewModel::setQuery,
                        onCategoryChange = viewModel::setCategory,
                        onTagChange = viewModel::setTag,
                        onRefresh = viewModel::refresh,
                        onOpenWallpaper = { wallpaper ->
                            navController.navigate("detail/${wallpaper.id}") {
                                launchSingleTop = true
                            }
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onOpenStudio = { navController.navigateTopLevel("studio") },
                        onOpenSettings = { navController.navigate("settings") },
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
                        onCreateCollection = viewModel::createCollection,
                    )
                }
            }
            composable(
                route = "collection/{id}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id")
                val collection = state.collectionItems.firstOrNull { it.id == id }
                if (collection == null) {
                    if (state.collections is LoadState.Loading) {
                        RouteLoading()
                    } else {
                        LaunchedEffect(id) {
                            navController.popBackStack()
                        }
                    }
                } else {
                    CollectionDetailScreen(
                        state = state,
                        collection = collection,
                        onBack = { navController.popBackStack() },
                        onOpenWallpaper = { wallpaper ->
                            navController.navigate("detail/${wallpaper.id}") {
                                launchSingleTop = true
                            }
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onRemove = { wallpaperId ->
                            viewModel.removeFromCollection(collection.id, wallpaperId)
                        },
                        onDelete = { viewModel.deleteCollection(collection.id) },
                    )
                }
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onThemeModeChange = viewModel::setThemeMode,
                    onLiteModeChange = viewModel::setLiteMode,
                    onOpenEditorOnApplyChange = viewModel::setOpenEditorOnApply,
                    onClearCache = viewModel::clearCache,
                    onResetAppearance = viewModel::resetAppearance,
                    onSignOut = {
                        viewModel.signOut()
                        navController.popBackStack()
                    },
                )
            }
            composable("studio") {
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
                    onDeleteCategory = viewModel::deleteCategory,
                    onUpdateWallpaper = viewModel::updateWallpaper,
                    onReplaceImage = viewModel::replaceWallpaperImage,
                    onArchiveWallpaper = viewModel::archiveWallpaper,
                    onDeleteWallpaper = viewModel::deleteWallpaper,
                    onCreateCollection = viewModel::createCollection,
                    onUpdateCollection = viewModel::updateCollection,
                    onDeleteCollection = viewModel::deleteCollection,
                    onSetCollectionWallpapers = viewModel::setCollectionWallpapers,
                    onReorderCollection = viewModel::reorderCollection,
                    onAddTag = viewModel::addTag,
                    onRenameTag = viewModel::renameTag,
                    onDeleteTag = viewModel::deleteTag,
                    onOpenCollection = { collection ->
                        navController.navigate("collection/${collection.id}") {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = "detail/{id}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id")
                val wallpaper = state.wallpapers.firstOrNull { it.id == id }
                if (wallpaper == null) {
                    if (state.feed is LoadState.Loading) {
                        RouteLoading()
                    } else {
                        LaunchedEffect(id) {
                            navController.popBackStack()
                        }
                    }
                } else {
                    DetailScreen(
                        wallpaper = wallpaper,
                        isFavorite = wallpaper.id in state.favorites,
                        collections = state.myCollections,
                        canManageCollections = state.isDemo || state.session != null,
                        collectionBusy = state.collectionBusy,
                        actionState = actionState,
                        downloadPrompt = state.downloadPrompt
                            ?.takeIf { it.wallpaperId == wallpaper.id },
                        alreadyDownloaded = wallpaper.id in state.downloadedIds,
                        offline = state.offline,
                        openEditorOnApply = state.openEditorOnApply,
                        onBack = { navController.popBackStack() },
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
                        onOpenFile = { uri -> runCatching { wallpaperFiles.open(uri) } },
                        onDownloadAgain = { id -> viewModel.downloadAgain(id) },
                        onDismissDownloadPrompt = { viewModel.dismissDownloadPrompt() },
                        onAddToCollection = { collectionId ->
                            viewModel.addToCollection(collectionId, wallpaper)
                        },
                        onCreateCollection = { name, description, isPublic ->
                            viewModel.createCollection(name, description, isPublic)
                        },
                    )
                }
            }
            composable(
                route = "editor/{id}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id")
                val wallpaper = state.wallpapers.firstOrNull { it.id == id }
                if (wallpaper == null) {
                    if (state.feed is LoadState.Loading) {
                        RouteLoading()
                    } else {
                        LaunchedEffect(id) {
                            navController.popBackStack()
                        }
                    }
                } else {
                    WallpaperEditorScreen(
                        wallpaper = wallpaper,
                        onBack = { navController.popBackStack() },
                        onApplied = { target ->
                            viewModel.onWallpaperApplied(target)
                            navController.popBackStack()
                        },
                    )
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

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
