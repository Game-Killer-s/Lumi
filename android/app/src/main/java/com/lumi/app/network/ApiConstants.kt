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

    // Timeouts
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L
}
