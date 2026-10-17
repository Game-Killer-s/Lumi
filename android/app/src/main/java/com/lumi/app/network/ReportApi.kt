package com.lumi.app.network

import com.lumi.app.data.model.CreateReportRequest
import com.lumi.app.data.model.ReportResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Ендпоінти скарг: трек або коментар потрапляє в адмін-панель модератору
 * (ТЗ 6.3.11 «Поскаржитися на трек», ТЗ 6.7.2).
 *
 * Виділено в окремий інтерфейс, щоб у юніт-тестах підставити маленький
 * фейк замість усього [LumiApiService]. Реалізацію додає [LumiApiService].
 */
interface ReportApi {

    @POST(ApiConstants.REPORTS)
    suspend fun sendReport(@Body request: CreateReportRequest): Response<ReportResponse>
}
