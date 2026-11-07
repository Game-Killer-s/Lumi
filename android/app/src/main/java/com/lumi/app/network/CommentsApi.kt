package com.lumi.app.network

import com.lumi.app.data.model.Comment
import com.lumi.app.data.model.CommentListResponse
import com.lumi.app.data.model.CreateCommentRequest
import com.lumi.app.data.model.DeletedResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Ендпоінти коментарів під треком (ТЗ 6.3.11).
 *
 * Виділено в окремий інтерфейс, щоб у юніт-тестах підставити маленький
 * фейк замість усього [LumiApiService]. Реалізацію додає [LumiApiService].
 */
interface CommentsApi {

    @GET("${ApiConstants.TRACKS}/{id}/comments")
    suspend fun getComments(
        @Path("id") trackId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<CommentListResponse>

    @POST("${ApiConstants.TRACKS}/{id}/comments")
    suspend fun addComment(
        @Path("id") trackId: String,
        @Body request: CreateCommentRequest
    ): Response<Comment>

    /** Видаляти можна лише власний коментар — сервер це теж перевіряє. */
    @DELETE("${ApiConstants.COMMENTS}/{id}")
    suspend fun deleteComment(@Path("id") commentId: String): Response<DeletedResponse>
}
