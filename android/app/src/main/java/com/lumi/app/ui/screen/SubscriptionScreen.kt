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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumi.app.R
import com.lumi.app.ui.auth.AuthBackground
import com.lumi.app.ui.auth.AuthErrorColor
import com.lumi.app.ui.auth.AuthGradient
import com.lumi.app.ui.auth.AuthPlaceholderColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.LumiTextField
import com.lumi.app.ui.auth.MinTouchTarget
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.auth.PrimaryAuthButton
import com.lumi.app.ui.i18n.LumiFormatters
import com.lumi.app.ui.i18n.currentAppLocale
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.i18n.uiText
import com.lumi.app.ui.subscription.LumiSecondaryButton
import com.lumi.app.ui.subscription.LumiSwitchRow
import com.lumi.app.ui.subscription.LumiTopBar
import com.lumi.app.ui.subscription.PlanCard
import com.lumi.app.ui.subscription.SectionTitle
import com.lumi.app.ui.subscription.SubscriptionBanner
import com.lumi.app.ui.subscription.SubscriptionEvent
import com.lumi.app.ui.subscription.SubscriptionStatusRules
import com.lumi.app.ui.subscription.SubscriptionViewModel
import com.lumi.app.ui.subscription.cardLabel
import com.lumi.app.ui.subscription.moneyLabel
import com.lumi.app.ui.subscription.PlanInfoCard
import com.lumi.app.ui.subscription.paymentStatusColor
import com.lumi.app.ui.subscription.paymentStatusLabel

/**
 * Екран підписки: вибір тарифу, платіжні дані та скасування.
 */
