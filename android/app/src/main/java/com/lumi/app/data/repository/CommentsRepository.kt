package com.lumi.app.data.repository

import com.lumi.app.data.model.Comment
import com.lumi.app.data.model.CommentListResponse
import com.lumi.app.data.model.CreateCommentRequest
import com.lumi.app.data.model.DeletedResponse
import com.lumi.app.network.ApiResult
import com.lumi.app.network.CommentsApi
import com.lumi.app.network.NetworkModule
import com.lumi.app.network.safeApiCall

/**
 * Коментарі під треком (ТЗ 6.3.11).
 *
 * Список читаємо сторінками: лічильник у плеєрі беремо з поля total,
 * тож для нього достатньо запиту з limit = 1.
 */
class CommentsRepository(
    private val api: CommentsApi = NetworkModule.apiService
) {

    suspend fun getComments(
        trackId: String,
        page: Int = FIRST_PAGE,
        limit: Int = PAGE_SIZE
    ): ApiResult<CommentListResponse> {
        return safeApiCall { api.getComments(trackId, page, limit) }
    }

    suspend fun addComment(trackId: String, text: String): ApiResult<Comment> {
        return safeApiCall { api.addComment(trackId, CreateCommentRequest(text)) }
    }

    /**
     * Видалити власний коментар. Чужі коментарі прибирає лише модератор,
     * тому кнопка видалення показується тільки під своїми (ТЗ 6.7.2).
     */
    suspend fun deleteComment(commentId: String): ApiResult<DeletedResponse> {
        return safeApiCall { api.deleteComment(commentId) }
    }

    companion object {
        const val FIRST_PAGE = 1

        /** Скільки коментарів тягнемо за один запит. */
        const val PAGE_SIZE = 20
    }
}
