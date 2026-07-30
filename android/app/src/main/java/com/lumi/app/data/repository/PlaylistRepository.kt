package com.lumi.app.data.repository

import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for playlist operations.
 * Handles CRUD operations for playlists and track management within playlists.
 */
class PlaylistRepository {

    private val api = NetworkModule.apiService

    suspend fun getPlaylists(page: Int = 1, limit: Int = 20): ApiResult<PlaylistListResponse> {
        return safeApiCall { api.getPlaylists(page, limit) }
    }

    suspend fun getPlaylist(playlistId: String): ApiResult<Playlist> {
        return safeApiCall { api.getPlaylist(playlistId) }
    }

    suspend fun createPlaylist(title: String, description: String? = null, isPublic: Boolean = true): ApiResult<Playlist> {
        return safeApiCall { api.createPlaylist(CreatePlaylistRequest(title, description, isPublic)) }
    }

    suspend fun updatePlaylist(playlistId: String, title: String? = null, description: String? = null, isPublic: Boolean? = null): ApiResult<Playlist> {
        return safeApiCall { api.updatePlaylist(playlistId, UpdatePlaylistRequest(title, description, isPublic)) }
    }

    suspend fun deletePlaylist(playlistId: String): ApiResult<Unit> {
        return safeApiCall { api.deletePlaylist(playlistId) }
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String): ApiResult<Playlist> {
        return safeApiCall { api.addTrackToPlaylist(playlistId, AddTrackRequest(trackId)) }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): ApiResult<Playlist> {
        return safeApiCall { api.removeTrackFromPlaylist(playlistId, trackId) }
    }
}
