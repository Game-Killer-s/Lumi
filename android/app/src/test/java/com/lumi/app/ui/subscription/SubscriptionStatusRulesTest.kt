package com.lumi.app.ui.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила статусів Grace Period і Suspended (Story 3):
 * чи показувати банер, чи блокувати преміум-дії та що саме пропонувати.
 */
class SubscriptionStatusRulesTest {

    // ---------- Коли банер взагалі показуємо ----------

    @Test
    fun `banner is visible for every subscription state`() {
        assertTrue(SubscriptionStatusRules.shouldShowBanner(SubscriptionStatus.ACTIVE))
        assertTrue(SubscriptionStatusRules.shouldShowBanner(SubscriptionStatus.GRACE_PERIOD))
        assertTrue(SubscriptionStatusRules.shouldShowBanner(SubscriptionStatus.SUSPENDED))
        assertTrue(SubscriptionStatusRules.shouldShowBanner(SubscriptionStatus.CANCELLED))
    }

    @Test
    fun `banner is hidden when there is no subscription`() {
        assertFalse(SubscriptionStatusRules.shouldShowBanner(SubscriptionStatus.FREE))
        assertFalse(SubscriptionStatusRules.shouldShowBanner(null))
    }

    // ---------- Grace Period: дата завершення ----------

    @Test
    fun `grace period shows its end date`() {
        assertTrue(SubscriptionStatusRules.showsGraceDate(SubscriptionStatus.GRACE_PERIOD))
    }

    @Test
    fun `grace date disappears once the subscription is active again`() {
        // Billing refresh повернув активну підписку — дата Grace Period більше не потрібна.
        assertTrue(SubscriptionStatusRules.isRecovered(SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.ACTIVE))
        assertFalse(SubscriptionStatusRules.showsGraceDate(SubscriptionStatus.ACTIVE))
        assertFalse(SubscriptionStatusRules.showsGraceDate(SubscriptionStatus.SUSPENDED))
        assertFalse(SubscriptionStatusRules.showsGraceDate(null))
    }

    // ---------- Suspended: причина обмеження ----------

    @Test
    fun `suspended shows the reason of the restriction`() {
        assertTrue(SubscriptionStatusRules.showsSuspendedReason(SubscriptionStatus.SUSPENDED))
        // Скасована підписка теж закриває доступ — причина теж доречна.
        assertTrue(SubscriptionStatusRules.showsSuspendedReason(SubscriptionStatus.CANCELLED))
    }

    @Test
    fun `grace period does not look like a suspended account`() {
        assertFalse(SubscriptionStatusRules.showsSuspendedReason(SubscriptionStatus.GRACE_PERIOD))
        assertFalse(SubscriptionStatusRules.showsSuspendedReason(SubscriptionStatus.ACTIVE))
        assertFalse(SubscriptionStatusRules.isSuspended(SubscriptionStatus.GRACE_PERIOD))
    }

    // ---------- Що дозволено, а що блоковано ----------

    @Test
    fun `grace period keeps basic playback allowed`() {
        // ТЗ: Grace Period дає 3 дні доступу — базове відтворення не блокуємо.
        assertTrue(SubscriptionStatusRules.allowsBasicPlayback(SubscriptionStatus.GRACE_PERIOD))
        assertTrue(SubscriptionStatusRules.allowsBasicPlayback(SubscriptionStatus.ACTIVE))
        assertTrue(SubscriptionStatusRules.allowsBasicPlayback(SubscriptionStatus.FREE))
    }

    @Test
    fun `suspended blocks basic playback`() {
        assertFalse(SubscriptionStatusRules.allowsBasicPlayback(SubscriptionStatus.SUSPENDED))
        assertFalse(SubscriptionStatusRules.allowsBasicPlayback(SubscriptionStatus.CANCELLED))
    }

    @Test
    fun `premium actions are blocked only while suspended`() {
        assertTrue(SubscriptionStatusRules.blocksPremiumActions(SubscriptionStatus.SUSPENDED))
        assertTrue(SubscriptionStatusRules.blocksPremiumActions(SubscriptionStatus.CANCELLED))

        // Без підписки преміум теж закрито, але цим керує пейвол (Story 2).
        assertFalse(SubscriptionStatusRules.blocksPremiumActions(SubscriptionStatus.FREE))
        assertFalse(SubscriptionStatusRules.blocksPremiumActions(SubscriptionStatus.GRACE_PERIOD))
        assertFalse(SubscriptionStatusRules.blocksPremiumActions(SubscriptionStatus.ACTIVE))
    }

    // ---------- Відновлення доступу ----------

    @Test
    fun `recovery is the transition from grace period to active`() {
        assertTrue(SubscriptionStatusRules.isRecovered(SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.ACTIVE))
        assertFalse(SubscriptionStatusRules.isRecovered(SubscriptionStatus.ACTIVE, SubscriptionStatus.GRACE_PERIOD))
        assertFalse(SubscriptionStatusRules.isRecovered(SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.SUSPENDED))
        assertFalse(SubscriptionStatusRules.isRecovered(SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.GRACE_PERIOD))
        assertFalse(SubscriptionStatusRules.isRecovered(null, SubscriptionStatus.ACTIVE))
    }

    @Test
    fun `recovery actions cover both grace period and suspension`() {
        assertEquals(
            listOf(
                SubscriptionRecoveryAction.UPDATE_PAYMENT_METHOD,
                SubscriptionRecoveryAction.OPEN_SUBSCRIPTION,
                SubscriptionRecoveryAction.REFRESH_STATUS
            ),
            SubscriptionStatusRules.recoveryActions(SubscriptionStatus.GRACE_PERIOD)
        )

        assertEquals(
            listOf(
                SubscriptionRecoveryAction.UPDATE_PAYMENT_METHOD,
                SubscriptionRecoveryAction.OPEN_SUBSCRIPTION
            ),
            SubscriptionStatusRules.recoveryActions(SubscriptionStatus.SUSPENDED)
        )
    }

    @Test
    fun `active subscription offers no recovery actions`() {
        assertTrue(SubscriptionStatusRules.recoveryActions(SubscriptionStatus.ACTIVE).isEmpty())
        assertTrue(SubscriptionStatusRules.recoveryActions(null).isEmpty())
        assertFalse(SubscriptionStatusRules.needsRecovery(SubscriptionStatus.ACTIVE))
        assertTrue(SubscriptionStatusRules.needsRecovery(SubscriptionStatus.GRACE_PERIOD))
        assertTrue(SubscriptionStatusRules.needsRecovery(SubscriptionStatus.CANCELLED))
    }
}
