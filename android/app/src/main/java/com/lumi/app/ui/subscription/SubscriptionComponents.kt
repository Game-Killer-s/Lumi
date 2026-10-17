package com.lumi.app.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.app.R
import com.lumi.app.data.model.PaymentMethod
import com.lumi.app.data.model.SubscriptionPlan
import com.lumi.app.data.model.SubscriptionStatusResponse
import com.lumi.app.ui.auth.AuthBackgroundColor
import com.lumi.app.ui.auth.AuthErrorColor
import com.lumi.app.ui.auth.AuthGradient
import com.lumi.app.ui.auth.AuthPlaceholderColor
import com.lumi.app.ui.auth.AuthSuccessColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.MinTouchTarget
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.LumiFormatters
import com.lumi.app.ui.i18n.currentAppLocale

// Акцент для Grace Period — той самий оранжевий, що й на макетах.
val GraceColor = Color(0xFFFFA26B)

/**
 * Ціна тарифу обраною мовою: «1 299 ₴ / 30 днів» або «Безкоштовно».
 * Сума і період форматуються під локаль інтерфейсу (ТЗ 6.3.1).
 */
@Composable
fun priceLabel(plan: SubscriptionPlan): String {
    if (plan.priceCents == 0) {
        return stringResource(R.string.subscription_free)
    }

    val price = LumiFormatters.formatMoney(plan.priceCents, currentAppLocale())
    val period = pluralStringResource(
        R.plurals.subscription_period_days,
        plan.periodDays,
        plan.periodDays
    )

    return stringResource(R.string.subscription_price, price, period)
}

/** Маска картки: бренд і останні 4 цифри — однакові для всіх мов. */
fun cardLabel(method: PaymentMethod): String = "${method.brand} •••• ${method.last4}"

// Колір плашки залежить від стану підписки.
fun bannerColor(status: String): Color = when (status) {
    SubscriptionStatus.ACTIVE -> AuthSuccessColor
    SubscriptionStatus.GRACE_PERIOD -> GraceColor
    SubscriptionStatus.SUSPENDED, SubscriptionStatus.CANCELLED -> AuthErrorColor
    else -> AuthPlaceholderColor
}

/**
 * Інформаційна плашка про стан підписки (Story 3).
 *
 * Крім тексту стану показує дату завершення Grace Period, причину обмеження
 * і дії для відновлення доступу. Дії з'являються лише тоді, коли екран
 * передав відповідний обробник.
 */
@Composable
fun SubscriptionBanner(
    status: SubscriptionStatusResponse,
    modifier: Modifier = Modifier,
    onUpdatePaymentMethod: (() -> Unit)? = null,
    onOpenSubscription: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null
) {
    val color = bannerColor(status.status)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = status.title,
            fontFamily = Montserrat,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = status.message,
            fontFamily = Montserrat,
            color = AuthTextColor,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        // Причина обмеження: у suspended показуємо, чому доступ закрито.
        if (SubscriptionStatusRules.showsSuspendedReason(status.status)) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.subscription_suspended_reason, status.message),
                fontFamily = Montserrat,
                color = AuthErrorColor,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        // Дата Grace Period зникає, щойно підписка знову стала активною.
        val graceUntil = LumiFormatters.formatDate(status.gracePeriodEndsAt, currentAppLocale())

        if (SubscriptionStatusRules.showsGraceDate(status.status) && graceUntil != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.subscription_grace_until, graceUntil),
                fontFamily = Montserrat,
                color = GraceColor,
                fontSize = 11.sp
            )
        }

        // CTA оновлення способу оплати та переходу до тарифів.
        val actions = SubscriptionStatusRules.recoveryActions(status.status)

        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
        }

        if (
            SubscriptionRecoveryAction.UPDATE_PAYMENT_METHOD in actions &&
            onUpdatePaymentMethod != null
        ) {
            LumiSecondaryButton(
                text = stringResource(R.string.subscription_cta_update_payment),
                onClick = onUpdatePaymentMethod
            )
        }

        if (SubscriptionRecoveryAction.OPEN_SUBSCRIPTION in actions && onOpenSubscription != null) {
            Spacer(Modifier.height(6.dp))
            BannerTextAction(
                text = stringResource(R.string.subscription_cta_open),
                onClick = onOpenSubscription
            )
        }

        if (SubscriptionRecoveryAction.REFRESH_STATUS in actions && onRefresh != null) {
            Spacer(Modifier.height(6.dp))
            BannerTextAction(
                text = stringResource(R.string.subscription_cta_refresh),
                onClick = onRefresh
            )
        }
    }
}

/**
 * Текстова дія всередині плашки: окремий елемент з роллю кнопки
 * і зоною дотику щонайменше 48dp (ТЗ 6.3.7).
 */
@Composable
private fun BannerTextAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.Button) { onClick() },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontFamily = Montserrat,
            color = AuthTextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Преміум-дію заблоковано, бо доступ призупинено (Story 3):
 * показуємо причину й ведемо до відновлення оплати.
 */
