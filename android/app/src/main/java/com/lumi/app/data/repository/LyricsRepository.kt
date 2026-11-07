package com.lumi.app.data.repository

import com.lumi.app.data.model.LyricsResponse
import com.lumi.app.network.ApiResult
import com.lumi.app.network.LyricsApi
import com.lumi.app.network.NetworkModule
import com.lumi.app.network.safeApiCall

/**
 * Текст пісні (ТЗ 6.3.11).
 *
 * Бекенд віддає текст одним рядком (колонка Track.lyrics), тому розбиття
 * на рядки та підсвічування «в такт» робить UI-шар — див.
 * [com.lumi.app.ui.player.LyricsRules].
 *
 * [api] підставляється за замовчуванням із [NetworkModule]; у юніт-тестах
 * передаємо маленький фейк, завдяки чому перевіряємо і репозиторій,
 * і ViewModel без мережі.
 */
class LyricsRepository(
    private val api: LyricsApi = NetworkModule.apiService
) {

    suspend fun getLyrics(trackId: String): ApiResult<LyricsResponse> {
        return safeApiCall { api.getTrackLyrics(trackId) }
    }
}
