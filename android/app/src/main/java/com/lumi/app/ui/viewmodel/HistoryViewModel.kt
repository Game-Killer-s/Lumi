package com.lumi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.local.HistoryEntry
import com.lumi.app.data.local.LibraryLocalStore
import com.lumi.app.data.repository.HistoryRepository
import com.lumi.app.ui.history.HistoryRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<HistoryEntry> = emptyList()
)

class HistoryViewModel(
    private val repository: HistoryRepository = HistoryRepository()
) : ViewModel() {

    /** Фіксуємо «зараз» на момент відкриття екрана, щоб групування не «стрибало». */
    private val now = System.currentTimeMillis()

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    init {
        // Гарантуємо, що сховище завантажене, навіть якщо екран відкрили
        // глибоким посиланням до активації сесії.
        LibraryLocalStore.snapshot()
        viewModelScope.launch {
            LibraryLocalStore.state.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = HistoryRules.visible(snapshot.history, now)
                    )
                }
            }
        }
    }

    fun clearHistory() {
        repository.clear()
    }
}
