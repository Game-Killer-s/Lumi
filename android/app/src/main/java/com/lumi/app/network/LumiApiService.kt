package com.lumi.app.network

import com.lumi.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API service interface for Lumi backend.
 * Defines all API endpoints used by the application.
 */
interface LumiApiService : LyricsApi, CommentsApi, ReportApi, TracksApi {

    // ==================== AUTH ====================

    @POST(ApiConstants.AUTH_LOGIN)
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST(ApiConstants.AUTH_REGISTER)
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST(ApiConstants.AUTH_REFRESH)
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<AuthResponse>

    @POST(ApiConstants.AUTH_GOOGLE)
    suspend fun googleLogin(@Body request: GoogleAuthRequest): Response<AuthResponse>

    @POST(ApiConstants.AUTH_FORGOT_PASSWORD)
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ForgotPasswordResponse>

    // ==================== USERS ====================

    @GET(ApiConstants.USERS_PROFILE)
    suspend fun getProfile(): Response<UserProfile>

    @PUT(ApiConstants.USERS_PROFILE)
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserProfile>

    // ==================== TRACKS ====================
    // Самі ендпоінти треків, лайків та історії живуть у [TracksApi]:
    // вузький інтерфейс лишає плеєр і його тести без усієї мережі застосунку.

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

    // ==================== ALBUMS ====================
    // TODO(backend): endpoints below assume the same contract as
    // /playlists. Confirm/replace once real /albums API ships.

    @GET(ApiConstants.ALBUMS)
    suspend fun getAlbums(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<AlbumListResponse>

    @GET("${ApiConstants.ALBUMS}/{id}")
    suspend fun getAlbum(@Path("id") albumId: String): Response<Album>

    // ==================== RECOMMENDATIONS ====================
    // TODO(backend): wire this up once /tracks/recommended exists.

    @GET(ApiConstants.TRACKS_RECOMMENDED)
    suspend fun getRecommendedTracks(
        @Query("limit") limit: Int = 20
    ): Response<TrackListResponse>

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
    // Лайки — у [TracksApi] разом з іншими ендпоінтами треків.

    // ==================== NOTIFICATIONS ====================

    @GET(ApiConstants.NOTIFICATIONS_SETTINGS)
    suspend fun getNotificationSettings(): Response<NotificationSettings>

    @PUT(ApiConstants.NOTIFICATIONS_SETTINGS)
    suspend fun updateNotificationSettings(
        @Body request: UpdateNotificationSettingsRequest
    ): Response<NotificationSettings>

    @GET(ApiConstants.NOTIFICATIONS)
    suspend fun getNotifications(): Response<NotificationListResponse>

    @PATCH("${ApiConstants.NOTIFICATIONS}/{id}/read")
    suspend fun markNotificationRead(@Path("id") notificationId: String): Response<NotificationItem>

    @PATCH(ApiConstants.NOTIFICATIONS_READ_ALL)
    suspend fun markAllNotificationsRead(): Response<UpdatedCountResponse>

    // ==================== SUBSCRIPTIONS ====================

    @GET(ApiConstants.SUBSCRIPTION_PLANS)
    suspend fun getSubscriptionPlans(): Response<List<SubscriptionPlan>>

    @GET(ApiConstants.SUBSCRIPTION_STATUS)
    suspend fun getSubscriptionStatus(): Response<SubscriptionStatusResponse>

    @GET(ApiConstants.SUBSCRIPTION_PLAY_ACCESS)
    suspend fun checkPlayAccess(): Response<PlayAccessResponse>

    @POST(ApiConstants.SUBSCRIPTION_CHECKOUT)
    suspend fun checkout(@Body request: CheckoutRequest): Response<CheckoutResponse>

    @POST(ApiConstants.SUBSCRIPTION_CANCEL)
    suspend fun cancelSubscription(): Response<CancelSubscriptionResponse>

    @GET(ApiConstants.SUBSCRIPTION_PAYMENTS)
    suspend fun getPaymentHistory(): Response<List<PaymentHistoryItem>>

    @GET(ApiConstants.SUBSCRIPTION_PAYMENT_METHODS)
    suspend fun getPaymentMethods(): Response<List<PaymentMethod>>

    @POST(ApiConstants.SUBSCRIPTION_PAYMENT_METHODS)
    suspend fun addPaymentMethod(
        @Body request: AddPaymentMethodRequest
    ): Response<PaymentMethod>

    @PUT("${ApiConstants.SUBSCRIPTION_PAYMENT_METHODS}/{id}")
    suspend fun updatePaymentMethod(
        @Path("id") paymentMethodId: String,
        @Body request: UpdatePaymentMethodRequest
    ): Response<PaymentMethod>

    @DELETE("${ApiConstants.SUBSCRIPTION_PAYMENT_METHODS}/{id}")
    suspend fun deletePaymentMethod(
        @Path("id") paymentMethodId: String
    ): Response<DeletedResponse>
    // ==================== STATISTICS ====================

    @GET(ApiConstants.STATISTICS_SUMMARY)
    suspend fun getStatisticsSummary(
        @Query("period") period: String
    ): Response<StatsSummaryDto>
}
