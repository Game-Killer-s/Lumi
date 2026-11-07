package com.lumi.app.network

import com.lumi.app.data.model.AddHistoryRequest
import com.lumi.app.data.model.Track
import com.lumi.app.data.model.TrackListResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Ендпоінти треків: сам трек, списки (популярні, нові, пошук, історія)
 * та лайки.
 *
 * Виділено в окремий інтерфейс, щоб у юніт-тестах підставити маленький
 * фейк замість усього [LumiApiService] — саме так плеєр перевіряється
 * без мережі. Реалізацію додає [LumiApiService].
 */
interface TracksApi {

    @GET(ApiConstants.TRACKS)
    suspend fun getTracks(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        // TODO(backend): genre filter is not implemented server-side yet;
        // TrackRepository.getTracksByGenre() additionally filters client-side.
        @Query("genre") genre: String? = null
    ): Response<TrackListResponse>

    @GET("${ApiConstants.TRACKS}/{id}")
    suspend fun getTrack(@Path("id") trackId: String): Response<Track>

    @GET(ApiConstants.TRACKS_POPULAR)
    suspend fun getPopularTracks(
        @Query("limit") limit: Int = 10
    ): Response<TrackListResponse>

    @GET(ApiConstants.TRACKS_NEW)
    suspend fun getNewTracks(
        @Query("limit") limit: Int = 10
    ): Response<TrackListResponse>

    @GET(ApiConstants.TRACKS_SEARCH)
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<TrackListResponse>

    @GET(ApiConstants.LIKES)
    suspend fun getLikedTracks(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<TrackListResponse>

    @POST("${ApiConstants.LIKES}/{trackId}")
    suspend fun likeTrack(@Path("trackId") trackId: String): Response<Unit>

    @DELETE("${ApiConstants.LIKES}/{trackId}")
    suspend fun unlikeTrack(@Path("trackId") trackId: String): Response<Unit>

    @GET(ApiConstants.HISTORY)
    suspend fun getHistory(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<TrackListResponse>

    @POST(ApiConstants.HISTORY)
    suspend fun addToHistory(@Body request: AddHistoryRequest): Response<Unit>
}