@Composable
fun PremiumBlockedDialog(
    reason: String,
    onRecover: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.subscription_suspended_title),
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.subscription_suspended_reason, reason),
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onRecover) {
                Text(
                    text = stringResource(R.string.subscription_cta_update_payment),
                    fontFamily = Montserrat,
                    color = AuthTextColor
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_close),
                    fontFamily = Montserrat,
                    color = AuthTextColor
                )
            }
        }
    )
}

/**
 * Картка тарифу на екрані вибору плану.
 */
@Composable
fun PlanCard(
    plan: SubscriptionPlan,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Тариф — один вузол вибору: TalkBack озвучує назву, ціну й стан
    // «обрано» разом (ТЗ 6.3.7), тому вкладені тексти не дублюються.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .background(AuthGradient, RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AuthTextColor else AuthPlaceholderColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = plan.title,
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = priceLabel(plan),
                fontFamily = Montserrat,
                color = if (selected) AuthTextColor else AuthPlaceholderColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (!plan.description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = plan.description,
                fontFamily = Montserrat,
                color = AuthPlaceholderColor,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * Рядок зі свитчем для екрана налаштувань сповіщень.
 */
@Composable
fun LumiSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    // Рядок — одна інтерактивна зона з роллю Checkbox: TalkBack читає
    // підпис і підказку один раз і озвучує стан (ТЗ 6.3.7).
    val description = if (subtitle.isNullOrBlank()) title else "$title. $subtitle"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { }
        ) {
            Text(
                text = title,
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = Montserrat,
                    color = AuthPlaceholderColor,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Switch(
            checked = checked,
            // Свитч не є окремою ціллю для TalkBack — керує рядок.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AuthBackgroundColor,
                checkedTrackColor = AuthTextColor,
                uncheckedThumbColor = AuthPlaceholderColor,
                uncheckedTrackColor = Color(0x334B1F6F),
                uncheckedBorderColor = AuthPlaceholderColor
            )
        )
    }
}

/**
 * Другорядна кнопка: рамка і текст, без градієнта.
 */
@Composable
fun LumiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Візуальна висота 44dp за макетом, зона дотику — щонайменше 48dp (ТЗ 6.3.7).
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(enabled = enabled, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .border(1.dp, AuthPlaceholderColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Верхня панель з кнопкою «Назад» для внутрішніх екранів.
 */
@Composable
fun LumiTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backLabel = stringResource(R.string.common_back)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // «Назад» — кнопка з роллю Button, підказкою для TalkBack
        // і зоною дотику 48dp (ТЗ 6.3.7).
        Box(
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .clickable(role = Role.Button, onClickLabel = backLabel) { onBack() },
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = stringResource(R.string.common_back_arrow),
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            fontFamily = Montserrat,
            color = AuthTextColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Заголовок секції на внутрішніх екранах.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    // uppercase() з локаллю: у турецькій «i» → «İ», тому враховуємо мову інтерфейсу.
    val locale = currentAppLocale()

    Text(
        text = text.uppercase(locale),
        fontFamily = Montserrat,
        color = AuthPlaceholderColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp)
    )
}

/** Сума платежу обраною мовою: «1 299,00 ₴» або «—», якщо суми немає. */
@Composable
fun moneyLabel(cents: Int?): String = LumiFormatters.formatMoney(cents, currentAppLocale())

/** Статус платежу локалізується: сервер віддає лише код статусу. */
@Composable
fun paymentStatusLabel(status: String): String = when (status) {
    "SUCCEEDED" -> stringResource(R.string.payment_status_paid)
    "FAILED" -> stringResource(R.string.payment_status_failed)
    "REFUNDED" -> stringResource(R.string.payment_status_refunded)
    else -> stringResource(R.string.payment_status_pending)
}

fun paymentStatusColor(status: String): Color = when (status) {
    "SUCCEEDED" -> AuthSuccessColor
    "FAILED" -> AuthErrorColor
    else -> AuthPlaceholderColor
}

/**
 * Інформаційна картка поточного тарифу з датою і сумою наступного списання.
 */
@Composable
fun PlanInfoCard(
    status: SubscriptionStatusResponse,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AuthGradient, RoundedCornerShape(12.dp))
            .border(1.dp, AuthPlaceholderColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = status.plan?.title
                    ?.let { stringResource(R.string.subscription_plan_label, it) }
                    ?: stringResource(R.string.subscription_no_plan),
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            // status.title — текст стану з бекенду (ACTIVE/GRACE_PERIOD...).
            Text(
                text = status.title,
                fontFamily = Montserrat,
                color = bannerColor(status.status),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        InfoLine(stringResource(R.string.subscription_amount), moneyLabel(status.nextChargeAmountCents))
        InfoLine(
            stringResource(R.string.subscription_next_charge),
            LumiFormatters.formatDate(status.nextChargeDate, currentAppLocale())
                ?: LumiFormatters.EMPTY_VALUE
        )
        InfoLine(
            stringResource(R.string.subscription_payments_count),
            LumiFormatters.formatNumber(status.paymentsCount, currentAppLocale())
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontFamily = Montserrat,
            color = AuthPlaceholderColor,
            fontSize = 12.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontFamily = Montserrat,
            color = AuthTextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
