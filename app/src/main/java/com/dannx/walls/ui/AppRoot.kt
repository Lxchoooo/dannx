package com.dannx.walls.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.dannx.walls.ui.screens.CollectionDetailScreen
import com.dannx.walls.ui.screens.CollectionsScreen
import com.dannx.walls.ui.screens.DetailScreen
import com.dannx.walls.ui.screens.FavoritesScreen
import com.dannx.walls.ui.screens.HomeScreen
import com.dannx.walls.ui.screens.StudioScreen

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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute in tabs.map { it.route }
    val startDestination = if (startTab in tabs.map { it.route }) startTab else "home"
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message, withDismissAction = true)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { height -> height },
                exit = slideOutVertically { height -> height },
            ) {
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
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    state = state,
                    onQueryChange = viewModel::setQuery,
                    onCategoryChange = viewModel::setCategory,
                    onTagChange = viewModel::setTag,
                    onRefresh = viewModel::refresh,
                    onOpenWallpaper = { wallpaper ->
                        navController.navigate("detail/${wallpaper.id}")
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            }
            composable("favorites") {
                FavoritesScreen(
                    state = state,
                    onOpenWallpaper = { wallpaper ->
                        navController.navigate("detail/${wallpaper.id}")
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onExplore = { navController.navigateTopLevel("home") },
                )
            }
            composable("collections") {
                CollectionsScreen(
                    state = state,
                    onOpenCollection = { collection ->
                        navController.navigate("collection/${collection.id}")
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
                val collection = state.collections.firstOrNull { it.id == id }
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
                            navController.navigate("detail/${wallpaper.id}")
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
                        onBack = { navController.popBackStack() },
                        onToggleFavorite = { viewModel.toggleFavorite(wallpaper.id) },
                        onDownload = { viewModel.download(wallpaper) },
                        onSetWallpaper = { viewModel.setWallpaper(wallpaper) },
                        onShare = { viewModel.share(wallpaper) },
                        onAddToCollection = { collectionId ->
                            viewModel.addToCollection(collectionId, wallpaper)
                        },
                        onCreateCollection = viewModel::createCollection,
                    )
                }
            }
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
