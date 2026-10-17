package com.lumi.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumi.app.R
import com.lumi.app.data.model.NotificationItem
import com.lumi.app.ui.auth.AuthBackground
import com.lumi.app.ui.auth.AuthGradient
import com.lumi.app.ui.auth.AuthPlaceholderColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.MinTouchTarget
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.LumiFormatters
import com.lumi.app.ui.i18n.currentAppLocale
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.notifications.NotificationsEvent
import com.lumi.app.ui.notifications.NotificationsViewModel
import com.lumi.app.ui.subscription.LumiTopBar

/**
 * Шторка сповіщень: список того, що вже прийшло користувачу.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val viewModel: NotificationsViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val state = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
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
                LumiTopBar(title = stringResource(R.string.notifications_topbar), onBack = onBack)

                Text(
                    text = stringResource(R.string.notifications_subtitle),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.notifications_unread_count, state.unread),
                    fontFamily = Montserrat,
                    color = AuthTextColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                if (state.unread > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.notifications_mark_all_read),
                        fontFamily = Montserrat,
                        color = AuthTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            // Посилання-дія: роль кнопки і зона дотику 48dp (ТЗ 6.3.7).
                            .clickable(role = Role.Button) { viewModel.markAllAsRead() }
                            .heightIn(min = MinTouchTarget)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 6.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (state.items.isEmpty() && !state.isLoading) {
                    Text(
                        text = stringResource(R.string.notifications_empty),
                        fontFamily = Montserrat,
                        color = AuthPlaceholderColor,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 40.dp)
                    )
                }

                state.items.forEach { item ->
                    NotificationRow(item = item, onClick = { viewModel.markAsRead(item) })
                    Spacer(Modifier.height(8.dp))
                }

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

// Один рядок шторки: непрочитані підсвічені точкою і жирним заголовком.
@Composable
private fun NotificationRow(item: NotificationItem, onClick: () -> Unit) {
    // Стан рядка озвучується окремо («Непрочитане»), а роль Button
    // підказує TalkBack, що рядок можна активувати (ТЗ 6.3.7).
    val unreadState = stringResource(R.string.notifications_row_unread_state)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuthGradient, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onClick() }
            .semantics { if (!item.isRead) stateDescription = unreadState }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!item.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(AuthTextColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
            }

            Text(
                text = item.title,
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 14.sp,
                fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Bold
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = item.body,
            fontFamily = Montserrat,
            color = AuthTextColor,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        val date = LumiFormatters.formatDate(item.createdAt, currentAppLocale())

        if (date != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = date,
                fontFamily = Montserrat,
                color = AuthPlaceholderColor,
                fontSize = 10.sp
            )
        }
    }
}
