package com.lumi.app.data.repository

import com.lumi.app.data.model.CreateReportRequest
import com.lumi.app.data.model.ReportReason
import com.lumi.app.data.model.ReportResponse
import com.lumi.app.network.ApiResult
import com.lumi.app.network.NetworkModule
import com.lumi.app.network.ReportApi
import com.lumi.app.network.safeApiCall

/**
 * Скарги: трек або коментар під ним потрапляє в адмін-панель модератору
 * (ТЗ 6.3.11, ТЗ 6.7.2).
 *
 * Запит формує [com.lumi.app.ui.player.ReportRules] — репозиторій лише
 * відправляє його й повертає статус модерації.
 */
class ReportRepository(
    private val api: ReportApi = NetworkModule.apiService
) {

    suspend fun sendReport(request: CreateReportRequest): ApiResult<ReportResponse> {
        return safeApiCall { api.sendReport(request) }
    }

    /**
     * Сумісний вхід для меню треку ([com.lumi.app.ui.component.TrackContextMenu]):
     * воно передає [ReportReason] з data.model, тож мапимо його на той самий
     * запит, що й діалог скарги в плеєрі.
     */
    suspend fun reportTrack(
        trackId: String,
        reason: ReportReason,
        comment: String? = null
    ): ApiResult<Unit> {
        val request = CreateReportRequest(
            target = "track",
            targetId = trackId,
            trackId = trackId,
            reason = reason.name.lowercase(),
            comment = comment?.trim()?.takeIf { it.isNotEmpty() }
        )

        return when (val result = sendReport(request)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> result
        }
    }
}
