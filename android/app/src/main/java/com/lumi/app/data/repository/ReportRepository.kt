package com.lumi.app.data.repository

import android.util.Log
import com.lumi.app.data.model.ReportReason
import com.lumi.app.network.ApiResult

/**
 * TODO(backend): no /report endpoint exists yet (ApiConstants.REPORT).
 * This stub just logs the report so the "Report" action in the track
 * context menu (reason picker) has somewhere to call into. Replace the
 * body with a real safeApiCall { api.reportTrack(...) } once the
 * endpoint ships — call sites already treat this as a suspend ApiResult.
 */
class ReportRepository {

    suspend fun reportTrack(
        trackId: String,
        reason: ReportReason,
        comment: String? = null
    ): ApiResult<Unit> {
        Log.w(
            "ReportRepository",
            "TODO(backend): reportTrack(trackId=$trackId, reason=${reason.name}, comment=$comment) — no real API call made"
        )
        return ApiResult.Success(Unit)
    }
}
