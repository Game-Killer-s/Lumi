package com.lumi.app.data.repository

import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for notification settings and the notification curtain.
 */
class NotificationRepository {

    private val api = NetworkModule.apiService

    suspend fun getSettings(): ApiResult<NotificationSettings> {
        return safeApiCall { api.getNotificationSettings() }
    }

    suspend fun updateSettings(
        request: UpdateNotificationSettingsRequest
    ): ApiResult<NotificationSettings> {
        return safeApiCall { api.updateNotificationSettings(request) }
    }

    suspend fun getNotifications(): ApiResult<NotificationListResponse> {
        return safeApiCall { api.getNotifications() }
    }

    suspend fun markAsRead(notificationId: String): ApiResult<NotificationItem> {
        return safeApiCall { api.markNotificationRead(notificationId) }
    }

    suspend fun markAllAsRead(): ApiResult<UpdatedCountResponse> {
        return safeApiCall { api.markAllNotificationsRead() }
    }
}
