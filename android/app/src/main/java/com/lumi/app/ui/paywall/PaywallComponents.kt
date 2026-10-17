package com.lumi.app.ui.paywall

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.app.R
import com.lumi.app.data.model.Entitlement
import com.lumi.app.ui.auth.AuthErrorColor
import com.lumi.app.ui.auth.AuthGradient
import com.lumi.app.ui.auth.AuthPlaceholderColor
import com.lumi.app.ui.auth.AuthSuccessColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.auth.PrimaryAuthButton
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.currentAppLocale
import com.lumi.app.ui.i18n.localized
import com.lumi.app.ui.i18n.uiText
import com.lumi.app.ui.subscription.LumiSecondaryButton

/** Перевага підписки у списку пейвола. */
data class PaywallBenefit(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int
)

// Що відкриває Premium: HQ, Lossless і завантаження (ТЗ 4.2).
val PremiumBenefits = listOf(
    PaywallBenefit(R.string.paywall_benefit_hq, R.string.paywall_benefit_hq_sub),
    PaywallBenefit(R.string.paywall_benefit_lossless, R.string.paywall_benefit_lossless_sub),
    PaywallBenefit(R.string.paywall_benefit_download, R.string.paywall_benefit_download_sub)
)

/** Перевага підписки: галочка, назва та коротке пояснення. */
@Composable
fun PaywallBenefitRow(benefit: PaywallBenefit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "✓",
            fontFamily = Montserrat,
            color = AuthSuccessColor,
            fontSize = 14.sp,
            // Галочка декоративна — TalkBack читає лише текст переваги.
            modifier = Modifier.clearAndSetSemantics { }
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(benefit.titleRes),
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(benefit.subtitleRes),
                fontFamily = Montserrat,
                color = AuthPlaceholderColor,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Reusable пейвол (Story 2): пояснює, чому дію заблоковано,
 * показує переваги та веде на екран підписки.
 */
@Composable
fun PaywallSheet(
    feature: PaywallFeature,
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: UiText? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AuthGradient, RoundedCornerShape(16.dp))
            .border(1.dp, AuthPlaceholderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(feature.titleRes),
            fontFamily = Montserrat,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // Назва функції підставляється у пояснення обраною мовою.
            text = stringResource(feature.messageRes),
            fontFamily = Montserrat,
            color = AuthTextColor,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.paywall_benefits_title).uppercase(currentAppLocale()),
            fontFamily = Montserrat,
            color = AuthPlaceholderColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        PremiumBenefits.forEach { PaywallBenefitRow(it) }

        // Помилка переходу не ховає пейвол: показуємо текст і пропонуємо спробувати ще раз.
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it.localized(),
                fontFamily = Montserrat,
                color = AuthErrorColor,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(16.dp))
        PrimaryAuthButton(
            text = uiText(R.string.paywall_cta),
            onClick = onSubscribe,
            loading = isLoading,
            height = 46.dp
        )
        Spacer(Modifier.height(10.dp))
        LumiSecondaryButton(
            text = stringResource(R.string.paywall_not_now),
            onClick = onDismiss,
            enabled = !isLoading
        )
    }
}

/**
 * Точка входу для преміум-функції: малює пейвол лише тоді,
 * коли функція справді заблокована тарифом.
 */
@Composable
fun PaywallGate(
    entitlement: Entitlement,
    feature: PaywallFeature?,
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: UiText? = null
) {
    // Активний користувач із підпискою пейвола не бачить узагалі.
    val locked = feature?.takeIf { PaywallRules.isLocked(entitlement, it) } ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        PaywallSheet(
            feature = locked,
            onSubscribe = onSubscribe,
            onDismiss = onDismiss,
            isLoading = isLoading,
            error = error
        )
    }
}
