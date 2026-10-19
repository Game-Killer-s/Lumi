package com.lumi.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lumi.app.data.repository.AuthRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.network.TokenManager
import com.lumi.app.ui.screen.MainScaffold
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Root composable for the Lumi application.
 * Bottom-nav shell + navigation graph live in MainScaffold (which
 * also hosts the persistent mini player above the nav bar).
 */
@Composable
fun LumiApp() {
    var sessionReady by remember { mutableStateOf(false) }

    /*
     * Сесія з DataStore відновлюється ДО першого запиту профілю.
     * Раніше TokenManager.init() викликався лише на Splash-екрані, якого
     * в навігації немає, тому accessToken у пам'яті лишався null:
     * Authorization-заголовок не додавався і /users/profile віддавав 401.
     */
    LaunchedEffect(Unit) {
        TokenManager.init()

        val refreshToken = TokenManager.getRefreshToken()
        if (refreshToken != null) {
            // Старт не блокуємо довше 2.5 с: якщо бекенд недоступний,
            // екрани все одно мають показати UI (з помилкою і кнопкою «Повторити»).
            withTimeoutOrNull(2_500) {
                when (val result = AuthRepository().refreshToken(refreshToken)) {
                    is ApiResult.Success -> TokenManager.saveSession(result.data, remember = true)
                    is ApiResult.Error -> if (result.error.statusCode == 401) TokenManager.clearSession()
                }
            }
        }

        sessionReady = true
    }

    if (sessionReady) {
        MainScaffold()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
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
    data object SubscriptionCheckout : Screen("subscription_checkout")
    data object SubscriptionSuccess : Screen("subscription_success")
    data object SupportChat : Screen("support_chat")
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
}
