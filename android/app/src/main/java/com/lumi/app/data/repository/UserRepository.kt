package com.lumi.app.data.repository

import com.lumi.app.data.mock.MockDataStore
import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for user profile operations.
 */
class UserRepository {

    private val api = NetworkModule.apiService

    suspend fun getProfile(): ApiResult<UserProfile> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.profile())
        return safeApiCall { api.getProfile() }
    }

    suspend fun updateProfile(username: String? = null, avatarUrl: String? = null): ApiResult<UserProfile> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.updateProfile(username, avatarUrl))
        return safeApiCall { api.updateProfile(UpdateProfileRequest(username, avatarUrl)) }
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
