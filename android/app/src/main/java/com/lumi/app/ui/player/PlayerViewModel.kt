package com.lumi.app.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.R
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.CommentsRepository
import com.lumi.app.data.repository.TrackRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.uiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Стан екрана плеєра: сам трек, лічильник коментарів під ним і позиція
 * відтворення (поки плеєр не підключено до сервісу — нуль).
 *
 * ViewModel свідомо не торкається відтворення: поки користувач читає текст
 * пісні, коментарі чи відправляє скаргу, музика грає далі (ТЗ 6.3.11).
 */
data class PlayerUiState(
    val track: Track? = null,
    val commentsCount: Int = 0,
    val positionSeconds: Int = 0,
    val isLoading: Boolean = false,
    val error: UiText? = null
) {

    /**
     * Частка прослуханого (0f..1f): нею живиться прогрес-бар, а з неї ж
     * [LyricsViewModel] рахує підсвічений рядок. Тривалість невідома → 0f.
     */
    val progressFraction: Float
        get() = PlayerRules.progressFraction(positionSeconds, track?.duration ?: 0)
}

class PlayerViewModel(
    private val trackRepository: TrackRepository = TrackRepository(),
    private val commentsRepository: CommentsRepository = CommentsRepository()
) : ViewModel() {

    var uiState by mutableStateOf(PlayerUiState())
        private set

    /** id з маршруту; запам'ятовуємо, щоб «Спробувати ще раз» не втратив його. */
    private var requestedTrackId: String? = null

    /** id, який реально відкрили: за ним перечитуємо лічильник коментарів. */
    private var resolvedTrackId: String? = null

    private var loadJob: Job? = null

    /**
     * Відкриває екран плеєра.
     *
     * @param trackId id з маршруту; null або порожній рядок означає «відкрий те,
     * що є» — поки плеєр не підключено до сервісу відтворення, це перший
     * популярний трек.
     */
    fun load(trackId: String? = requestedTrackId) {
        loadJob?.cancel()

        requestedTrackId = trackId
        resolvedTrackId = null
        uiState = PlayerUiState(isLoading = true)

        loadJob = viewModelScope.launch {
            val id = PlayerRules.resolveTrackId(trackId, firstPopularTrackId())
            if (id == null) {
                uiState = PlayerUiState(error = uiText(R.string.player_error_no_tracks))
                return@launch
            }

            resolvedTrackId = id

            when (val result = trackRepository.getTrack(id)) {
                is ApiResult.Success -> uiState = uiState.copy(track = result.data, isLoading = false)
                is ApiResult.Error -> uiState = uiState.copy(
                    isLoading = false,
                    error = uiText(R.string.player_error_track)
                )
            }

            loadCommentsCount(id)
        }
    }

    /** Повторити спробу після помилки, не втративши id із маршруту. */
    fun retry() = load(requestedTrackId)

    /**
     * Позиція відтворення в секундах — сюди пише шар відтворення.
     * Поки його не підключено, частка прогресу лишається 0f, і жоден рядок
     * тексту не підсвічується передчасно.
     */
    fun onProgress(positionSeconds: Int) {
        uiState = uiState.copy(positionSeconds = positionSeconds.coerceAtLeast(0))
    }

    /**
     * Перечитує лічильник після додавання чи видалення коментаря у шторці.
     * Помилку тут не показуємо: лічильник — допоміжна деталь, а сама шторка
     * про помилку вже повідомила.
     */
    fun refreshCommentsCount() {
        val trackId = resolvedTrackId ?: return

        viewModelScope.launch { loadCommentsCount(trackId) }
    }

    private suspend fun loadCommentsCount(trackId: String) {
        // limit = 1: у плеєрі потрібне лише поле total, а не весь список.
        when (val result = commentsRepository.getComments(trackId, limit = COUNT_ONLY_LIMIT)) {
            is ApiResult.Success -> uiState = uiState.copy(commentsCount = result.data.total)
            is ApiResult.Error -> Unit
        }
    }

    private suspend fun firstPopularTrackId(): String? {
        return when (val result = trackRepository.getPopularTracks(limit = COUNT_ONLY_LIMIT)) {
            is ApiResult.Success -> result.data.tracks.firstOrNull()?.id
            is ApiResult.Error -> null
        }
    }

    private companion object {
        const val COUNT_ONLY_LIMIT = 1
    }
}
