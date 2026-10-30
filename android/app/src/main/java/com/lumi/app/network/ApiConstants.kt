package com.lumi.app.network

import com.lumi.app.BuildConfig

/**
 * API configuration constants.
 * Base URL is configured via BuildConfig for different build variants.
 */
object ApiConstants {
    val BASE_URL: String = BuildConfig.BASE_URL

    // API endpoints
    const val AUTH_LOGIN = "auth/login"
    const val AUTH_REGISTER = "auth/register"
    const val AUTH_REFRESH = "auth/refresh"
    const val AUTH_GOOGLE = "auth/google"
    const val AUTH_FORGOT_PASSWORD = "auth/forgot-password"
    // Backend ProfileController exposes GET/PATCH /api/profile.
    // The old /api/users/profile path no longer exists.
    const val USERS_PROFILE = "profile"
    const val USERS_PROFILE_PRIVACY = "profile/privacy"
    const val USERS_PROFILE_AVATAR = "profile/avatar"
    const val TRACKS = "tracks"
    const val TRACKS_POPULAR = "tracks/popular"
    const val TRACKS_NEW = "tracks/new"
    const val TRACKS_SEARCH = "tracks/search"
    const val PLAYLISTS = "playlists"
    const val PLAYLISTS_MINE = "playlists/mine"
    const val GENRES = "genres"
    const val ARTISTS = "artists"
    const val LIKES = "likes"
    const val HISTORY = "history"

    // Notifications
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATIONS_SETTINGS = "notifications/settings"
    const val NOTIFICATIONS_READ_ALL = "notifications/read-all"

    // Subscription
    const val SUBSCRIPTION_PLANS = "subscriptions/plans"
    const val SUBSCRIPTION_STATUS = "subscriptions/status"
    const val SUBSCRIPTION_PLAY_ACCESS = "subscriptions/play-access"
    const val SUBSCRIPTION_CHECKOUT = "subscriptions/checkout"
    const val SUBSCRIPTION_CANCEL = "subscriptions/cancel"
    const val SUBSCRIPTION_PAYMENTS = "subscriptions/payments"
    const val SUBSCRIPTION_PAYMENT_METHODS = "subscriptions/payment-methods"
    // TODO(backend): /statistics/summary does not exist yet — contract in docs/statistics-api-contract.md.
    const val STATISTICS_SUMMARY = "statistics/summary"

    // TODO(backend): /albums does not exist yet — see Models.kt Album section.
    const val ALBUMS = "albums"

    // TODO(backend): recommendations endpoint does not exist yet.
    // Once it does, point TrackRepository.getRecommendedTracks() at this
    // instead of its current getPopularTracks()/getNewTracks() fallback.
    const val TRACKS_RECOMMENDED = "tracks/recommended"

    // TODO(backend): follow/unfollow endpoints do not exist yet.
    // UserRepository.followArtist()/unfollowArtist() stub these locally.
    const val ARTISTS_FOLLOW = "artists/{id}/follow"

    // Текст пісні, коментарі та скарги (екран плеєра)
    const val COMMENTS = "comments"
    // POST /api/reports — скарга на трек або коментар (ReportRepository).
    const val REPORTS = "reports"


    /**
     * Абсолютний URL медіафайлу для плеєра/Coil.
     *
     * Бекенд зберігає у БД відносний шлях (див. mediaPublicUrl):
     * "/media/audio/2026/10/<uuid>.flac". API живе під .../api/, а
     * статика під префіксом /media/ віддається з кореня, тому суфікс
     * "api/" відрізаємо. Абсолютні URL (http/https, content://, file://)
     * повертаємо як є.
     */
    fun mediaUrl(path: String?): String? {
        val value = path?.trim().orEmpty()
        if (value.isEmpty()) return null

        if (value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("content://", ignoreCase = true) ||
            value.startsWith("file://", ignoreCase = true) ||
            value.startsWith("android.resource://", ignoreCase = true)
        ) {
            return value
        }

        val root = BASE_URL.trimEnd('/').removeSuffix("/api").trimEnd('/')
        return "$root/${value.trimStart('/')}"
    }

    // Timeouts
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L
}
