package com.lumi.app.data.repository

import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for authentication operations.
 * Handles login, registration, Google Sign-In, password recovery,
 * and token/session management.
 */
class AuthRepository {

    private val api = NetworkModule.apiService
    private val subscriptions = SubscriptionRepository()

    suspend fun login(email: String, password: String): ApiResult<AuthResponse> {
        return safeApiCall {
            api.login(LoginRequest(email, password))
        }
    }

    suspend fun register(nickname: String, email: String, password: String): ApiResult<AuthResponse> {
        return safeApiCall {
            api.register(RegisterRequest(nickname, email, password))
        }
    }

    suspend fun googleLogin(idToken: String): ApiResult<AuthResponse> {
        return safeApiCall {
            api.googleLogin(GoogleAuthRequest(idToken))
        }
    }

    suspend fun forgotPassword(email: String): ApiResult<ForgotPasswordResponse> {
        return safeApiCall {
            api.forgotPassword(ForgotPasswordRequest(email))
        }
    }

    suspend fun refreshToken(refreshToken: String): ApiResult<AuthResponse> {
        return safeApiCall {
            api.refreshToken(RefreshTokenRequest(refreshToken))
        }
    }

    /**
     * Зберігає сесію. [rememberMe] керує функцією «Запам'ятати мене».
     */
    suspend fun saveSession(authResponse: AuthResponse, rememberMe: Boolean = true) {
        TokenManager.saveSession(authResponse, rememberMe)

        // Після входу одразу знаємо, що дозволено тарифом користувача (ТЗ 4.2).
        refreshEntitlement()
    }

    suspend fun logout() {
        // Вихід стирає і можливості тарифу — вони живуть у сесії.
        TokenManager.clearSession()
    }

    fun isLoggedIn(): Boolean = TokenManager.isLoggedIn()

    /**
     * Можливості тарифу поточної сесії: без підписки — лише базові.
     */
    fun currentEntitlement(): Entitlement = TokenManager.getEntitlement() ?: EntitlementPolicy.free

    suspend fun refreshEntitlement(): Entitlement = subscriptions.refreshEntitlement()
}
