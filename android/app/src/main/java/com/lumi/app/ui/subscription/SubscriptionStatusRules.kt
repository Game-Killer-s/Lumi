package com.lumi.app.ui.subscription

/**
 * Стани підписки, які повертає бекенд (ТЗ: PENDING, ACTIVE, GRACE_PERIOD,
 * SUSPENDED, CANCELLED). Для користувача без підписки статус — FREE.
 */
object SubscriptionStatus {
    const val FREE = "FREE"
    const val PENDING = "PENDING"
    const val ACTIVE = "ACTIVE"
    const val GRACE_PERIOD = "GRACE_PERIOD"
    const val SUSPENDED = "SUSPENDED"
    const val CANCELLED = "CANCELLED"
}

/** Що банер може запропонувати користувачу для відновлення доступу. */
enum class SubscriptionRecoveryAction {
    /** Оновити або змінити платіжну карту — причина оплата не проходить. */
    UPDATE_PAYMENT_METHOD,

    /** Перейти на екран підписки й оплатити тариф. */
    OPEN_SUBSCRIPTION,

    /** Повторно запитати стан (billing refresh) — банк міг уже списати кошти. */
    REFRESH_STATUS
}

/**
 * Правила статусів Grace Period і Suspended (Story 3) — чисті функції,
 * тому покриваються звичайними unit-тестами.
 */
object SubscriptionStatusRules {

    // Активна підписка — користувач нічого не втратив.
    fun isActive(status: String?): Boolean = status == SubscriptionStatus.ACTIVE

    // Grace Period: оплата не пройшла, але доступ ще діє 3 дні (ТЗ 4.2).
    fun isGrace(status: String?): Boolean = status == SubscriptionStatus.GRACE_PERIOD

    // Призупинена підписка. Скасована теж закриває преміум-доступ,
    // тому для плашки поводиться так само.
    fun isSuspended(status: String?): Boolean =
        status == SubscriptionStatus.SUSPENDED || status == SubscriptionStatus.CANCELLED

    // Банер показуємо кожному, хто вже має підписку: новому користувачу — ні.
    fun shouldShowBanner(status: String?): Boolean =
        status != null && status != SubscriptionStatus.FREE

    // Дату завершення Grace Period показуємо лише протягом самого Grace Period.
    fun showsGraceDate(status: String?): Boolean = isGrace(status)

    // Причину обмеження показуємо саме тоді, коли доступ закрито.
    fun showsSuspendedReason(status: String?): Boolean = isSuspended(status)

    /**
     * ТЗ: базове відтворення не блокуємо в Grace Period — воно працює
     * і без підписки. Обмежуємо лише призупинений доступ.
     */
    fun allowsBasicPlayback(status: String?): Boolean = !isSuspended(status)

    // Преміум-функції (HQ, Lossless, завантаження) у suspended не запускаємо.
    fun blocksPremiumActions(status: String?): Boolean = isSuspended(status)

    /**
     * Billing refresh підтвердив оплату: був Grace Period — стала активна
     * підписка. Саме на цей перехід показуємо підтвердження користувачу.
     */
    fun isRecovered(previous: String?, current: String?): Boolean =
        isGrace(previous) && isActive(current)

    /**
     * Дії відновлення доступу для банера.
     * Grace Period — просимо оновити картку, перейти до тарифів і освіжити стан.
     * Suspended — оновлення картки й перехід до тарифів.
     * Активна підписка — жодних дій, лише інформація про тариф.
     */
    fun recoveryActions(status: String?): List<SubscriptionRecoveryAction> = when {
        isGrace(status) -> listOf(
            SubscriptionRecoveryAction.UPDATE_PAYMENT_METHOD,
            SubscriptionRecoveryAction.OPEN_SUBSCRIPTION,
            SubscriptionRecoveryAction.REFRESH_STATUS
        )

        isSuspended(status) -> listOf(
            SubscriptionRecoveryAction.UPDATE_PAYMENT_METHOD,
            SubscriptionRecoveryAction.OPEN_SUBSCRIPTION
        )

        else -> emptyList()
    }

    // Банера з діями немає, коли підписка активна або її ще немає.
    fun needsRecovery(status: String?): Boolean = recoveryActions(status).isNotEmpty()
}
