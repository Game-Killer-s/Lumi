package com.lumi.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Перевірки тарифу: без підписки доступна лише стандартна якість (ТЗ 4.2).
 */
class EntitlementPolicyTest {

    // ---------- Якість із сервера ----------

    @Test
    fun `quality text is read from the server value`() {
        assertEquals(AudioQuality.HQ, AudioQuality.parse("HQ"))
        assertEquals(AudioQuality.LOSSLESS, AudioQuality.parse("lossless"))
    }

    @Test
    fun `unknown quality falls back to standard`() {
        assertEquals(AudioQuality.STANDARD, AudioQuality.parse("NONE"))
        assertEquals(AudioQuality.STANDARD, AudioQuality.parse(null))
    }

    // ---------- Статус підписки ----------

    @Test
    fun `active subscription gives premium capabilities`() {
        assertEquals(EntitlementPolicy.premium, EntitlementPolicy.forStatus("ACTIVE"))
    }

    @Test
    fun `grace period keeps premium capabilities`() {
        assertEquals(EntitlementPolicy.premium, EntitlementPolicy.forStatus("GRACE_PERIOD"))
    }

    @Test
    fun `suspended subscription falls back to free`() {
        assertEquals(EntitlementPolicy.free, EntitlementPolicy.forStatus("SUSPENDED"))
        assertEquals(EntitlementPolicy.free, EntitlementPolicy.forStatus(null))
    }

    // ---------- Відповідь API ----------

    @Test
    fun `entitlement is taken from the api response`() {
        val response = SubscriptionStatusResponse(
            hasSubscription = true,
            status = "ACTIVE",
            entitlement = EntitlementResponse(
                tier = "PREMIUM",
                maxQuality = "HQ",
                canUseHq = true,
                canDownload = true
            )
        )

        val entitlement = EntitlementPolicy.fromStatus(response)

        assertEquals(AudioQuality.HQ, entitlement.maxQuality)
        assertTrue(entitlement.canUseHq)
        assertTrue(entitlement.canDownload)
        assertFalse(entitlement.canUseLossless)
    }

    @Test
    fun `entitlement is calculated when the api sends none`() {
        val response = SubscriptionStatusResponse(hasSubscription = false, status = "FREE")

        assertEquals(EntitlementPolicy.free, EntitlementPolicy.fromStatus(response))
    }

    // ---------- Перевірки перед відтворенням ----------

    @Test
    fun `free user cannot pick hq or lossless`() {
        assertFalse(EntitlementPolicy.canUseQuality(EntitlementPolicy.free, AudioQuality.HQ))
        assertFalse(EntitlementPolicy.canUseQuality(EntitlementPolicy.free, AudioQuality.LOSSLESS))
        assertTrue(EntitlementPolicy.canUseQuality(EntitlementPolicy.free, AudioQuality.STANDARD))
    }

    @Test
    fun `premium user can pick hq and lossless`() {
        assertTrue(EntitlementPolicy.canUseQuality(EntitlementPolicy.premium, AudioQuality.HQ))
        assertTrue(EntitlementPolicy.canUseQuality(EntitlementPolicy.premium, AudioQuality.LOSSLESS))
    }

    @Test
    fun `hq request of a free user falls back to standard`() {
        assertEquals(
            AudioQuality.STANDARD,
            EntitlementPolicy.resolveQuality(EntitlementPolicy.free, AudioQuality.HQ)
        )
    }

    @Test
    fun `lossless request of a premium user stays lossless`() {
        assertEquals(
            AudioQuality.LOSSLESS,
            EntitlementPolicy.resolveQuality(EntitlementPolicy.premium, AudioQuality.LOSSLESS)
        )
    }

    // ---------- Завантаження ----------

    @Test
    fun `downloads are available for premium only`() {
        assertFalse(EntitlementPolicy.canDownload(EntitlementPolicy.free))
        assertTrue(EntitlementPolicy.canDownload(EntitlementPolicy.premium))
    }
}
