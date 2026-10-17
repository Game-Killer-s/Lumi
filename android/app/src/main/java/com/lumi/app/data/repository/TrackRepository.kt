package com.lumi.app.data.repository

import com.lumi.app.data.mock.MockDataStore
import com.lumi.app.data.model.*
import com.lumi.app.network.*
import com.lumi.app.util.filterByGenre

/**
 * Repository for track-related operations.
 * Handles fetching tracks, searching, and managing likes.
 */
class TrackRepository {

    private val api = NetworkModule.apiService

    suspend fun getPopularTracks(limit: Int = 10): ApiResult<TrackListResponse> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.popular(limit))
        return safeApiCall { api.getPopularTracks(limit) }
    }

    suspend fun getRecommendedTracks(limit: Int = 20): ApiResult<TrackListResponse> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.popular(limit))
        val popular = safeApiCall { api.getPopularTracks(limit) }
        if (popular is ApiResult.Success) return popular
        return safeApiCall { api.getNewTracks(limit) }
    }

    suspend fun getNewTracks(limit: Int = 10): ApiResult<TrackListResponse> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.newReleases(limit))
        return safeApiCall { api.getNewTracks(limit) }
    }

    suspend fun getTrack(trackId: String): ApiResult<Track> {
        if (MockDataStore.ENABLED) {
            val track = MockDataStore.track(trackId)
            return if (track != null) ApiResult.Success(track) else ApiResult.Error(ApiError(statusCode = 404, message = "Track not found"))
        }
        return safeApiCall { api.getTrack(trackId) }
    }

    suspend fun searchTracks(query: String, page: Int = 1, limit: Int = 20): ApiResult<TrackListResponse> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.searchTracks(query, page, limit))
        return safeApiCall { api.searchTracks(query, page, limit) }
    }

    suspend fun getTracksByGenre(genre: String, limit: Int = 50): ApiResult<TrackListResponse> {
        if (MockDataStore.ENABLED) {
            val all = MockDataStore.allTracks(limit)
            return ApiResult.Success(all.copy(tracks = filterByGenre(all.tracks, genre)))
        }
        return when (val result = safeApiCall { api.getTracks(page = 1, limit = limit, genre = genre) }) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.copy(tracks = filterByGenre(result.data.tracks, genre))
            )
            is ApiResult.Error -> result
        }
    }

    suspend fun getLikedTracks(page: Int = 1, limit: Int = 20): ApiResult<TrackListResponse> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.liked(page, limit))
        return safeApiCall { api.getLikedTracks(page, limit) }
    }

    suspend fun likeTrack(trackId: String): ApiResult<Unit> {
        if (MockDataStore.ENABLED) {
            MockDataStore.like(trackId)
            return ApiResult.Success(Unit)
        }
        return safeApiCall { api.likeTrack(trackId) }
    }

    suspend fun unlikeTrack(trackId: String): ApiResult<Unit> {
        if (MockDataStore.ENABLED) {
            MockDataStore.unlike(trackId)
            return ApiResult.Success(Unit)
        }
        return safeApiCall { api.unlikeTrack(trackId) }
    }

    suspend fun getHistory(page: Int = 1, limit: Int = 20): ApiResult<TrackListResponse> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.history(page, limit))
        return safeApiCall { api.getHistory(page, limit) }
    }

    suspend fun addToHistory(trackId: String): ApiResult<Unit> {
        if (MockDataStore.ENABLED) {
            MockDataStore.addHistory(trackId)
            return ApiResult.Success(Unit)
        }
        return safeApiCall { api.addToHistory(AddHistoryRequest(trackId)) }
    }
}
