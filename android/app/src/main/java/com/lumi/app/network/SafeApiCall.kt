package com.lumi.app.network

import com.google.gson.Gson
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Safe API call wrapper that catches network errors and parses error responses.
 * Use this for all API calls to ensure consistent error handling.
 */
suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): ApiResult<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Error(ApiError(response.code(), "Empty response body"))
            }
        } else {
            val errorBody = response.errorBody()?.string()
            val apiError = try {
                Gson().fromJson(errorBody, ApiError::class.java)
            } catch (e: Exception) {
                ApiError(
                    statusCode = response.code(),
                    message = getErrorMessage(response.code())
                )
            }
            ApiResult.Error(apiError)
        }
    } catch (e: UnknownHostException) {
        ApiResult.Error(ApiError.NETWORK_ERROR)
    } catch (e: ConnectException) {
        ApiResult.Error(ApiError.NETWORK_ERROR)
    } catch (e: SocketTimeoutException) {
        ApiResult.Error(ApiError.TIMEOUT_ERROR)
    } catch (e: Exception) {
        ApiResult.Error(ApiError.UNKNOWN_ERROR)
    }
}

/**
 * Returns a human-readable error message based on HTTP status code.
 */
fun getErrorMessage(code: Int): String = when (code) {
    400 -> "Bad request. Please check your input."
    401 -> "Unauthorized. Please login again."
    403 -> "Access denied."
    404 -> "Resource not found."
    409 -> "Conflict. Resource already exists."
    422 -> "Validation failed. Please check your input."
    429 -> "Too many requests. Please slow down."
    in 500..599 -> "Server error. Please try again later."
    else -> "Something went wrong. Please try again."
}
