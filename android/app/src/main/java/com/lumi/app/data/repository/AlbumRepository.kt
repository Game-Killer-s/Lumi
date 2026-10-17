package com.lumi.app.data.repository

import com.lumi.app.data.mock.MockDataStore
import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for album operations.
 */
class AlbumRepository {

    private val api = NetworkModule.apiService

    suspend fun getAlbums(page: Int = 1, limit: Int = 20): ApiResult<AlbumListResponse> {
        if (MockDataStore.ENABLED) return ApiResult.Success(MockDataStore.albums(page, limit))
        return safeApiCall { api.getAlbums(page, limit) }
    }

    suspend fun getAlbum(albumId: String): ApiResult<Album> {
        if (MockDataStore.ENABLED) {
            val album = MockDataStore.album(albumId)
            return if (album != null) ApiResult.Success(album) else ApiResult.Error(ApiError(statusCode = 404, message = "Album not found"))
        }
        return safeApiCall { api.getAlbum(albumId) }
    }
}
