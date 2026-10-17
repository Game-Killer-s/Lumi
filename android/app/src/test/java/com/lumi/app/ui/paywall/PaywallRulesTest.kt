package com.lumi.app.ui.paywall

import com.lumi.app.data.model.AudioQuality
import com.lumi.app.data.model.EntitlementPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила видимості пейвола: без підписки HQ, Lossless і завантаження заблоковані (Story 2).
 */
class PaywallRulesTest {

    // ---------- Що заблоковано ----------

    @Test
    fun `free user has hq lossless and download locked`() {
        assertTrue(PaywallRules.isLocked(EntitlementPolicy.free, PaywallFeature.HQ_QUALITY))
        assertTrue(PaywallRules.isLocked(EntitlementPolicy.free, PaywallFeature.LOSSLESS_QUALITY))
        assertTrue(PaywallRules.isLocked(EntitlementPolicy.free, PaywallFeature.DOWNLOAD))
    }

    @Test
    fun `premium user has nothing locked`() {
        assertFalse(PaywallRules.isLocked(EntitlementPolicy.premium, PaywallFeature.HQ_QUALITY))
        assertFalse(PaywallRules.isLocked(EntitlementPolicy.premium, PaywallFeature.LOSSLESS_QUALITY))
        assertFalse(PaywallRules.isLocked(EntitlementPolicy.premium, PaywallFeature.DOWNLOAD))
    }

    // Grace Period працює як активна підписка — пейвола для неї теж немає.
    @Test
    fun `grace period user has nothing locked`() {
        val grace = EntitlementPolicy.forStatus("GRACE_PERIOD")

        assertNull(PaywallRules.paywallFor(grace, PaywallFeature.HQ_QUALITY))
        assertNull(PaywallRules.paywallFor(grace, PaywallFeature.DOWNLOAD))
    }

    // ---------- Що показуємо ----------

    @Test
    fun `paywall is returned for a locked feature`() {
        assertEquals(
            PaywallFeature.DOWNLOAD,
            PaywallRules.paywallFor(EntitlementPolicy.free, PaywallFeature.DOWNLOAD)
        )
    }

    @Test
    fun `paywall is hidden for an entitled user`() {
        assertNull(PaywallRules.paywallFor(EntitlementPolicy.premium, PaywallFeature.DOWNLOAD))
    }

    // ---------- Вибір якості ----------

    @Test
    fun `hq and lossless open the paywall for a free user`() {
        assertEquals(
            PaywallFeature.HQ_QUALITY,
            PaywallRules.paywallForQuality(EntitlementPolicy.free, AudioQuality.HQ)
        )
        assertEquals(
            PaywallFeature.LOSSLESS_QUALITY,
            PaywallRules.paywallForQuality(EntitlementPolicy.free, AudioQuality.LOSSLESS)
        )
    }

    @Test
    fun `premium quality opens no paywall for a premium user`() {
        assertNull(PaywallRules.paywallForQuality(EntitlementPolicy.premium, AudioQuality.HQ))
        assertNull(PaywallRules.paywallForQuality(EntitlementPolicy.premium, AudioQuality.LOSSLESS))
    }

    @Test
    fun `standard quality never opens the paywall`() {
        assertNull(PaywallRules.paywallForQuality(EntitlementPolicy.free, AudioQuality.STANDARD))
        assertNull(PaywallRules.paywallForQuality(EntitlementPolicy.free, AudioQuality.LOW))
    }

    // ---------- Зв'язок якості та функції ----------

    @Test
    fun `feature matches the chosen quality`() {
        assertEquals(PaywallFeature.HQ_QUALITY, PaywallFeature.forQuality(AudioQuality.HQ))
        assertEquals(PaywallFeature.LOSSLESS_QUALITY, PaywallFeature.forQuality(AudioQuality.LOSSLESS))
        assertNull(PaywallFeature.forQuality(AudioQuality.STANDARD))
    }
}
