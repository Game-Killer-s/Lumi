package com.lumi.app.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
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
import com.lumi.app.player.PlayerController
import com.lumi.app.ui.component.MiniPlayerBar

private data class BottomTab(val screen: Screen, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(Screen.Home, "Головна", Icons.Filled.Home),
    BottomTab(Screen.Search, "Пошук", Icons.Filled.Search),
    BottomTab(Screen.Library, "Бібліотека", Icons.Filled.LibraryMusic),
    BottomTab(Screen.Profile, "Профіль", Icons.Filled.Person)
)

@Composable
fun MainScaffold(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    // Повноекранний плеєр займає весь екран: мініплеєр і нижня навігація там зайві.
    val isPlayerScreen = currentRoute?.route == Screen.Player.route

    Scaffold(
        bottomBar = {
            if (!isPlayerScreen) Column {
                MiniPlayerBar(onOpenPlayer = {
                    PlayerController.state.value.currentTrack?.let { track ->
                        navController.navigate(Screen.Player.createRoute(track.id)) { launchSingleTop = true }
                    }
                })
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
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) })
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) },
                    onOpenAlbum = { albumId -> navController.navigate(Screen.Album.createRoute(albumId)) },
                    onOpenProfile = {}
                )
            }
            composable(Screen.Library.route) {
                LibraryScreen(
                    onOpenPlaylist = { playlistId -> navController.navigate(Screen.Playlist.createRoute(playlistId)) },
                    onOpenAlbum = { albumId -> navController.navigate(Screen.Album.createRoute(albumId)) },
                    onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) },
                    onOpenProfile = {}
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = { navController.navigate(Screen.Login.route) { popUpTo(0) } },
                    onOpenStatistics = { navController.navigate(Screen.Statistics.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenSubscription = { navController.navigate(Screen.Subscription.route) },
                    onOpenSupportChat = { navController.navigate(Screen.SupportChat.route) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Subscription.route) {
                SubscriptionScreen(
                    onBack = { navController.popBackStack() },
                    onSubscribe = { navController.navigate(Screen.SubscriptionSuccess.route) }
                )
            }
            composable(Screen.SubscriptionSuccess.route) {
                SubscriptionSuccessScreen(onDone = { navController.popBackStack() })
            }
            composable(Screen.SupportChat.route) {
                SupportChatScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Statistics.route) {
                StatisticsScreen(
                    onOpenTrack = { tracks, index ->
                        // Топ-трек: запускаємо його (черга = весь список топу) і відкриваємо плеєр.
                        PlayerController.playQueue(tracks, index)
                        tracks.getOrNull(index)?.let { track ->
                            navController.navigate(Screen.Player.createRoute(track.id)) { launchSingleTop = true }
                        }
                    },
                    onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) },
                    onOpenGenre = { genre -> navController.navigate(Screen.Genre.createRoute(genre)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Player.route) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Genre.route) { backStackEntry ->
                val genre = backStackEntry.arguments?.getString("genreName").orEmpty()
                GenreResultsScreen(
                    genre = genre,
                    onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) }
                )
            }
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
