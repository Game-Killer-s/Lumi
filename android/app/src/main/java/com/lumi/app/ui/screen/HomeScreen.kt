package com.lumi.app.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumi.app.R
import com.lumi.app.data.repository.AuthRepository
import com.lumi.app.network.TokenManager
import com.lumi.app.ui.auth.AuthBackground
import com.lumi.app.ui.auth.AuthErrorColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.auth.PrimaryAuthButton
import com.lumi.app.ui.i18n.LumiFormatters
import com.lumi.app.ui.i18n.currentAppLocale
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.i18n.uiText
import com.lumi.app.ui.notifications.NotificationsEvent
import com.lumi.app.ui.notifications.NotificationsViewModel
import com.lumi.app.ui.paywall.PaywallEvent
import com.lumi.app.ui.paywall.PaywallFeature
import com.lumi.app.ui.paywall.PaywallGate
import com.lumi.app.ui.paywall.PaywallViewModel
import com.lumi.app.ui.subscription.LumiSecondaryButton
import com.lumi.app.ui.subscription.PremiumBlockedDialog
import com.lumi.app.ui.subscription.SectionTitle
import com.lumi.app.ui.subscription.SubscriptionBanner
import com.lumi.app.ui.subscription.SubscriptionEvent
import com.lumi.app.ui.subscription.SubscriptionStatusRules
import com.lumi.app.ui.subscription.SubscriptionViewModel
import kotlinx.coroutines.launch

