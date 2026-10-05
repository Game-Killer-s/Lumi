package com.lumi.app.ui.subscription

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.model.AddPaymentMethodRequest
import com.lumi.app.data.model.AudioQuality
import com.lumi.app.data.model.Entitlement
import com.lumi.app.data.model.EntitlementPolicy
import com.lumi.app.data.model.CheckoutRequest
import com.lumi.app.data.model.PaymentHistoryItem
import com.lumi.app.data.model.PaymentMethod
import com.lumi.app.data.model.PlayAccessResponse
import com.lumi.app.data.model.SubscriptionPlan
import com.lumi.app.data.model.SubscriptionStatusResponse
import com.lumi.app.data.model.UpdatePaymentMethodRequest
import com.lumi.app.data.repository.SubscriptionRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.R
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.rawText
import com.lumi.app.ui.i18n.uiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Стан екранів підписки: тарифи, статус і платіжні дані.
 */
data class SubscriptionUiState(
    val plans: List<SubscriptionPlan> = emptyList(),
    val status: SubscriptionStatusResponse? = null,
    // Можливості поточного тарифу: за ними перевіряємо якість треку.
    val entitlement: Entitlement = EntitlementPolicy.free,
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val payments: List<PaymentHistoryItem> = emptyList(),
    val selectedPlanId: String? = null,
    val cardNumber: String = "",
    val holderName: String = "",
    val expMonth: String = "",
    val expYear: String = "",
    val cvc: String = "",
    val useGooglePay: Boolean = false,
    val showCardForm: Boolean = false,
    val playAccess: PlayAccessResponse? = null,
    val isLoading: Boolean = false,
    // Story 3: преміум-дію заблоковано (suspended) — показуємо причину й CTA.
    val blockedPremium: Boolean = false
)

sealed class SubscriptionEvent {
    /** Повідомлення як UiText: Snackbar показується обраною мовою. */
    data class Message(val text: UiText) : SubscriptionEvent()
    data object Paid : SubscriptionEvent()
    data object Cancelled : SubscriptionEvent()

    /** Billing refresh підтвердив оплату — Grace Period закрито (Story 3). */
    data object Recovered : SubscriptionEvent()
}

/**
 * ViewModel тарифів, оплати та стану підписки.
 */
