package com.lumi.app.network

import com.lumi.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API service interface for Lumi backend.
 * Defines all API endpoints used by the application.
 */
interface LumiApiService {

    // ==================== AUTH ====================

    @POST(ApiConstants.AUTH_LOGIN)
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST(ApiConstants.AUTH_REGISTER)
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST(ApiConstants.AUTH_REFRESH)
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<AuthResponse>

    // ==================== USERS ====================

    @GET(ApiConstants.USERS_PROFILE)
    suspend fun getProfile(): Response<UserProfile>

    @PUT(ApiConstants.USERS_PROFILE)
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserProfile>

    // ==================== TRACKS ====================

    @GET(ApiConstants.TRACKS)
    suspend fun getTracks(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
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

    // ==================== PLAYLISTS ====================

    @GET(ApiConstants.PLAYLISTS)
    suspend fun getPlaylists(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<PlaylistListResponse>

    @GET("${ApiConstants.PLAYLISTS}/{id}")
    suspend fun getPlaylist(@Path("id") playlistId: String): Response<Playlist>

    @POST(ApiConstants.PLAYLISTS)
    suspend fun createPlaylist(@Body request: CreatePlaylistRequest): Response<Playlist>

    @PUT("${ApiConstants.PLAYLISTS}/{id}")
    suspend fun updatePlaylist(
        @Path("id") playlistId: String,
        @Body request: UpdatePlaylistRequest
    ): Response<Playlist>

    @DELETE("${ApiConstants.PLAYLISTS}/{id}")
    suspend fun deletePlaylist(@Path("id") playlistId: String): Response<Unit>

    @POST("${ApiConstants.PLAYLISTS}/{id}/tracks")
    suspend fun addTrackToPlaylist(
        @Path("id") playlistId: String,
        @Body request: AddTrackRequest
    ): Response<Playlist>

    @DELETE("${ApiConstants.PLAYLISTS}/{id}/tracks/{trackId}")
    suspend fun removeTrackFromPlaylist(
        @Path("id") playlistId: String,
        @Path("trackId") trackId: String
    ): Response<Playlist>

    // ==================== GENRES ====================

    @GET(ApiConstants.GENRES)
    suspend fun getGenres(): Response<GenreListResponse>

    // ==================== ARTISTS ====================

    @GET(ApiConstants.ARTISTS)
    suspend fun getArtists(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ArtistListResponse>

    @GET("${ApiConstants.ARTISTS}/{id}")
    suspend fun getArtist(@Path("id") artistId: String): Response<Artist>

    // ==================== LIKES ====================

    @GET(ApiConstants.LIKES)
    suspend fun getLikedTracks(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<TrackListResponse>

    @POST("${ApiConstants.LIKES}/{trackId}")
    suspend fun likeTrack(@Path("trackId") trackId: String): Response<Unit>

    @DELETE("${ApiConstants.LIKES}/{trackId}")
    suspend fun unlikeTrack(@Path("trackId") trackId: String): Response<Unit>

    // ==================== HISTORY ====================

    @GET(ApiConstants.HISTORY)
    suspend fun getHistory(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<TrackListResponse>

    @POST(ApiConstants.HISTORY)
    suspend fun addToHistory(@Body request: AddHistoryRequest): Response<Unit>
}
