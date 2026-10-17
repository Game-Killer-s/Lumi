package com.lumi.app.data.repository

import com.lumi.app.network.NetworkModule
import com.lumi.app.network.safeApiCall

/**
 * Процес-широкий екземпляр [StatisticsRepository] з реальним мережевим
 * викликом. Один спільний екземпляр означає, що кеш по періодах
 * переживає перестворення ViewModel.
 */
object StatisticsRepositoryProvider {
    val instance: StatisticsRepository by lazy {
        StatisticsRepository { period ->
            safeApiCall { NetworkModule.apiService.getStatisticsSummary(period.apiValue) }
        }
    }
}