class SubscriptionViewModel(
    private val repository: SubscriptionRepository = SubscriptionRepository()
) : ViewModel() {

    var uiState by mutableStateOf(SubscriptionUiState())
        private set

    private val _events = Channel<SubscriptionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)

            when (val plans = repository.getPlans()) {
                is ApiResult.Success -> uiState = uiState.copy(
                    plans = plans.data,
                    selectedPlanId = uiState.selectedPlanId ?: plans.data.firstOrNull()?.id
                )
                is ApiResult.Error -> _events.trySend(SubscriptionEvent.Message(rawText(plans.error.message)))
            }

            uiState = uiState.copy(isLoading = false)

            loadStatus()
            loadPaymentMethods()
            loadPayments()
        }
    }

    fun loadStatus() {
        viewModelScope.launch {
            fetchStatus()
        }
    }

    /**
     * Billing refresh (Story 3): після спроби списання перечитуємо стан
     * та історію оплат. Якщо Grace Period закрився — плашка стає активною,
     * а користувач бачить підтвердження, що преміум-доступ повернувся.
     */
    fun refreshBilling() {
        val previous = uiState.status?.status

        viewModelScope.launch {
            val refreshed = fetchStatus() ?: return@launch

            loadPayments()

            if (SubscriptionStatusRules.isRecovered(previous, refreshed.status)) {
                _events.trySend(SubscriptionEvent.Recovered)
            }
        }
    }

    /**
     * CTA «Оновити спосіб оплати»: відкриваємо форму картки,
     * щоб користувач замінив картку, з якої не проходить оплата.
     */
    fun openPaymentMethodForm() {
        uiState = uiState.copy(showCardForm = true)
    }

    /**
     * Чи можна запускати преміум-функцію (Story 3).
     * У suspended доступ закрито: дію не запускаємо, а показуємо причину.
     */
    fun checkPremiumAction(): Boolean {
        if (!SubscriptionStatusRules.blocksPremiumActions(uiState.status?.status)) {
            return true
        }

        uiState = uiState.copy(blockedPremium = true)
        return false
    }

    fun dismissBlockedPremium() {
        uiState = uiState.copy(blockedPremium = false)
    }

    // Спільна частина: застосовуємо стан із сервера і зберігаємо тариф у сесії.
    private suspend fun fetchStatus(): SubscriptionStatusResponse? {
        return when (val result = repository.getStatus()) {
            is ApiResult.Success -> {
                // Після оплати чи скасування можливості тарифу можуть змінитися.
                val entitlement = EntitlementPolicy.fromStatus(result.data)
                repository.saveEntitlement(entitlement)
                uiState = uiState.copy(
                    status = result.data,
                    entitlement = entitlement,
                    // Доступ повернувся — діалог із причиною більше не потрібен.
                    blockedPremium = if (SubscriptionStatusRules.isActive(result.data.status)) {
                        false
                    } else {
                        uiState.blockedPremium
                    }
                )
                result.data
            }

            is ApiResult.Error -> {
                _events.trySend(SubscriptionEvent.Message(rawText(result.error.message)))
                null
            }
        }
    }

    fun loadPaymentMethods() {
        viewModelScope.launch {
            when (val result = repository.getPaymentMethods()) {
                is ApiResult.Success -> uiState = uiState.copy(paymentMethods = result.data)
                is ApiResult.Error -> _events.trySend(SubscriptionEvent.Message(rawText(result.error.message)))
            }
        }
    }

    fun loadPayments() {
        viewModelScope.launch {
            when (val result = repository.getPayments()) {
                is ApiResult.Success -> uiState = uiState.copy(payments = result.data)
                is ApiResult.Error -> _events.trySend(SubscriptionEvent.Message(rawText(result.error.message)))
            }
        }
    }

    // Кнопка «Оновити / Змінити платіжну карту» показує форму картки.
    fun toggleCardForm() {
        uiState = uiState.copy(showCardForm = !uiState.showCardForm)
    }

    fun selectPlan(planId: String) {
        uiState = uiState.copy(selectedPlanId = planId)
    }

    // ---------- Поля картки ----------

    fun onCardNumberChange(value: String) {
        uiState = uiState.copy(cardNumber = formatCardNumber(value))
    }

    fun onHolderNameChange(value: String) {
        uiState = uiState.copy(holderName = value.uppercase().take(60))
    }

    fun onExpMonthChange(value: String) {
        uiState = uiState.copy(expMonth = value.filter { it.isDigit() }.take(2))
    }

    fun onExpYearChange(value: String) {
        uiState = uiState.copy(expYear = value.filter { it.isDigit() }.take(4))
    }

    fun onCvcChange(value: String) {
        uiState = uiState.copy(cvc = value.filter { it.isDigit() }.take(4))
    }

    fun setUseGooglePay(value: Boolean) {
        uiState = uiState.copy(useGooglePay = value)
    }

    // ---------- Оплата ----------

    fun pay() {
        val planId = uiState.selectedPlanId

        if (planId.isNullOrEmpty()) {
            _events.trySend(SubscriptionEvent.Message(uiText(R.string.error_choose_plan)))
            return
        }

        if (uiState.useGooglePay) {
            // Справжній токен прийде з Google Pay SDK, поки що демо-значення.
            checkout(
                CheckoutRequest(
                    planId = planId,
                    provider = "GOOGLE_PAY",
                    paymentToken = "gpay_demo_token"
                )
            )
            return
        }

        val error = validateCard()

        if (error != null) {
            _events.trySend(SubscriptionEvent.Message(error))
            return
        }

        checkout(
            CheckoutRequest(
                planId = planId,
                provider = "CARD",
                cardNumber = digitsOf(uiState.cardNumber),
                holderName = uiState.holderName,
                expMonth = uiState.expMonth.toIntOrNull(),
                expYear = uiState.expYear.toIntOrNull(),
                cvc = uiState.cvc
            )
        )
    }

    fun saveCard() {
        val error = validateCard()

        if (error != null) {
            _events.trySend(SubscriptionEvent.Message(error))
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)

            val result = repository.addPaymentMethod(
                AddPaymentMethodRequest(
                    cardNumber = digitsOf(uiState.cardNumber),
                    holderName = uiState.holderName,
                    expMonth = uiState.expMonth.toIntOrNull() ?: 0,
                    expYear = uiState.expYear.toIntOrNull() ?: 0,
                    cvc = uiState.cvc
                )
            )

            when (result) {
                is ApiResult.Success -> {
                    _events.trySend(SubscriptionEvent.Message(uiText(R.string.subscription_card_saved)))
                    clearCardForm()
                    loadPaymentMethods()
                }
                is ApiResult.Error -> _events.trySend(SubscriptionEvent.Message(rawText(result.error.message)))
            }

            uiState = uiState.copy(isLoading = false)
        }
    }

    fun makeDefault(method: PaymentMethod) {
        viewModelScope.launch {
            val result = repository.updatePaymentMethod(
                method.id,
                UpdatePaymentMethodRequest(isDefault = true)
            )

            when (result) {
                is ApiResult.Success -> loadPaymentMethods()
                is ApiResult.Error -> _events.trySend(SubscriptionEvent.Message(rawText(result.error.message)))
            }
        }
    }

    fun removeMethod(method: PaymentMethod) {
        viewModelScope.launch {
            when (val result = repository.removePaymentMethod(method.id)) {
                is ApiResult.Success -> loadPaymentMethods()
                is ApiResult.Error -> _events.trySend(SubscriptionEvent.Message(rawText(result.error.message)))
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)

            when (val result = repository.cancel()) {
                is ApiResult.Success -> {
                    _events.trySend(SubscriptionEvent.Cancelled)
                    loadStatus()
                }
                is ApiResult.Error -> _events.trySend(SubscriptionEvent.Message(rawText(result.error.message)))
            }

            uiState = uiState.copy(isLoading = false)
        }
    }

    // ---------- Перевірка доступу до треку ----------

    fun checkPlayAccess() {
        viewModelScope.launch {
            when (val result = repository.checkPlayAccess()) {
                is ApiResult.Success -> {
                    val access = result.data

                    // Якщо в сесії ще немає тарифу — рахуємо його за статусом треку.
                    val entitlement = repository.getEntitlement()
                        ?: EntitlementPolicy.forStatus(access.status)

                    // Недоступну за тарифом якість замінюємо на дозволену.
                    val quality = EntitlementPolicy.resolveQuality(
                        entitlement,
                        AudioQuality.parse(access.quality)
                    )

                    uiState = uiState.copy(
                        playAccess = access.copy(quality = quality.name),
                        entitlement = entitlement
                    )
                }
                is ApiResult.Error -> _events.trySend(SubscriptionEvent.Message(rawText(result.error.message)))
            }
        }
    }

    fun dismissPlayAccess() {
        uiState = uiState.copy(playAccess = null)
    }

    // ---------- Дрібні помічники ----------

    private fun checkout(request: CheckoutRequest) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)

            when (val result = repository.checkout(request)) {
                is ApiResult.Success -> {
                    _events.trySend(SubscriptionEvent.Paid)
                    clearCardForm()
                    loadStatus()
                    loadPaymentMethods()
                    loadPayments()
                }
                is ApiResult.Error -> {
                    _events.trySend(SubscriptionEvent.Message(rawText(result.error.message)))
                    // Оплата могла не пройти — показуємо новий стан підписки.
                    loadStatus()
                }
            }

            uiState = uiState.copy(isLoading = false)
        }
    }

    // Перевірка полів картки: текст помилки локалізується в UI (UiText).
    private fun validateCard(): UiText? {
        if (digitsOf(uiState.cardNumber).length < 13) {
            return uiText(R.string.error_card_number)
        }

        if (uiState.holderName.isBlank()) {
            return uiText(R.string.error_card_holder)
        }

        val month = uiState.expMonth.toIntOrNull()

        if (month == null || month !in 1..12) {
            return uiText(R.string.error_card_month)
        }

        val year = uiState.expYear.toIntOrNull()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        if (year == null || year < currentYear) {
            // Рік підставляється як аргумент: «Введіть рік, напр. 2028» / «Enter the year, e.g. 2028».
            return uiText(R.string.error_card_year, currentYear + 2)
        }

        if (uiState.cvc.length < 3) {
            return uiText(R.string.error_card_cvc)
        }

        return null
    }

    private fun clearCardForm() {
        uiState = uiState.copy(
            cardNumber = "",
            holderName = "",
            expMonth = "",
            expYear = "",
            cvc = ""
        )
    }

    private fun digitsOf(value: String): String = value.filter { it.isDigit() }

    // Номер картки показуємо групами по 4 цифри.
    private fun formatCardNumber(value: String): String {
        val digits = digitsOf(value).take(19)

        return digits.chunked(4).joinToString(" ")
    }
}
