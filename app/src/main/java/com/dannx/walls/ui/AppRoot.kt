package com.dannx.walls.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FavoriteBorder
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.dannx.walls.MainViewModel
import com.dannx.walls.ui.components.DebugFpsOverlay
import com.dannx.walls.ui.screens.CollectionDetailScreen
import com.dannx.walls.ui.screens.CollectionsScreen
import com.dannx.walls.ui.screens.DetailScreen
import com.dannx.walls.ui.screens.FavoritesScreen
import com.dannx.walls.ui.screens.HomeScreen
import com.dannx.walls.ui.screens.StudioScreen
import com.dannx.walls.util.WallpaperFiles

private data class TopLevelTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TopLevelTab("home", "Explorar", Icons.Rounded.Explore),
    TopLevelTab("collections", "Colecciones", Icons.Rounded.CollectionsBookmark),
    TopLevelTab("favorites", "Favoritos", Icons.Rounded.FavoriteBorder),
    TopLevelTab("studio", "Estudio", Icons.Rounded.CloudUpload),
)

@Composable
fun AppRoot(viewModel: MainViewModel, startTab: String = "home") {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val context = LocalContext.current
    val wallpaperFiles = remember(context) { WallpaperFiles(context) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute in tabs.map { it.route }
    val startDestination = if (startTab in tabs.map { it.route }) startTab else "home"
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigateTopLevel(tab.route)
                                }
                            },
                            icon = {
                                Icon(tab.icon, contentDescription = null)
                            },
                            label = {
                                Text(tab.label)
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding),
            ) {
            composable("home") {
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
                )
            }
            composable("favorites") {
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
            composable("collections") {
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
            composable(
                route = "collection/{id}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id")
                val collection = state.collectionItems.firstOrNull { it.id == id }
                if (collection == null) {
                    LaunchedEffect(id) {
                        navController.popBackStack()
                    }
                    Box(Modifier.fillMaxSize())
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
            composable("studio") {
                StudioScreen(
                    state = state,
                    onSignIn = viewModel::signIn,
                    onSignOut = viewModel::signOut,
                    onUpload = viewModel::uploadWallpaper,
                    onRefresh = viewModel::refresh,
                    onSelectUploadUri = viewModel::setSelectedUploadUri,
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
                    LaunchedEffect(id) {
                        navController.popBackStack()
                    }
                    Box(Modifier.fillMaxSize())
                } else {
                    DetailScreen(
                        wallpaper = wallpaper,
                        isFavorite = wallpaper.id in state.favorites,
                        collections = state.myCollections,
                        canManageCollections = state.isDemo || state.session != null,
                        collectionBusy = state.collectionBusy,
                        actionState = actionState,
                        downloadPrompt = state.downloadPrompt,
                        alreadyDownloaded = wallpaper.id in state.downloadedIds,
                        offline = state.offline,
                        onBack = { navController.popBackStack() },
                        onToggleFavorite = { viewModel.toggleFavorite(wallpaper.id) },
                        onDownload = { viewModel.download(wallpaper) },
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
        }
            DebugFpsOverlay(Modifier.fillMaxSize())
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
