package com.lumi.app

import androidx.compose.runtime.Composable
import com.lumi.app.ui.screen.MainScaffold

/**
 * Root composable for the Lumi application.
 * Bottom-nav shell + navigation graph now live in MainScaffold (which
 * also hosts the persistent mini player above the nav bar).
 */
@Composable
fun LumiApp() {
    MainScaffold()
}

/**
 * Navigation routes for the application.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
    data object Player : Screen("player/{trackId}") {
        fun createRoute(trackId: String) = "player/$trackId"
    }
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Profile : Screen("profile")
    data object Playlist : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    data object Album : Screen("album/{albumId}") {
        fun createRoute(albumId: String) = "album/$albumId"
    }
    data object Artist : Screen("artist/{artistId}") {
        fun createRoute(artistId: String) = "artist/$artistId"
    }
}
