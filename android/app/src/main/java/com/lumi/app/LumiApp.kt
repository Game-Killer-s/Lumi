package com.lumi.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lumi.app.ui.screen.HomeScreen

/**
 * Root composable for the Lumi application.
 * Sets up navigation graph and screen routing.
 */
@Composable
fun LumiApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
        // Future screens will be added here:
        // composable(Screen.Search.route) { SearchScreen() }
        // composable(Screen.Library.route) { LibraryScreen() }
        // composable(Screen.Player.route) { PlayerScreen() }
        // composable(Screen.Login.route) { LoginScreen() }
        // composable(Screen.Register.route) { RegisterScreen() }
    }
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
    data object Artist : Screen("artist/{artistId}") {
        fun createRoute(artistId: String) = "artist/$artistId"
    }
}
