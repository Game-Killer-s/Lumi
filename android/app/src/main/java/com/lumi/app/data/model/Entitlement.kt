package com.lumi.app.data.model

/**
 * Якість аудіопотоку: від найнижчої до Lossless (ТЗ 4.2).
 */
enum class AudioQuality {
    LOW,
    STANDARD,
    HQ,
    LOSSLESS;

    companion object {
        // Невідоме значення не ламає застосунок — вважаємо його стандартною якістю.
        fun parse(value: String?): AudioQuality =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STANDARD
    }
}

/**
 * Entitlement — можливості, які дозволені поточним тарифом користувача.
 */
data class Entitlement(
    val tier: String = EntitlementPolicy.TIER_FREE,
    val maxQuality: AudioQuality = AudioQuality.STANDARD,
    val canUseHq: Boolean = false,
    val canUseLossless: Boolean = false,
    val canDownload: Boolean = false
)

/**
 * Правила тарифу: перевірки перед вибором якості
 * та перед завантаженням треку на пристрій.
 */
object EntitlementPolicy {

    const val TIER_FREE = "FREE"
    const val TIER_PREMIUM = "PREMIUM"

    val free = Entitlement(
        tier = TIER_FREE,
        maxQuality = AudioQuality.STANDARD
    )

    val premium = Entitlement(
        tier = TIER_PREMIUM,
        maxQuality = AudioQuality.LOSSLESS,
        canUseHq = true,
        canUseLossless = true,
        canDownload = true
    )

    // Стани, за яких тариф діє: Grace Period дає 3 дні доступу (ТЗ).
    private val entitledStatuses = listOf("ACTIVE", "GRACE_PERIOD")

    fun forStatus(status: String?): Entitlement =
        if (status != null && entitledStatuses.contains(status)) premium else free

    // Беремо можливості з відповіді сервера, а без них — рахуємо за статусом.
    fun fromStatus(response: SubscriptionStatusResponse): Entitlement {
        val dto = response.entitlement ?: return forStatus(response.status)

        return Entitlement(
            tier = dto.tier,
            maxQuality = AudioQuality.parse(dto.maxQuality),
            canUseHq = dto.canUseHq,
            canUseLossless = dto.canUseLossless,
            canDownload = dto.canDownload
        )
    }

    // HQ і Lossless доступні лише за підпискою (ТЗ 4.2).
    fun canUseQuality(entitlement: Entitlement, quality: AudioQuality): Boolean {
        return when (quality) {
            AudioQuality.LOSSLESS -> entitlement.canUseLossless
            AudioQuality.HQ -> entitlement.canUseHq
            else -> true
        }
    }

    // Завантаження на пристрій — преміум-можливість.
    fun canDownload(entitlement: Entitlement): Boolean = entitlement.canDownload

    // Замість недоступної якості віддаємо найвищу дозволену тарифом.
    fun resolveQuality(entitlement: Entitlement, requested: AudioQuality): AudioQuality =
        if (canUseQuality(entitlement, requested)) requested else entitlement.maxQuality
}
