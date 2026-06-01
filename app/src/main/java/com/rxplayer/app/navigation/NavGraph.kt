package com.rxplayer.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rxplayer.app.ui.screens.HistoryScreen
import com.rxplayer.app.ui.screens.PlaylistDetailScreen
import com.rxplayer.app.ui.screens.PlaylistsScreen
import com.rxplayer.app.ui.screens.HomeScreen
import com.rxplayer.app.ui.screens.PlayerScreen
import com.rxplayer.app.ui.screens.SettingsScreen
import com.rxplayer.app.ui.screens.VideoListScreen

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("首页", Icons.Filled.Home, Icons.Outlined.Home, Route.FolderList.route),
    BottomNavItem("播放列表", Icons.AutoMirrored.Filled.PlaylistPlay, Icons.AutoMirrored.Outlined.PlaylistPlay, Route.Playlists.route),
    BottomNavItem("历史", Icons.Filled.History, Icons.Outlined.History, Route.History.route),
    BottomNavItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings, Route.Settings.route),
)

@Composable
fun RXPlayerNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.FolderList.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(0)) },
            exitTransition = { fadeOut(tween(0)) },
            popEnterTransition = { fadeIn(tween(0)) },
            popExitTransition = { fadeOut(tween(0)) }
        ) {
            composable(Route.FolderList.route) {
                HomeScreen(
                    onFolderClick = { folderPath ->
                        navController.navigate(Route.VideoList.createRoute(folderPath))
                    }
                )
            }
            composable(
                route = Route.VideoList.route,
                arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
            ) { backStackEntry ->
                val folderPath = Route.VideoList.decodePath(backStackEntry.arguments?.getString("folderPath") ?: "")
                VideoListScreen(
                    folderPath = folderPath,
                    onVideoClick = { videoPath, autoFullscreen, playbackMode ->
                        navController.navigate(Route.Player.createRoute(videoPath, if (autoFullscreen) 1 else 0, playbackMode, folderPath))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Route.Playlists.route) {
                PlaylistsScreen(
                    onPlaylistClick = { id, name ->
                        navController.navigate(Route.PlaylistDetail.createRoute(id, name))
                    }
                )
            }
            composable(
                route = Route.PlaylistDetail.route,
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.LongType },
                    navArgument("playlistName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                val playlistName = backStackEntry.arguments?.getString("playlistName")?.let {
                    String(android.util.Base64.decode(it, android.util.Base64.URL_SAFE))
                } ?: ""
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    playlistName = playlistName,
                    onVideoClick = { videoPath, autoFullscreen, playbackMode ->
                        navController.navigate(Route.Player.createRoute(videoPath, if (autoFullscreen) 1 else 0, playbackMode, playlistId = playlistId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Route.History.route) {
                HistoryScreen(
                    onVideoClick = { videoPath ->
                        navController.navigate(Route.Player.createRoute(videoPath))
                    }
                )
            }
            composable(Route.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = Route.Player.route,
                arguments = listOf(
                    navArgument("videoPath") { type = NavType.StringType },
                    navArgument("autoFullscreen") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("playbackMode") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("folderPath") { type = NavType.StringType; defaultValue = "" },
                    navArgument("playlistId") { type = NavType.LongType; defaultValue = 0L }
                )
            ) { backStackEntry ->
                val videoPath = Route.Player.decodePath(backStackEntry.arguments?.getString("videoPath") ?: "")
                val autoFullscreen = backStackEntry.arguments?.getInt("autoFullscreen") ?: 0
                val playbackMode = backStackEntry.arguments?.getInt("playbackMode") ?: 0
                val folderPath = Route.Player.decodeFolderPath(backStackEntry.arguments?.getString("folderPath") ?: "")
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                PlayerScreen(
                    videoPath = videoPath,
                    autoFullscreen = autoFullscreen == 1,
                    playbackMode = playbackMode,
                    folderPath = folderPath,
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
