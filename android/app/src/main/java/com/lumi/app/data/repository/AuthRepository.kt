package com.lumi.app.data.repository

import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for authentication operations.
 * Handles login, registration, and token management.
 */
class AuthRepository {

    private val api = NetworkModule.apiService

    suspend fun login(email: String, password: String): ApiResult<AuthResponse> {
        return safeApiCall {
            api.login(LoginRequest(email, password))
        }
    }

    suspend fun register(username: String, email: String, password: String): ApiResult<AuthResponse> {
        return safeApiCall {
            api.register(RegisterRequest(username, email, password))
        }
    }

    suspend fun refreshToken(refreshToken: String): ApiResult<AuthResponse> {
        return safeApiCall {
            api.refreshToken(RefreshTokenRequest(refreshToken))
        }
    }

    fun saveSession(authResponse: AuthResponse) {
        TokenManager.saveTokens(authResponse.accessToken, authResponse.refreshToken)
    }

    fun logout() {
        TokenManager.clearTokens()
    }

    fun isLoggedIn(): Boolean = TokenManager.isLoggedIn()
}
