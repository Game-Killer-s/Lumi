package com.lumi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.model.StatsPeriod
import com.lumi.app.data.model.StatsSummary
import com.lumi.app.data.repository.StatisticsRepositoryProvider
import com.lumi.app.data.repository.StatisticsSource
import com.lumi.app.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatisticsStatus { LOADING, CONTENT, EMPTY, ERROR }

/**
 * Стан екрана статистики.
 *
 *  - [selectedPeriod] зберігається в UI state, тому переживає перехід на
 *    детальні екрани (ViewModel прив'язаний до запису back stack).
 *  - [status] = LOADING при відсутності даних обраного періоду. Вкладки
 *    періодів лишаються активними — це локальний індикатор усередині контенту,
 *    а не повноекранний.
 *  - [isRefreshing] = true, коли показано закешований результат, а свіжі
 *    дані ще завантажуються.
 *  - [errorMessage] заповнений при ERROR, а також коли оновлення закешованих
 *    даних не вдалося (контент тоді лишається на екрані).
 */
data class StatisticsUiState(
    val selectedPeriod: StatsPeriod = StatsPeriod.WEEK,
    val status: StatisticsStatus = StatisticsStatus.LOADING,
    val summary: StatsSummary? = null,
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false
)

class StatisticsViewModel(
    private val source: StatisticsSource = StatisticsRepositoryProvider.instance,
    initialPeriod: StatsPeriod = StatsPeriod.WEEK,
    /** Для тестів без Android Main-диспетчера; у застосунку null → viewModelScope. */
    private val scopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState(selectedPeriod = initialPeriod))
    val uiState: StateFlow<StatisticsUiState> = _uiState

    private var loadJob: Job? = null

    private val scope: CoroutineScope get() = scopeOverride ?: viewModelScope

    init {
        load(initialPeriod)
    }

    /** Перемикає період. Повторний вибір того ж періоду нічого не робить (крім стану помилки). */
    fun selectPeriod(period: StatsPeriod) {
        val current = _uiState.value
        if (period == current.selectedPeriod && current.status != StatisticsStatus.ERROR) return
        load(period)
    }

    fun retry() = load(_uiState.value.selectedPeriod)

    fun refresh() = load(_uiState.value.selectedPeriod)

    private fun load(period: StatsPeriod) {
        // Попередній запит (інший період) більше не потрібен.
        loadJob?.cancel()

        val cached = source.cached(period)
        _uiState.value = if (cached != null) {
            StatisticsUiState(
                selectedPeriod = period,
                status = cached.toStatus(),
                summary = cached,
                isRefreshing = true
            )
        } else {
            StatisticsUiState(selectedPeriod = period, status = StatisticsStatus.LOADING)
        }

        loadJob = scope.launch {
            val result = source.load(period)
            // Відповідь для періоду, який вже не обраний, ігноруємо (швидке перемикання).
            if (_uiState.value.selectedPeriod != period) return@launch

            when (result) {
                is ApiResult.Success -> _uiState.value = StatisticsUiState(
                    selectedPeriod = period,
                    status = result.data.toStatus(),
                    summary = result.data
                )
                is ApiResult.Error -> _uiState.update { state ->
                    if (state.summary != null) {
                        // Лишаємо закешований контент, лише повідомляємо про невдале оновлення.
                        state.copy(isRefreshing = false, errorMessage = result.error.message)
                    } else {
                        StatisticsUiState(
                            selectedPeriod = period,
                            status = StatisticsStatus.ERROR,
                            errorMessage = result.error.message
                        )
                    }
                }
            }
        }
    }

    private fun StatsSummary.toStatus(): StatisticsStatus =
        if (isEmpty) StatisticsStatus.EMPTY else StatisticsStatus.CONTENT
}
