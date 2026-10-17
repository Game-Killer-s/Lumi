package com.lumi.app.ui.paywall

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumi.app.R
import com.lumi.app.data.model.Entitlement
import com.lumi.app.data.model.EntitlementPolicy
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.uiText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-перевірки правил видимості пейвола (Story 2).
 * Запуск на пристрої або емуляторі: gradlew connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class PaywallVisibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    // Пейвол показуємо через ту саму точку входу, що й у застосунку.
    private fun showPaywall(
        entitlement: Entitlement,
        feature: PaywallFeature?,
        isLoading: Boolean = false,
        error: UiText? = null,
        onSubscribe: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            PaywallGate(
                entitlement = entitlement,
                feature = feature,
                isLoading = isLoading,
                error = error,
                onSubscribe = onSubscribe,
                onDismiss = onDismiss
            )
        }
    }

    // ---------- Видимість ----------

    @Test
    fun freeUserSeesPaywall() {
        showPaywall(EntitlementPolicy.free, PaywallFeature.HQ_QUALITY)

        composeTestRule.onNodeWithText(string(R.string.paywall_feature_hq)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.paywall_cta)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.paywall_not_now)).assertIsDisplayed()
    }

    @Test
    fun entitledUserDoesNotSeePaywall() {
        showPaywall(EntitlementPolicy.premium, PaywallFeature.HQ_QUALITY)

        composeTestRule.onNodeWithText(string(R.string.paywall_feature_hq)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.paywall_cta)).assertDoesNotExist()
    }

    // Grace Period дає доступ, тому пейвол не показуємо.
    @Test
    fun gracePeriodUserDoesNotSeePaywall() {
        showPaywall(EntitlementPolicy.forStatus("GRACE_PERIOD"), PaywallFeature.DOWNLOAD)

        composeTestRule.onNodeWithText(string(R.string.paywall_feature_download)).assertDoesNotExist()
    }

    @Test
    fun paywallIsHiddenWhenNoFeatureIsRequested() {
        showPaywall(EntitlementPolicy.free, null)

        composeTestRule.onNodeWithText(string(R.string.paywall_cta)).assertDoesNotExist()
    }

    @Test
    fun paywallIsHiddenWhenTheEntitlementUnlocksTheFeature() {
        // Функцію обрано, але тариф її дозволяє — пейвола немає.
        showPaywall(EntitlementPolicy.premium, PaywallFeature.DOWNLOAD)

        composeTestRule.onNodeWithText(string(R.string.paywall_feature_download)).assertDoesNotExist()
    }

    // ---------- Точки входу HQ / Lossless / download ----------

    @Test
    fun losslessEntryPointShowsItsOwnPaywall() {
        showPaywall(EntitlementPolicy.free, PaywallFeature.LOSSLESS_QUALITY)

        composeTestRule.onNodeWithText(string(R.string.paywall_feature_lossless)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.paywall_locked_lossless)).assertIsDisplayed()
    }

    @Test
    fun downloadEntryPointShowsItsOwnPaywall() {
        showPaywall(EntitlementPolicy.free, PaywallFeature.DOWNLOAD)

        composeTestRule.onNodeWithText(string(R.string.paywall_feature_download)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.paywall_locked_download)).assertIsDisplayed()
    }

    @Test
    fun paywallListsAllPremiumBenefits() {
        showPaywall(EntitlementPolicy.free, PaywallFeature.HQ_QUALITY)

        PremiumBenefits.forEach { benefit ->
            composeTestRule.onNodeWithText(string(benefit.titleRes)).assertExists()
            composeTestRule.onNodeWithText(string(benefit.subtitleRes)).assertExists()
        }
    }

    // ---------- Дії ----------

    @Test
    fun ctaLeadsToTheSubscriptionScreen() {
        var navigations = 0
        showPaywall(EntitlementPolicy.free, PaywallFeature.HQ_QUALITY, onSubscribe = { navigations++ })

        composeTestRule.onNodeWithText(string(R.string.paywall_cta)).performClick()

        assertEquals(1, navigations)
    }

    @Test
    fun notNowClosesThePaywall() {
        var dismissals = 0
        showPaywall(EntitlementPolicy.free, PaywallFeature.DOWNLOAD, onDismiss = { dismissals++ })

        composeTestRule.onNodeWithText(string(R.string.paywall_not_now)).performClick()

        assertEquals(1, dismissals)
    }

    // ---------- Стани переходу до тарифів ----------

    @Test
    fun loadingStateIsShownWhileTheSubscriptionOpens() {
        showPaywall(EntitlementPolicy.free, PaywallFeature.HQ_QUALITY, isLoading = true)

        composeTestRule.onNodeWithContentDescription(string(R.string.common_loading)).assertExists()
    }

    @Test
    fun errorStateIsShownWhenTheSubscriptionCannotBeOpened() {
        showPaywall(
            EntitlementPolicy.free,
            PaywallFeature.HQ_QUALITY,
            error = uiText(R.string.paywall_navigation_error)
        )

        composeTestRule.onNodeWithText(string(R.string.paywall_navigation_error))
            .assertIsDisplayed()
    }
}
