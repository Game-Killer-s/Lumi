package com.lumi.app.data.repository

import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for album operations.
 *
 * TODO(backend): /albums does not exist on the backend yet — see the
 * Album section in Models.kt and ApiConstants.ALBUMS. This calls the
 * assumed contract (mirrors /playlists); once the real endpoint ships,
 * only ApiConstants/LumiApiService need adjusting, this class stays the
 * same shape.
 */
class AlbumRepository {

    private val api = NetworkModule.apiService

    suspend fun getAlbums(page: Int = 1, limit: Int = 20): ApiResult<AlbumListResponse> {
        return safeApiCall { api.getAlbums(page, limit) }
    }

    suspend fun getAlbum(albumId: String): ApiResult<Album> {
        return safeApiCall { api.getAlbum(albumId) }
    }
}