@Composable
fun SubscriptionScreen(onBack: () -> Unit, onUpgraded: () -> Unit = {}) {
    val viewModel: SubscriptionViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val state = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // Snackbar перекладається в момент показу — обраною мовою.
                is SubscriptionEvent.Message ->
                    snackbarHostState.showSnackbar(event.text.resolveCurrent(context))

                is SubscriptionEvent.Paid -> {
                    snackbarHostState.showSnackbar(uiText(R.string.subscription_paid).resolveCurrent(context))
                    // Підписку оформлено: повертаємось до дії, з якої відкрили пейвол (Story 2).
                    onUpgraded()
                }

                is SubscriptionEvent.Cancelled ->
                    snackbarHostState.showSnackbar(uiText(R.string.subscription_cancelled).resolveCurrent(context))

                // Billing refresh підтвердив оплату — Grace Period закрито (Story 3).
                is SubscriptionEvent.Recovered ->
                    snackbarHostState.showSnackbar(uiText(R.string.subscription_recovered).resolveCurrent(context))
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
                LumiTopBar(title = stringResource(R.string.subscription_topbar), onBack = onBack)

                Text(
                    text = stringResource(R.string.subscription_title),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                // Плашка зі станом підписки (Story 3): дата Grace Period,
                // причина обмеження і дії для відновлення доступу.
                state.status?.let { status ->
                    if (SubscriptionStatusRules.shouldShowBanner(status.status)) {
                        Spacer(Modifier.height(12.dp))
                        SubscriptionBanner(
                            status = status,
                            onUpdatePaymentMethod = { viewModel.openPaymentMethodForm() },
                            onRefresh = { viewModel.refreshBilling() }
                        )
                        Spacer(Modifier.height(10.dp))
                        PlanInfoCard(status = status)
                    }
                }

                SectionTitle(stringResource(R.string.subscription_choose_plan))

                state.plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        selected = plan.id == state.selectedPlanId,
                        onSelect = { viewModel.selectPlan(plan.id) }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                SectionTitle(stringResource(R.string.subscription_payment_section))

                LumiSwitchRow(
                    title = stringResource(R.string.subscription_google_pay),
                    checked = state.useGooglePay,
                    onCheckedChange = viewModel::setUseGooglePay,
                    subtitle = stringResource(R.string.subscription_google_pay_sub)
                )

                if (state.useGooglePay) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.subscription_google_pay_hint),
                        fontFamily = Montserrat,
                        color = AuthPlaceholderColor,
                        fontSize = 11.sp
                    )
                } else if (state.paymentMethods.isEmpty() || state.showCardForm) {
                    Spacer(Modifier.height(8.dp))
                    CardForm(viewModel = viewModel)
                }

                Spacer(Modifier.height(16.dp))

                PrimaryAuthButton(
                    text = uiText(R.string.subscription_pay),
                    onClick = { viewModel.pay() },
                    enabled = !state.isLoading,
                    loading = state.isLoading,
                    height = 44.dp
                )

                Spacer(Modifier.height(10.dp))

                LumiSecondaryButton(
                    text = stringResource(R.string.subscription_save_card),
                    onClick = { viewModel.saveCard() },
                    enabled = !state.isLoading
                )

                SectionTitle(stringResource(R.string.subscription_saved_cards))

                LumiSecondaryButton(
                    text = stringResource(R.string.subscription_update_card),
                    onClick = { viewModel.toggleCardForm() },
                    enabled = !state.isLoading
                )

                Spacer(Modifier.height(12.dp))

                if (state.paymentMethods.isEmpty()) {
                    Text(
                        text = stringResource(R.string.subscription_no_saved_cards),
                        fontFamily = Montserrat,
                        color = AuthPlaceholderColor,
                        fontSize = 12.sp
                    )
                }

                state.paymentMethods.forEach { method ->
                    SavedCardRow(
                        label = cardLabel(method),
                        isDefault = method.isDefault,
                        onMakeDefault = { viewModel.makeDefault(method) },
                        onRemove = { viewModel.removeMethod(method) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                SectionTitle(stringResource(R.string.subscription_payments_history))

                if (state.payments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.subscription_no_payments),
                        fontFamily = Montserrat,
                        color = AuthPlaceholderColor,
                        fontSize = 12.sp
                    )
                }

                state.payments.forEach { payment ->
                    PaymentRow(
                        title = moneyLabel(payment.amountCents),
                        date = LumiFormatters.formatDate(payment.createdAt, currentAppLocale())
                            ?: LumiFormatters.EMPTY_VALUE,
                        status = payment.status,
                        reason = payment.failureReason
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (state.status?.hasSubscription == true) {
                    SectionTitle(stringResource(R.string.subscription_manage))

                    if (state.status.cancelAtPeriodEnd) {
                        Text(
                            text = stringResource(R.string.subscription_cancelled_note),
                            fontFamily = Montserrat,
                            color = AuthPlaceholderColor,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    } else {
                        LumiSecondaryButton(
                            text = stringResource(R.string.subscription_cancel),
                            onClick = { viewModel.cancel() },
                            enabled = !state.isLoading
                        )
                    }
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

// Форма картки: номер, власник, строк дії та CVC.
@Composable
private fun CardForm(viewModel: SubscriptionViewModel) {
    val state = viewModel.uiState

    LumiTextField(
        value = state.cardNumber,
        onValueChange = viewModel::onCardNumberChange,
        label = uiText(R.string.card_number),
        placeholder = uiText(R.string.card_number_placeholder),
        keyboardType = KeyboardType.Number
    )

    Spacer(Modifier.height(12.dp))

    LumiTextField(
        value = state.holderName,
        onValueChange = viewModel::onHolderNameChange,
        label = uiText(R.string.card_holder),
        placeholder = uiText(R.string.card_holder_placeholder)
    )

    Spacer(Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
        LumiTextField(
            value = state.expMonth,
            onValueChange = viewModel::onExpMonthChange,
            label = uiText(R.string.card_month),
            placeholder = uiText(R.string.card_month_placeholder),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        LumiTextField(
            value = state.expYear,
            onValueChange = viewModel::onExpYearChange,
            label = uiText(R.string.card_year),
            placeholder = uiText(R.string.card_year_placeholder),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        LumiTextField(
            value = state.cvc,
            onValueChange = viewModel::onCvcChange,
            label = uiText(R.string.card_cvc),
            placeholder = uiText(R.string.card_cvc_placeholder),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
    }
}

// Рядок історії оплат: сума, дата і статус платежу.
@Composable
private fun PaymentRow(
    title: String,
    date: String,
    status: String,
    reason: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuthGradient, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = reason ?: date,
                fontFamily = Montserrat,
                color = AuthPlaceholderColor,
                fontSize = 10.sp
            )
        }

        Text(
            text = paymentStatusLabel(status),
            fontFamily = Montserrat,
            color = paymentStatusColor(status),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Збережена картка: маска, мітка «Основна» і дії.
@Composable
private fun SavedCardRow(
    label: String,
    isDefault: Boolean,
    onMakeDefault: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuthGradient, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (isDefault) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.card_default),
                    fontFamily = Montserrat,
                    color = AuthPlaceholderColor,
                    fontSize = 10.sp
                )
            }
        }

        if (!isDefault) {
            Text(
                text = stringResource(R.string.card_make_default),
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 12.sp,
                modifier = Modifier
                    // Дія «Зробити основною» — кнопка з зоною дотику 48dp.
                    .clickable(role = Role.Button) { onMakeDefault() }
                    .heightIn(min = MinTouchTarget)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 4.dp)
            )
            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = stringResource(R.string.card_remove),
            fontFamily = Montserrat,
            color = AuthErrorColor,
            fontSize = 12.sp,
            modifier = Modifier
                // Дія «Видалити» — кнопка з зоною дотику 48dp (ТЗ 6.3.7).
                .clickable(role = Role.Button) { onRemove() }
                .heightIn(min = MinTouchTarget)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 4.dp)
        )
    }
}
