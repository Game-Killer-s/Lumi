package com.lumi.app.data.repository

import com.lumi.app.data.mock.MockDataStore
import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for playlist operations.
 * Handles CRUD operations for playlists and track management within playlists.
 */
class PlaylistRepository {

    private val api = NetworkModule.apiService

    suspend fun getPlaylists(page: Int = 1, limit: Int = 20): ApiResult<PlaylistListResponse> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.playlists(page, limit))
        return safeApiCall { api.getPlaylists(page, limit) }
    }

    suspend fun getPlaylist(playlistId: String): ApiResult<Playlist> {
        if (MockDataStore.ENABLED) {
            val playlist = MockDataStore.playlist(playlistId)
            return if (playlist != null) ApiResult.Success(playlist) else ApiResult.Error(ApiError(statusCode = 404, message = "Playlist not found"))
        }
        return safeApiCall { api.getPlaylist(playlistId) }
    }

    suspend fun createPlaylist(title: String, description: String? = null, isPublic: Boolean = true): ApiResult<Playlist> {
        if (MockDataStore.ENABLED) {
            return ApiResult.Success(MockDataStore.createPlaylist(title, description, isPublic))
        }
        return safeApiCall { api.createPlaylist(CreatePlaylistRequest(title, description, isPublic)) }
    }

    suspend fun updatePlaylist(playlistId: String, title: String? = null, description: String? = null, isPublic: Boolean? = null): ApiResult<Playlist> {
        if (MockDataStore.ENABLED) {
            val p = MockDataStore.updatePlaylist(playlistId, title, description, isPublic)
            return if (p != null) ApiResult.Success(p) else ApiResult.Error(ApiError(statusCode = 404, message = "Playlist not found"))
        }
        return safeApiCall { api.updatePlaylist(playlistId, UpdatePlaylistRequest(title, description, isPublic)) }
    }

    suspend fun deletePlaylist(playlistId: String): ApiResult<Unit> {
        if (MockDataStore.ENABLED) {
            MockDataStore.deletePlaylist(playlistId)
            return ApiResult.Success(Unit)
        }
        return safeApiCall { api.deletePlaylist(playlistId) }
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String): ApiResult<Playlist> {
        if (MockDataStore.ENABLED) {
            val p = MockDataStore.addTrackToPlaylist(playlistId, trackId)
            return if (p != null) ApiResult.Success(p) else ApiResult.Error(ApiError(statusCode = 404, message = "Playlist or Track not found"))
        }
        return safeApiCall { api.addTrackToPlaylist(playlistId, AddTrackRequest(trackId)) }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): ApiResult<Playlist> {
        if (MockDataStore.ENABLED) {
            val p = MockDataStore.removeTrackFromPlaylist(playlistId, trackId)
            return if (p != null) ApiResult.Success(p) else ApiResult.Error(ApiError(statusCode = 404, message = "Playlist not found"))
        }
        return safeApiCall { api.removeTrackFromPlaylist(playlistId, trackId) }
    }
}
