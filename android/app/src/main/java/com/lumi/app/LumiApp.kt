package com.lumi.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lumi.app.ui.screen.ForgotPasswordScreen
import com.lumi.app.ui.screen.HomeScreen
import com.lumi.app.ui.screen.LoginScreen
import com.lumi.app.ui.screen.NotificationSettingsScreen
import com.lumi.app.ui.screen.NotificationsScreen
import com.lumi.app.ui.screen.RegisterScreen
import com.lumi.app.ui.screen.SettingsScreen
import com.lumi.app.ui.screen.SplashScreen
import com.lumi.app.ui.screen.SubscriptionScreen
import com.lumi.app.ui.screen.MainScaffold

/**
 * Root composable for the Lumi application.
 * Bottom-nav shell + navigation graph now live in MainScaffold (which
 * also hosts the persistent mini player above the nav bar).
 */
@Composable
fun LumiApp() {
    val navController = rememberNavController()

    // Після успішного логіну очищаємо стек авторизації,
    // щоб кнопка «назад» не повертала на екрани входу.
    val navigateToHome: () -> Unit = {
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = navigateToHome
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
                onLoginSuccess = navigateToHome
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = navigateToHome
            )
        }
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenNotifications = {
                    navController.navigate(Screen.Notifications.route)
                },
                onOpenNotificationSettings = {
                    navController.navigate(Screen.NotificationSettings.route)
                },
                onOpenSubscription = {
                    navController.navigate(Screen.Subscription.route)
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(Screen.Notifications.route) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.NotificationSettings.route) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Subscription.route) {
            SubscriptionScreen(
                onBack = { navController.popBackStack() },
                // Після оплати вертаємось на попередній екран, де продовжиться дія з пейвола.
                onUpgraded = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
    MainScaffold()
}

/**
 * Navigation routes for the application.
 */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object ForgotPassword : Screen("forgot-password")
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
    data object Player : Screen("player/{trackId}") {
        fun createRoute(trackId: String) = "player/$trackId"
    }
    data object Profile : Screen("profile")
    data object Notifications : Screen("notifications")
    data object NotificationSettings : Screen("notification-settings")
    data object Subscription : Screen("subscription")
    data object Settings : Screen("settings")
    data object Playlist : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    data object Album : Screen("album/{albumId}") {
        fun createRoute(albumId: String) = "album/$albumId"
    }
    data object Artist : Screen("artist/{artistId}") {
        fun createRoute(artistId: String) = "artist/$artistId"
    }
    data object Statistics : Screen("statistics")
    data object Genre : Screen("genre/{genreName}") {
        /** Назву жанру кодуємо, бо вона може містити "/" або "&" (наприклад "R&B/Soul"). */
        fun createRoute(genreName: String) = "genre/${android.net.Uri.encode(genreName)}"
    }
    data object Settings : Screen("settings")
    data object Subscription : Screen("subscription")
    data object SubscriptionSuccess : Screen("subscription_success")
    data object SupportChat : Screen("support_chat")
}
