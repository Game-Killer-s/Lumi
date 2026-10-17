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
    const val USERS_PROFILE = "users/profile"
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

    // TODO(backend): report endpoint does not exist yet.
    // ReportRepository.reportTrack() stubs this locally.
    const val REPORT = "report/track"

    // Timeouts
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L
}
