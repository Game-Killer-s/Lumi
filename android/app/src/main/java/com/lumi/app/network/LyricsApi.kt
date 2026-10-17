package com.lumi.app.network

import com.lumi.app.data.model.LyricsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Ендпоінти тексту пісні (ТЗ 6.3.11).
 *
 * Виділено в окремий інтерфейс, щоб у юніт-тестах підставити маленький
 * фейк замість усього [LumiApiService]. Реалізацію додає [LumiApiService].
 */
interface LyricsApi {

    @GET("${ApiConstants.TRACKS}/{id}/lyrics")
    suspend fun getTrackLyrics(@Path("id") trackId: String): Response<LyricsResponse>
}
