package com.lumi.app.data.repository

import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for user profile operations.
 */
class UserRepository {

    private val api = NetworkModule.apiService

    suspend fun getProfile(): ApiResult<UserProfile> {
        return safeApiCall { api.getProfile() }
    }

    suspend fun updateProfile(username: String? = null, avatarUrl: String? = null): ApiResult<UserProfile> {
        return safeApiCall { api.updateProfile(UpdateProfileRequest(username, avatarUrl)) }
    }

    suspend fun getArtists(page: Int = 1, limit: Int = 20): ApiResult<ArtistListResponse> {
        return safeApiCall { api.getArtists(page, limit) }
    }

    suspend fun getArtist(artistId: String): ApiResult<Artist> {
        return safeApiCall { api.getArtist(artistId) }
    }

    suspend fun getGenres(): ApiResult<GenreListResponse> {
        return safeApiCall { api.getGenres() }
    }
}
