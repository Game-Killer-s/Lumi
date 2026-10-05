package com.lumi.app.ui.paywall

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.model.AudioQuality
import com.lumi.app.data.model.Entitlement
import com.lumi.app.data.model.EntitlementPolicy
import com.lumi.app.data.repository.SubscriptionRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.rawText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Стан пейвола: яку функцію заблоковано та що показати під час переходу до тарифів.
 */
data class PaywallUiState(
    // null — пейвол приховано
    val feature: PaywallFeature? = null,
    val isLoading: Boolean = false,
    val error: UiText? = null
)

sealed class PaywallEvent {
    /** Функція вже доступна: екран продовжує дію, з якої відкривали пейвол. */
    data class Unlocked(val feature: PaywallFeature) : PaywallEvent()
}

/**
 * Пейвол як «ворота» для преміум-функцій (Story 2):
 * перевіряє тариф, тримає контекст дії та веде на екран підписки.
 */
class PaywallViewModel(
    private val repository: SubscriptionRepository = SubscriptionRepository()
) : ViewModel() {

    var uiState by mutableStateOf(PaywallUiState())
        private set

    private val _events = Channel<PaywallEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Контекст навігації: до якої дії повертаємось після успішного апгрейду.
    private var pendingFeature: PaywallFeature? = null

    /** Запит на преміум-функцію: пейвол показуємо лише для заблокованої. */
    fun request(feature: PaywallFeature) {
        val locked = PaywallRules.paywallFor(entitlement(), feature)

        if (locked == null) {
            // У користувача вже є доступ — пейвол не показуємо.
            _events.trySend(PaywallEvent.Unlocked(feature))
            return
        }

        pendingFeature = feature
        uiState = uiState.copy(feature = locked, error = null)
    }

    /** Вибір якості: стандартна доступна всім, HQ і Lossless — за підпискою. */
    fun requestQuality(quality: AudioQuality) {
        PaywallFeature.forQuality(quality)?.let { request(it) }
    }

    fun requestDownload() = request(PaywallFeature.DOWNLOAD)

    /** «Не зараз»: ховаємо пейвол і забуваємо дію, з якої його відкрили. */
    fun dismiss() {
        pendingFeature = null
        uiState = uiState.copy(feature = null, error = null)
    }

    /**
     * CTA «Перейти до тарифів»: спершу підтягуємо актуальний тариф
     * (тому є loading/error), потім відкриваємо екран підписки.
     */
    fun openSubscription(onNavigate: () -> Unit) {
        uiState = uiState.copy(isLoading = true, error = null)

        viewModelScope.launch {
            when (val result = repository.getStatus()) {
                is ApiResult.Success -> {
                    repository.saveEntitlement(EntitlementPolicy.fromStatus(result.data))
                    uiState = uiState.copy(isLoading = false, feature = null)
                    onNavigate()
                }
                // Помилка мережі не ламає пейвол: лишаємо його відкритим із текстом.
                is ApiResult.Error -> uiState = uiState.copy(
                    isLoading = false,
                    error = rawText(result.error.message)
                )
            }
        }
    }

    /**
     * Повернення до попередньої дії: викликається, коли користувач
     * повертається з екрана підписки після успішного апгрейду.
     */
    fun resumeAfterUpgrade(): PaywallFeature? {
        val feature = pendingFeature ?: return null

        // Тариф ще не активний — контекст лишаємо на наступний раз.
        val current = entitlement()
        if (PaywallRules.isLocked(current, feature)) return null

        pendingFeature = null
        uiState = uiState.copy(feature = null)
        return feature
    }

    // Можливості тарифу з сесії; якщо їх немає — вважаємо користувача без підписки.
    private fun entitlement(): Entitlement =
        repository.getEntitlement() ?: EntitlementPolicy.free
}
