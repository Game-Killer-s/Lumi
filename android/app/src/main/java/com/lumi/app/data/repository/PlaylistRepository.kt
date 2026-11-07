package com.lumi.app.data.repository

import com.lumi.app.data.local.LibraryLocalStore
import com.lumi.app.data.mock.MockDataStore
import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for playlist operations.
 *
 * У контракті backend немає стабільних endpoint'ів для плейлистів, тому
 * створення/перейменування/видалення виконуються локально через
 * [LibraryLocalStore]. Це дає миттєвий відгук і зберігає дані між запусками.
 */
class PlaylistRepository {

    private val api = NetworkModule.apiService

    suspend fun getPlaylists(page: Int = 1, limit: Int = 20): ApiResult<PlaylistListResponse> {
        val playlists = LibraryLocalStore.playlists()
        if (playlists.isNotEmpty()) {
            return ApiResult.Success(
                PlaylistListResponse(playlists = playlists, total = playlists.size, page = page, limit = limit)
            )
        }
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.playlists(page, limit))
        // TODO(backend): замінити на реальний GET /playlists, щойно він з'явиться.
        return ApiResult.Success(
            PlaylistListResponse(playlists = emptyList(), total = 0, page = page, limit = limit)
        )
    }

    suspend fun getPlaylist(playlistId: String): ApiResult<Playlist> {
        LibraryLocalStore.playlist(playlistId)?.let { return ApiResult.Success(it) }
        if (MockDataStore.ENABLED) {
            val playlist = MockDataStore.playlist(playlistId)
            return if (playlist != null) ApiResult.Success(playlist) else ApiResult.Error(ApiError(statusCode = 404, message = "Playlist not found"))
        }
        return safeApiCall { api.getPlaylist(playlistId) }
    }

    fun createPlaylist(title: String, description: String? = null, isPublic: Boolean = true): ApiResult<Playlist> {
        val normalized = title.trim()
        if (normalized.isEmpty()) {
            return ApiResult.Error(ApiError(statusCode = 400, message = "Playlist title is empty"))
        }
        val id = "local-" + java.util.UUID.randomUUID().toString()
        val playlist = LibraryLocalStore.createPlaylist(
            title = normalized,
            description = description,
            now = System.currentTimeMillis(),
            id = id
        )
        return ApiResult.Success(playlist)
    }

    fun updatePlaylist(
        playlistId: String,
        title: String? = null,
        description: String? = null,
        isPublic: Boolean? = null
    ): ApiResult<Playlist> {
        val current = LibraryLocalStore.playlist(playlistId)
            ?: return ApiResult.Error(ApiError(statusCode = 404, message = "Playlist not found"))
        val nextTitle = title?.trim() ?: current.title
        if (nextTitle.isEmpty()) {
            return ApiResult.Error(ApiError(statusCode = 400, message = "Playlist title is empty"))
        }
        LibraryLocalStore.updatePlaylist(
            playlistId = playlistId,
            title = nextTitle,
            description = description ?: current.description
        )
        return ApiResult.Success(checkNotNull(LibraryLocalStore.playlist(playlistId)))
    }

    fun deletePlaylist(playlistId: String): ApiResult<Unit> {
        if (LibraryLocalStore.playlist(playlistId) == null) {
            return ApiResult.Error(ApiError(statusCode = 404, message = "Playlist not found"))
        }
        LibraryLocalStore.deletePlaylist(playlistId)
        return ApiResult.Success(Unit)
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String): ApiResult<Playlist> {
        val playlist = LibraryLocalStore.playlist(playlistId)
            ?: return ApiResult.Error(ApiError(statusCode = 404, message = "Playlist not found"))
        val knownTrack = (playlist.tracks.orEmpty() +
            LibraryLocalStore.likedTracks() +
            LibraryLocalStore.history().map { it.track })
            .firstOrNull { it.id == trackId }
        if (knownTrack != null) return addTrackToPlaylist(playlistId, knownTrack)

        if (MockDataStore.ENABLED) {
            val p = MockDataStore.addTrackToPlaylist(playlistId, trackId)
            if (p != null) {
                p.tracks.orEmpty().firstOrNull { it.id == trackId }?.let {
                    LibraryLocalStore.addTrack(playlistId, it)
                }
                return ApiResult.Success(checkNotNull(LibraryLocalStore.playlist(playlistId)))
            }
            return ApiResult.Error(ApiError(statusCode = 404, message = "Playlist or Track not found"))
        }

        // Track не траплявся в локальних списках — пробуємо отримати його з API.
        return when (val result = safeApiCall { api.getTrack(trackId) }) {
            is ApiResult.Success -> addTrackToPlaylist(playlistId, result.data)
            is ApiResult.Error -> result
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track): ApiResult<Playlist> {
        if (LibraryLocalStore.playlist(playlistId) == null) {
            return ApiResult.Error(ApiError(statusCode = 404, message = "Playlist not found"))
        }
        LibraryLocalStore.addTrack(playlistId, track)
        return ApiResult.Success(checkNotNull(LibraryLocalStore.playlist(playlistId)))
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String): ApiResult<Playlist> {
        if (LibraryLocalStore.playlist(playlistId) == null) {
            return ApiResult.Error(ApiError(statusCode = 404, message = "Playlist not found"))
        }
        LibraryLocalStore.removeTrack(playlistId, trackId)
        return ApiResult.Success(checkNotNull(LibraryLocalStore.playlist(playlistId)))
    }
}
