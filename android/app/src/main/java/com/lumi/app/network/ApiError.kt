package com.lumi.app.network

/**
 * Represents API errors returned from the backend.
 */
data class ApiError(
    val statusCode: Int = 0,
    val message: String = "Unknown error",
    val errors: Map<String, List<String>>? = null
) {
    companion object {
        val NETWORK_ERROR = ApiError(
            statusCode = 0,
            message = "Network error. Please check your internet connection."
        )
        val TIMEOUT_ERROR = ApiError(
            statusCode = 0,
            message = "Request timed out. Please try again."
        )
        val UNKNOWN_ERROR = ApiError(
            statusCode = 0,
            message = "Something went wrong. Please try again."
        )
        val UNAUTHORIZED = ApiError(
            statusCode = 401,
            message = "Session expired. Please login again."
        )
    }
}
