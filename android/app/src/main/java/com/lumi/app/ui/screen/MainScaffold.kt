package com.lumi.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lumi.app.Screen
import com.lumi.app.ui.component.MiniPlayerBar

private data class BottomTab(val screen: Screen, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(Screen.Home, "Головна", Icons.Filled.Home),
    BottomTab(Screen.Search, "Пошук", Icons.Filled.Search),
    BottomTab(Screen.Library, "Бібліотека", Icons.Filled.LibraryMusic)
)

@Composable
fun MainScaffold(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayerBar(onOpenPlayer = { /* TODO: full-screen player, out of this backlog's scope */ })
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute?.hierarchy?.any { it.route == tab.screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) })
            }
            composable(Screen.Search.route) { SearchPlaceholderScreen() }
            composable(Screen.Library.route) { LibraryPlaceholderScreen() }
            composable(Screen.Playlist.route) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("playlistId").orEmpty()
                PlaylistDetailScreen(
                    playlistId = id,
                    onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) }
                )
            }
            composable(Screen.Album.route) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("albumId").orEmpty()
                AlbumDetailScreen(
                    albumId = id,
                    onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) }
                )
            }
            composable(Screen.Artist.route) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("artistId").orEmpty()
                ArtistScreen(
                    artistId = id,
                    onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) }
                )
            }
        }
    }
}

// TODO: full Search screen (categories, tabbed results — see design refs) is out of
// this backlog's explicit scope (only the bottom-nav tab was required for Epic 1).
@Composable
private fun SearchPlaceholderScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Пошук — TODO") }
}

// TODO: Library (playlists/liked/albums tabs, "create playlist" — see design refs)
// is out of this backlog's explicit scope.
@Composable
private fun LibraryPlaceholderScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Бібліотека — TODO") }
}
