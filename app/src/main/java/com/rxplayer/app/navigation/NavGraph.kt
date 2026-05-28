package com.rxplayer.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import com.rxplayer.app.ui.screens.FavoritesScreen
import com.rxplayer.app.ui.screens.HistoryScreen
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
    BottomNavItem("收藏", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, Route.Favorites.route),
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
                    onVideoClick = { videoPath ->
                        navController.navigate(Route.Player.createRoute(videoPath))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Route.Favorites.route) {
                FavoritesScreen(
                    onVideoClick = { videoPath ->
                        navController.navigate(Route.Player.createRoute(videoPath))
                    }
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
                arguments = listOf(navArgument("videoPath") { type = NavType.StringType })
            ) { backStackEntry ->
                val videoPath = Route.Player.decodePath(backStackEntry.arguments?.getString("videoPath") ?: "")
                PlayerScreen(
                    videoPath = videoPath,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