/**
 * Головний екран (медіатека). Показує вітання, стан підписки
 * і переходи до сповіщень та оплати.
 */
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val user = TokenManager.getCurrentUser()
    val scope = rememberCoroutineScope()
    val subscriptionViewModel: SubscriptionViewModel = viewModel()
    val notificationsViewModel: NotificationsViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val state = subscriptionViewModel.uiState
    val notificationsState = notificationsViewModel.uiState
    val paywallViewModel: PaywallViewModel = viewModel()
    val paywallState = paywallViewModel.uiState

    // Дія, з якої відкрили пейвол: її й продовжуємо після апгрейду (Story 2).
    val resumeLockedAction: (PaywallFeature) -> Unit = { feature ->
        when (feature) {
            // HQ і Lossless — далі звичайний сценарій відтворення треку.
            PaywallFeature.HQ_QUALITY, PaywallFeature.LOSSLESS_QUALITY ->
                subscriptionViewModel.checkPlayAccess()

            PaywallFeature.DOWNLOAD -> scope.launch {
                snackbarHostState.showSnackbar(
                    uiText(R.string.home_download_started).resolveCurrent()
                )
            }
        }
    }

    // Преміум-функції (Story 3): у suspended дію не запускаємо —
    // показуємо причину й ведемо до відновлення оплати.
    val requestPremium: (PaywallFeature) -> Unit = { feature ->
        if (subscriptionViewModel.checkPremiumAction()) {
            paywallViewModel.request(feature)
        }
    }

    LaunchedEffect(Unit) {
        // Червона точка на іконці сповіщень (з ТЗ).
        notificationsViewModel.loadNotifications()
    }

    LaunchedEffect(Unit) {
        subscriptionViewModel.events.collect { event ->
            when (event) {
                is SubscriptionEvent.Message -> snackbarHostState.showSnackbar(
                    // Мова під час перекладу береться з налаштувань застосунку,
                    // тому повідомлення з'являється обраною мовою.
                    event.text.resolveCurrent()
                )

                // Billing refresh повернув активну підписку (Story 3).
                is SubscriptionEvent.Recovered -> snackbarHostState.showSnackbar(
                    uiText(R.string.subscription_recovered).resolveCurrent()
                )

                else -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        notificationsViewModel.events.collect { event ->
            when (event) {
                is NotificationsEvent.Message -> snackbarHostState.showSnackbar(
                    event.text.resolveCurrent()
                )
            }
        }
    }

    // Пейвол: якщо функція вже доступна тарифом — пейвол не показуємо,
    // а одразу продовжуємо дію користувача.
    LaunchedEffect(Unit) {
        paywallViewModel.events.collect { event ->
            when (event) {
                is PaywallEvent.Unlocked -> resumeLockedAction(event.feature)
            }
        }
    }

    // Повернулись з екрана тарифів після оплати — доводимо дію до кінця
    // і оновлюємо плашку підписки (Story 3).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        paywallViewModel.resumeAfterUpgrade()?.let(resumeLockedAction)
        subscriptionViewModel.loadStatus()
    }

    AuthBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.home_greeting,
                        user?.nickname
                            ?: stringResource(R.string.home_default_user)
                    ),
                    fontFamily = Montserrat,
                    color = AuthTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Плашка про стан підписки (Story 3): дата Grace Period,
                // причина призупинення та дії для відновлення доступу.
                state.status?.let { status ->
                    if (SubscriptionStatusRules.shouldShowBanner(status.status)) {
                        Spacer(Modifier.height(20.dp))
                        SubscriptionBanner(
                            status = status,
                            onUpdatePaymentMethod = onOpenSubscription,
                            onOpenSubscription = onOpenSubscription,
                            onRefresh = { subscriptionViewModel.refreshBilling() }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                PrimaryAuthButton(
                    // Кнопка приймає UiText: мова підставляється під час показу.
                    text = uiText(R.string.home_play_track),
                    onClick = { subscriptionViewModel.checkPlayAccess() },
                    enabled = !state.isLoading,
                    height = 48.dp
                )

                Spacer(Modifier.height(24.dp))

                // Точки входу преміум-функцій: без підписки відкривають пейвол (Story 2),
                // а в suspended — причину призупинення (Story 3).
                SectionTitle(stringResource(R.string.home_quality_section))

                LumiSecondaryButton(
                    text = stringResource(R.string.home_play_hq),
                    onClick = { requestPremium(PaywallFeature.HQ_QUALITY) }
                )

                Spacer(Modifier.height(10.dp))

                LumiSecondaryButton(
                    text = stringResource(R.string.home_play_lossless),
                    onClick = { requestPremium(PaywallFeature.LOSSLESS_QUALITY) }
                )

                Spacer(Modifier.height(10.dp))

                LumiSecondaryButton(
                    text = stringResource(R.string.home_download_track),
                    onClick = { requestPremium(PaywallFeature.DOWNLOAD) }
                )

                Spacer(Modifier.height(10.dp))

                NotificationButton(
                    text = stringResource(R.string.home_notifications),
                    unread = notificationsState.unread,
                    onClick = onOpenNotifications
                )

                Spacer(Modifier.height(10.dp))

                LumiSecondaryButton(
                    text = stringResource(R.string.home_notification_settings),
                    onClick = onOpenNotificationSettings
                )

                Spacer(Modifier.height(10.dp))

                LumiSecondaryButton(
                    text = stringResource(R.string.home_subscription),
                    onClick = onOpenSubscription
                )

                Spacer(Modifier.height(10.dp))

                LumiSecondaryButton(
                    text = stringResource(R.string.home_app_settings),
                    onClick = onOpenSettings
                )

                Spacer(Modifier.height(24.dp))

                OutlinedButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        scope.launch {
                            AuthRepository().logout()
                            onLogout()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.home_logout),
                        fontFamily = Montserrat
                    )
                }

                Spacer(Modifier.height(32.dp))
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )

            // Пейвол поверх екрана: лише для заблокованої функції.
            PaywallGate(
                entitlement = state.entitlement,
                feature = paywallState.feature,
                isLoading = paywallState.isLoading,
                error = paywallState.error,
                onSubscribe = { paywallViewModel.openSubscription(onOpenSubscription) },
                onDismiss = { paywallViewModel.dismiss() }
            )
        }
    }

    // Результат спроби увімкнути трек: дозволено або причина обмеження.
    state.playAccess?.let { access ->
        AlertDialog(
            onDismissRequest = { subscriptionViewModel.dismissPlayAccess() },
            title = {
                Text(
                    text = stringResource(
                        if (access.allowed) {
                            R.string.home_play_access_allowed
                        } else {
                            R.string.home_play_access_denied
                        }
                    ),
                    fontFamily = Montserrat,
                    color = AuthTextColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (access.allowed) {
                        // Якість озвучується локалізовано, повідомлення приходить з бекенду.
                        stringResource(
                            R.string.home_play_access_quality,
                            stringResource(
                                if (access.quality == "HQ") {
                                    R.string.home_play_access_quality_hq
                                } else {
                                    R.string.home_play_access_quality_standard
                                }
                            ),
                            access.message
                        )
                    } else {
                        stringResource(
                            R.string.home_play_access_payment_hint,
                            access.message
                        )
                    },
                    fontFamily = Montserrat,
                    color = AuthTextColor,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { subscriptionViewModel.dismissPlayAccess() }) {
                    Text(
                        text = stringResource(R.string.common_ok),
                        fontFamily = Montserrat,
                        color = AuthTextColor
                    )
                }
            }
        )
    }

    // Преміум-дію заблоковано, бо доступ призупинено: причина і CTA (Story 3).
    if (state.blockedPremium) {
        PremiumBlockedDialog(
            reason = state.status?.message.orEmpty(),
            onRecover = {
                subscriptionViewModel.dismissBlockedPremium()
                onOpenSubscription()
            },
            onDismiss = { subscriptionViewModel.dismissBlockedPremium() }
        )
    }
}

// Іконка сповіщень із червоною крапкою, коли є непрочитані (з ТЗ).
@Composable
private fun NotificationButton(
    text: String,
    unread: Int,
    onClick: () -> Unit
) {
    val locale = currentAppLocale()
    // Стан «є непрочитані» озвучується TalkBack-ом (ТЗ 6.3.7).
    val unreadState = pluralStringResource(
        R.plurals.notifications_unread_state,
        unread,
        unread
    )
    val description = if (unread > 0) "$text, $unreadState" else text

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        LumiSecondaryButton(text = text, onClick = onClick)

        if (unread > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(AuthErrorColor, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = LumiFormatters.formatNumber(unread, locale),
                    fontFamily = Montserrat,
                    color = AuthErrorColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
