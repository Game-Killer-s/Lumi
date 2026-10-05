package com.lumi.app.ui.paywall

import androidx.annotation.StringRes
import com.lumi.app.R
import com.lumi.app.data.model.AudioQuality
import com.lumi.app.data.model.Entitlement

/**
 * Функції, які відкриваються підпискою (Story 2).
 * Назва та причина блокування зберігаються як ресурси, тому пейвол локалізується.
 */
enum class PaywallFeature(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
) {
    HQ_QUALITY(R.string.paywall_feature_hq, R.string.paywall_locked_hq),
    LOSSLESS_QUALITY(R.string.paywall_feature_lossless, R.string.paywall_locked_lossless),
    DOWNLOAD(R.string.paywall_feature_download, R.string.paywall_locked_download);

    companion object {
        // HQ і Lossless — преміум-якість; для стандартної якості пейвол не потрібен.
        fun forQuality(quality: AudioQuality): PaywallFeature? = when (quality) {
            AudioQuality.HQ -> HQ_QUALITY
            AudioQuality.LOSSLESS -> LOSSLESS_QUALITY
            else -> null
        }
    }
}

/**
 * Правила видимості пейвола — чисті функції, тому легко покриваються тестами.
 */
object PaywallRules {

    // Функція заблокована, якщо поточний тариф її не дозволяє.
    fun isLocked(entitlement: Entitlement, feature: PaywallFeature): Boolean = when (feature) {
        PaywallFeature.HQ_QUALITY -> !entitlement.canUseHq
        PaywallFeature.LOSSLESS_QUALITY -> !entitlement.canUseLossless
        PaywallFeature.DOWNLOAD -> !entitlement.canDownload
    }

    // Пейвол показуємо лише для заблокованої функції: null — показувати нічого.
    fun paywallFor(entitlement: Entitlement, feature: PaywallFeature): PaywallFeature? =
        feature.takeIf { isLocked(entitlement, it) }

    // Якість, за яку треба платити; стандартна якість пейвол не викликає.
    fun paywallForQuality(entitlement: Entitlement, quality: AudioQuality): PaywallFeature? =
        PaywallFeature.forQuality(quality)?.let { paywallFor(entitlement, it) }
}
