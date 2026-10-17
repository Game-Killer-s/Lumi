package com.lumi.app.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.R
import com.lumi.app.data.repository.LyricsRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.uiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Текст пісні: рядки, стан завантаження та підсвічений рядок. */
data class LyricsUiState(
    val trackId: String = "",
    val lines: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val highlightedLine: Int = LyricsRules.NO_LINE
)

class LyricsViewModel(
    private val repository: LyricsRepository = LyricsRepository()
) : ViewModel() {

    var uiState by mutableStateOf(LyricsUiState())
        private set

    private var loadJob: Job? = null

    /**
     * Завантажує текст треку.
     *
     * Попередній запит скасовуємо: якщо користувач перемкнув трек, текст
     * попередньої пісні не повинен «приїхати» поверх нової.
     */
    fun load(trackId: String) {
        loadJob?.cancel()

        val id = trackId.trim()
        if (id.isEmpty()) {
            uiState = LyricsUiState()
            return
        }

        uiState = LyricsUiState(trackId = id, isLoading = true)

        loadJob = viewModelScope.launch {
            when (val result = repository.getLyrics(id)) {
                is ApiResult.Success -> {
                    val lines = LyricsRules.lines(result.data.lyrics)
                    uiState = uiState.copy(
                        lines = lines,
                        isLoading = false,
                        error = null,
                        highlightedLine = LyricsRules.highlightedLine(0f, lines.size)
                    )
                }

                is ApiResult.Error -> uiState = uiState.copy(
                    isLoading = false,
                    error = uiText(R.string.lyrics_error)
                )
            }
        }
    }

    fun retry() = load(uiState.trackId)

    /**
     * Підсвічування рядка під час відтворення: плеєр віддає частку прогресу,
     * тож ViewModel не залежить від аудіо-рушія і нічого в ньому не змінює.
     */
    fun onProgress(progressFraction: Float) {
        val highlighted = LyricsRules.highlightedLine(progressFraction, uiState.lines.size)
        if (highlighted != uiState.highlightedLine) {
            uiState = uiState.copy(highlightedLine = highlighted)
        }
    }
}
