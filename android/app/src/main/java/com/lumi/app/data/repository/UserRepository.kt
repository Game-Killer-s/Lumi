package com.lumi.app.data.repository

import com.lumi.app.data.mock.MockDataStore
import com.lumi.app.data.model.*
import com.lumi.app.network.*
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Repository for user profile operations.
 */
class UserRepository {

    private val api = NetworkModule.apiService

    suspend fun getProfile(): ApiResult<UserProfile> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.profile())
        return safeApiCall { api.getProfile() }
    }

    /**
     * PATCH /profile приймає лише nickname та email (див. UpdateProfileDto).
     * Аватар змінюється окремим multipart-запитом [uploadAvatar].
     */
    suspend fun updateProfile(
        username: String? = null,
        email: String? = null,
        avatarUrl: String? = null
    ): ApiResult<UserProfile> {
        if (MockDataStore.ENABLED) {
            return persistProfile(
                ApiResult.Success(MockDataStore.updateProfile(username, email, avatarUrl))
            )
        }
        return persistProfile(
            safeApiCall {
                api.updateProfile(UpdateProfileRequest(nickname = username, email = email))
            }
        )
    }

    suspend fun uploadAvatar(file: File, mimeType: String = "image/jpeg"): ApiResult<UserProfile> {
        if (MockDataStore.ENABLED) {
            return persistProfile(
                ApiResult.Success(
                    MockDataStore.updateProfile(
                        username = null,
                        avatarUrl = file.toURI().toString()
                    )
                )
            )
        }

        val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("avatar", file.name, requestBody)
        return persistProfile(safeApiCall { api.uploadAvatar(part) })
    }

    suspend fun updatePrivacy(
        isPublic: Boolean? = null,
        showCurrentListening: Boolean? = null
    ): ApiResult<UserProfile> {
        if (MockDataStore.ENABLED) {
            return persistProfile(
                ApiResult.Success(
                    MockDataStore.updatePrivacy(isPublic, showCurrentListening)
                )
            )
        }
        return persistProfile(
            safeApiCall {
                api.updatePrivacy(
                    UpdatePrivacyRequest(
                        isPublic = isPublic,
                        showCurrentListening = showCurrentListening
                    )
                )
            }
        )
    }

    private suspend fun persistProfile(result: ApiResult<UserProfile>): ApiResult<UserProfile> {
        if (result is ApiResult.Success) {
            /*
             * Кеш сесії не має «ламати» успішне збереження: на сервері дані
             * вже оновлені, тож збій запису в DataStore не повинен
             * перетворювати успіх на помилку в UI.
             */
            try {
                TokenManager.updateCurrentUser(result.data)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Дані в памʼяті вже оновлені — просто ігноруємо збій кешу.
            }
        }
        return result
    }

    suspend fun getArtists(page: Int = 1, limit: Int = 20): ApiResult<ArtistListResponse> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.getArtists(page, limit))
        return safeApiCall { api.getArtists(page, limit) }
    }

    suspend fun getArtist(artistId: String): ApiResult<Artist> {
        if (MockDataStore.ENABLED) {
            val artist = MockDataStore.artist(artistId)
            return if (artist != null) ApiResult.Success(artist) else ApiResult.Error(ApiError(statusCode = 404, message = "Artist not found"))
        }
        return safeApiCall { api.getArtist(artistId) }
    }

    suspend fun getGenres(): ApiResult<GenreListResponse> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.genres())
        return safeApiCall { api.getGenres() }
    }

    suspend fun followArtist(artistId: String): ApiResult<Unit> {
        if (MockDataStore.ENABLED) {
            MockDataStore.followArtist(artistId)
            return ApiResult.Success(Unit)
        }
        return ApiResult.Success(Unit)
    }

    suspend fun unfollowArtist(artistId: String): ApiResult<Unit> {
        if (MockDataStore.ENABLED) {
            MockDataStore.unfollowArtist(artistId)
            return ApiResult.Success(Unit)
        }
        return ApiResult.Success(Unit)
    }
}
