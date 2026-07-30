package com.lumi.app.data.repository

import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for track-related operations.
 * Handles fetching tracks, searching, and managing likes.
 */
class TrackRepository {

    private val api = NetworkModule.apiService

    suspend fun getPopularTracks(limit: Int = 10): ApiResult<TrackListResponse> {
        return safeApiCall { api.getPopularTracks(limit) }
    }

    suspend fun getNewTracks(limit: Int = 10): ApiResult<TrackListResponse> {
        return safeApiCall { api.getNewTracks(limit) }
    }

    suspend fun getTrack(trackId: String): ApiResult<Track> {
        return safeApiCall { api.getTrack(trackId) }
    }

    suspend fun searchTracks(query: String, page: Int = 1, limit: Int = 20): ApiResult<TrackListResponse> {
        return safeApiCall { api.searchTracks(query, page, limit) }
    }

    suspend fun getLikedTracks(page: Int = 1, limit: Int = 20): ApiResult<TrackListResponse> {
        return safeApiCall { api.getLikedTracks(page, limit) }
    }

    suspend fun likeTrack(trackId: String): ApiResult<Unit> {
        return safeApiCall { api.likeTrack(trackId) }
    }

    suspend fun unlikeTrack(trackId: String): ApiResult<Unit> {
        return safeApiCall { api.unlikeTrack(trackId) }
    }

    suspend fun getHistory(page: Int = 1, limit: Int = 20): ApiResult<TrackListResponse> {
        return safeApiCall { api.getHistory(page, limit) }
    }

    suspend fun addToHistory(trackId: String): ApiResult<Unit> {
        return safeApiCall { api.addToHistory(AddHistoryRequest(trackId)) }
    }
}
