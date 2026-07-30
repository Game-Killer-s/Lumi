package com.lumi.app.network

/**
 * Simple token manager for storing JWT tokens in memory.
 * In production, use EncryptedSharedPreferences for secure storage.
 */
object TokenManager {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
    }

    fun getToken(): String? = accessToken

    fun getRefreshToken(): String? = refreshToken

    fun clearTokens() {
        accessToken = null
        refreshToken = null
    }

    fun isLoggedIn(): Boolean = accessToken != null
}
