package com.lumi.app.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lumi.app.Screen
import com.lumi.app.R
import com.lumi.app.data.local.LibraryLocalStore
import com.lumi.app.data.repository.AuthRepository
import com.lumi.app.player.PlayerController
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.component.MiniPlayerBar
import com.lumi.app.ui.theme.LumiGlassColors
import com.lumi.app.ui.theme.lumiSecondaryGradient
import kotlinx.coroutines.launch

private data class BottomTab(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Screen.Home, R.string.nav_home, Icons.Filled.Home),
    BottomTab(Screen.Search, R.string.nav_search, Icons.Filled.Search),
    BottomTab(Screen.Library, R.string.nav_library, Icons.Filled.LibraryMusic),
    BottomTab(Screen.Profile, R.string.nav_profile, Icons.Filled.Person)
)

/** Екрани авторизації: мініплеєр і нижня навігація там зайві. */
private val authRoutes = setOf(
    Screen.Login.route,
    Screen.Register.route,
    Screen.ForgotPassword.route
)

@Composable
fun MainScaffold(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository() }

    // Вихід з акаунта: стираємо сесію і ведемо на екран входу.
    val onLoggedOut: () -> Unit = {
        scope.launch {
            authRepository.logout()
            LibraryLocalStore.activateForCurrentUser()
        }
        navController.navigate(Screen.Login.route) { popUpTo(0) }
    }

    // Повноекранні екрани: плеєр та авторизація.
    val isFullScreen = currentRoute?.route == Screen.Player.route ||
        currentRoute?.route in authRoutes

    Scaffold(
        containerColor = LumiGlassColors.Background,
        bottomBar = {
            if (!isFullScreen) Column {
                MiniPlayerBar(onOpenPlayer = {
                    PlayerController.state.value.currentTrack?.let { track ->
                        navController.navigate(Screen.Player.createRoute(track.id)) { launchSingleTop = true }
                    }
                })
                LumiBottomNavigation(
                    currentRoute = currentRoute,
                    onSelect = { tab ->
                        navController.navigate(tab.screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
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
                    onOpenHistory = { navController.navigate(Screen.History.route) { launchSingleTop = true } },
                    onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) },
                    onOpenAlbum = { albumId -> navController.navigate(Screen.Album.createRoute(albumId)) }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { artistId -> navController.navigate(Screen.Artist.createRoute(artistId)) }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = onLoggedOut,
                    onOpenStatistics = { navController.navigate(Screen.Statistics.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenSubscription = { navController.navigate(Screen.Subscription.route) },
                    onOpenSupportChat = { navController.navigate(Screen.SupportChat.route) }
                )
            }
            // Реальний екран налаштувань (мова, тема, очищення кешу, вихід),
            // а не заглушка з SettingsScreens.kt.
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLogout = onLoggedOut,
                    onOpenProfile = {
                        navController.navigate(Screen.Profile.route) { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.Subscription.route) {
                SubscriptionScreen(
                    onBack = { navController.popBackStack() },
                    onSubscribe = { navController.navigate(Screen.SubscriptionCheckout.route) }
                )
            }
            composable(Screen.SubscriptionCheckout.route) {
                SubscriptionCheckoutScreen(
                    onBack = { navController.popBackStack() },
                    onUpgraded = {
                        navController.navigate(Screen.SubscriptionSuccess.route) {
                            popUpTo(Screen.Subscription.route)
                        }
                    }
                )
            }
            composable(Screen.SubscriptionSuccess.route) {
                SubscriptionSuccessScreen(
                    onDone = {
                        navController.popBackStack(Screen.Subscription.route, inclusive = false)
                    }
                )
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
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { artistId ->
                        navController.navigate(Screen.Artist.createRoute(artistId))
                    },
                    onOpenAlbum = { albumId ->
                        navController.navigate(Screen.Album.createRoute(albumId))
                    }
                )
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
                    onBack = { navController.popBackStack() },
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
            // Екрани авторизації: без них «Вийти» падало на відсутньому маршруті.
            composable(Screen.Login.route) {
                LoginScreen(
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route) { launchSingleTop = true }
                    },
                    onNavigateToForgotPassword = {
                        navController.navigate(Screen.ForgotPassword.route) { launchSingleTop = true }
                    },
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) { popUpTo(0) }
                    }
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    onNavigateToLogin = { navController.popBackStack() },
                    onRegisterSuccess = {
                        navController.navigate(Screen.Home.route) { popUpTo(0) }
                    }
                )
            }
            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private val LumiNavLabelStyle = TextStyle(
    fontFamily = Montserrat,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 12.sp,
    color = LumiGlassColors.SecondaryText
)

/**
 * Нижня навігація з макета: скляна панель 73px, radius 11, іконки 24px,
 * підписи 12/500. Активний пункт лежить на скляній «пігулці» 77×47/15.
 */
@Composable
private fun LumiBottomNavigation(
    currentRoute: NavDestination?,
    onSelect: (BottomTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                lumiSecondaryGradient(alpha = 0.4f),
                RoundedCornerShape(11.dp)
            )
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(73.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomTabs.forEach { tab ->
                val selected = currentRoute?.hierarchy?.any { it.route == tab.screen.route } == true
                val label = stringResource(tab.labelRes)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(73.dp)
                        // Без indication: квадратний світлий відбиток натискання не показуємо.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(tab) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = if (selected) {
                            Modifier
                                .width(77.dp)
                                .height(47.dp)
                                // Скруглення малює сам фон, без clip(), інакше
                                // обрізаються нижні виносні елементи літер («y» у «Library»).
                                .background(
                                    lumiSecondaryGradient(alpha = 0.65f),
                                    RoundedCornerShape(15.dp)
                                )
                        } else {
                            Modifier
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (selected) {
                            androidx.compose.foundation.layout.Arrangement.Center
                        } else {
                            androidx.compose.foundation.layout.Arrangement.Center
                        }
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = label,
                            tint = LumiGlassColors.SecondaryText,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = label, style = LumiNavLabelStyle)
                    }
                }
            }
        }
    }
}

