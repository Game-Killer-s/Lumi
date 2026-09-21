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

    /**
     * TODO(backend): no follow/unfollow endpoint exists yet
     * (ApiConstants.ARTISTS_FOLLOW). This is a local-only stub so the UI
     * (Artist screen, follow button) can be built and tested now.
     * Replace the body with a real safeApiCall once the endpoint ships;
     * ArtistViewModel already treats this as a suspend ApiResult call so
     * no call-site changes will be needed.
     */
    suspend fun followArtist(artistId: String): ApiResult<Unit> {
        android.util.Log.w("UserRepository", "TODO(backend): followArtist($artistId) — no real API call made")
        return ApiResult.Success(Unit)
    }

    suspend fun unfollowArtist(artistId: String): ApiResult<Unit> {
        android.util.Log.w("UserRepository", "TODO(backend): unfollowArtist($artistId) — no real API call made")
        return ApiResult.Success(Unit)
    }
}
