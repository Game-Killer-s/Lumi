package com.lumi.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumi.app.R
import com.lumi.app.ui.auth.AuthBackground
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.notifications.NotificationsEvent
import com.lumi.app.ui.notifications.NotificationsViewModel
import com.lumi.app.ui.subscription.LumiSwitchRow
import com.lumi.app.ui.subscription.LumiTopBar
import com.lumi.app.ui.subscription.SectionTitle

/**
 * Екран налаштувань сповіщень: свитчі для нових релізів,
 * оновлень виконавців, плейлистів і каналів доставки.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val viewModel: NotificationsViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val state = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // Snackbar перекладається в момент показу — обраною мовою.
                is NotificationsEvent.Message -> snackbarHostState.showSnackbar(event.text.resolveCurrent(context))
            }
        }
    }

    AuthBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LumiTopBar(title = stringResource(R.string.notif_settings_topbar), onBack = onBack)

                Text(
                    text = stringResource(R.string.notif_settings_title),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.notif_settings_subtitle),
                    fontFamily = Montserrat,
                    color = AuthTextColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))

                SectionTitle(stringResource(R.string.notif_section_releases))
                LumiSwitchRow(
                    title = stringResource(R.string.notif_new_releases),
                    checked = state.settings.newReleases,
                    onCheckedChange = viewModel::toggleNewReleases,
                    subtitle = stringResource(R.string.notif_new_releases_sub)
                )
                LumiSwitchRow(
                    title = stringResource(R.string.notif_artist_updates),
                    checked = state.settings.artistUpdates,
                    onCheckedChange = viewModel::toggleArtistUpdates,
                    subtitle = stringResource(R.string.notif_artist_updates_sub)
                )
                LumiSwitchRow(
                    title = stringResource(R.string.notif_platform_updates),
                    checked = state.settings.platformUpdates,
                    onCheckedChange = viewModel::togglePlatformUpdates,
                    subtitle = stringResource(R.string.notif_platform_updates_sub)
                )

                SectionTitle(stringResource(R.string.notif_section_channels))
                LumiSwitchRow(
                    title = stringResource(R.string.notif_push),
                    checked = state.settings.pushEnabled,
                    onCheckedChange = viewModel::togglePush,
                    subtitle = stringResource(R.string.notif_push_sub)
                )
                LumiSwitchRow(
                    title = stringResource(R.string.notif_email),
                    checked = state.settings.emailEnabled,
                    onCheckedChange = viewModel::toggleEmail,
                    subtitle = stringResource(R.string.notif_email_sub)
                )

                Spacer(Modifier.height(32.dp))
            }

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AuthTextColor
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
    }
}
