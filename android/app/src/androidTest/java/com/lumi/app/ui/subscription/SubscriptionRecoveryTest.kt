package com.lumi.app.ui.subscription

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumi.app.R
import com.lumi.app.data.model.SubscriptionStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-перевірки відновлення доступу в Grace Period і Suspended (Story 3).
 * Запуск на пристрої або емуляторі: gradlew connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionRecoveryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(@StringRes id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    // Фраза банера з датою: перевіряємо сам текст без конкретної дати,
    // бо дата форматується під мову інтерфейсу.
    private val gracePrefix: String
        get() = string(R.string.subscription_grace_until, "%1\$s").replace("%1\$s", "").trim()

    private fun status(
        state: String,
        title: String = "Стан підписки",
        message: String = "Повідомлення про стан підписки",
        gracePeriodEndsAt: String? = null
    ) = SubscriptionStatusResponse(
        hasSubscription = true,
        status = state,
        title = title,
        message = message,
        gracePeriodEndsAt = gracePeriodEndsAt
    )

    private fun showBanner(
        state: SubscriptionStatusResponse,
        onUpdatePaymentMethod: (() -> Unit)? = null,
        onOpenSubscription: (() -> Unit)? = null,
        onRefresh: (() -> Unit)? = null
    ) {
        composeTestRule.setContent {
            SubscriptionBanner(
                status = state,
                onUpdatePaymentMethod = onUpdatePaymentMethod,
                onOpenSubscription = onOpenSubscription,
                onRefresh = onRefresh
            )
        }
    }

    // ---------- Grace Period ----------

    @Test
    fun graceBannerShowsTheEndDate() {
        showBanner(
            status(SubscriptionStatus.GRACE_PERIOD, gracePeriodEndsAt = "2026-03-08T10:00:00Z")
        )

        composeTestRule.onNodeWithText(gracePrefix, substring = true).assertIsDisplayed()
    }

    @Test
    fun graceBannerOffersAllRecoveryActions() {
        showBanner(
            status(SubscriptionStatus.GRACE_PERIOD, gracePeriodEndsAt = "2026-03-08T10:00:00Z"),
            onUpdatePaymentMethod = {},
            onOpenSubscription = {},
            onRefresh = {}
        )

        composeTestRule.onNodeWithText(string(R.string.subscription_cta_update_payment))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.subscription_cta_open)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.subscription_cta_refresh)).assertIsDisplayed()
    }

    @Test
    fun updatePaymentMethodCtaAsksForANewCard() {
        var taps = 0
        showBanner(
            status(SubscriptionStatus.GRACE_PERIOD, gracePeriodEndsAt = "2026-03-08T10:00:00Z"),
            onUpdatePaymentMethod = { taps++ }
        )

        composeTestRule.onNodeWithText(string(R.string.subscription_cta_update_payment)).performClick()

        assertEquals(1, taps)
    }

    @Test
    fun openSubscriptionCtaLeadsToTheSubscriptionScreen() {
        var navigations = 0
        showBanner(
            status(SubscriptionStatus.GRACE_PERIOD, gracePeriodEndsAt = "2026-03-08T10:00:00Z"),
            onOpenSubscription = { navigations++ }
        )

        composeTestRule.onNodeWithText(string(R.string.subscription_cta_open)).performClick()

        assertEquals(1, navigations)
    }

    @Test
    fun refreshCtaAsksForABillingRefresh() {
        var refreshes = 0
        showBanner(
            status(SubscriptionStatus.GRACE_PERIOD, gracePeriodEndsAt = "2026-03-08T10:00:00Z"),
            onRefresh = { refreshes++ }
        )

        composeTestRule.onNodeWithText(string(R.string.subscription_cta_refresh)).performClick()

        assertEquals(1, refreshes)
    }

    // ---------- Після відновлення active ----------

    @Test
    fun activeBannerHidesTheGraceDateAndActions() {
        // Billing refresh повернув активну підписку: дата й дії зникають.
        showBanner(
            status(SubscriptionStatus.ACTIVE, gracePeriodEndsAt = "2026-03-08T10:00:00Z"),
            onUpdatePaymentMethod = {},
            onOpenSubscription = {},
            onRefresh = {}
        )

        composeTestRule.onNodeWithText(gracePrefix, substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.subscription_cta_update_payment))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.subscription_cta_refresh))
            .assertDoesNotExist()
    }

    // ---------- Suspended ----------

    @Test
    fun suspendedBannerShowsTheReason() {
        val reason = "Оплата не пройшла за 3 дні Grace Period"

        showBanner(status(SubscriptionStatus.SUSPENDED, message = reason))

        composeTestRule.onNodeWithText(string(R.string.subscription_suspended_reason, reason))
            .assertIsDisplayed()
    }

    @Test
    fun suspendedBannerOffersRecoveryWithoutRefresh() {
        showBanner(
            status(SubscriptionStatus.SUSPENDED),
            onUpdatePaymentMethod = {},
            onOpenSubscription = {},
            onRefresh = {}
        )

        composeTestRule.onNodeWithText(string(R.string.subscription_cta_update_payment))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.subscription_cta_open)).assertIsDisplayed()
        // Доступ призупинено — саме перечитування стану нічого не змінить.
        composeTestRule.onNodeWithText(string(R.string.subscription_cta_refresh))
            .assertDoesNotExist()
    }

    @Test
    fun bannerShowsNoActionsWhenTheScreenPassedNoHandlers() {
        showBanner(status(SubscriptionStatus.GRACE_PERIOD, gracePeriodEndsAt = "2026-03-08T10:00:00Z"))

        composeTestRule.onNodeWithText(string(R.string.subscription_cta_update_payment))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.subscription_cta_open)).assertDoesNotExist()
    }

    // ---------- Діалог заблокованої преміум-дії ----------

    @Test
    fun blockedPremiumDialogShowsTheReasonAndRecoveryCta() {
        val reason = "Доступ до HQ-сервера обмежено: оплата не пройшла за 3 дні"

        composeTestRule.setContent {
            PremiumBlockedDialog(reason = reason, onRecover = {}, onDismiss = {})
        }

        composeTestRule.onNodeWithText(string(R.string.subscription_suspended_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.subscription_suspended_reason, reason))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.subscription_cta_update_payment))
            .assertIsDisplayed()
    }

    @Test
    fun blockedPremiumDialogLeadsToThePaymentMethod() {
        var recoveries = 0

        composeTestRule.setContent {
            PremiumBlockedDialog(reason = "Причина", onRecover = { recoveries++ }, onDismiss = {})
        }

        composeTestRule.onNodeWithText(string(R.string.subscription_cta_update_payment)).performClick()

        assertEquals(1, recoveries)
    }

    @Test
    fun blockedPremiumDialogCanBeClosed() {
        var dismissals = 0

        composeTestRule.setContent {
            PremiumBlockedDialog(reason = "Причина", onRecover = {}, onDismiss = { dismissals++ })
        }

        composeTestRule.onNodeWithText(string(R.string.common_close)).performClick()

        assertEquals(1, dismissals)
    }
}
