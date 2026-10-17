package com.lumi.app.data.repository

import com.lumi.app.data.model.StatsPeriod
import com.lumi.app.data.model.StatsSummary
import com.lumi.app.data.model.StatsSummaryDto
import com.lumi.app.data.model.toDomain
import com.lumi.app.network.ApiResult
import java.util.concurrent.ConcurrentHashMap

/** Джерело статистики для ViewModel: кеш по періоду + завантаження. */
interface StatisticsSource {
    /** Останній успішно завантажений результат для [period] або null. */
    fun cached(period: StatsPeriod): StatsSummary?

    /** Завантажує свіжі дані; при успіху оновлює кеш цього періоду. */
    suspend fun load(period: StatsPeriod): ApiResult<StatsSummary>
}

/**
 * Репозиторій статистики. Мережевий виклик передається ззовні ([remote]),
 * тому логіку кешування можна тестувати без Retrofit — реальний виклик
 * підключено в [StatisticsRepositoryProvider].
 *
 * Кеш зберігає останній успішний результат окремо для кожного періоду;
 * помилка завантаження кеш не чіпає.
 */
class StatisticsRepository(
    private val remote: suspend (StatsPeriod) -> ApiResult<StatsSummaryDto>
) : StatisticsSource {

    private val cache = ConcurrentHashMap<StatsPeriod, StatsSummary>()

    override fun cached(period: StatsPeriod): StatsSummary? = cache[period]

    override suspend fun load(period: StatsPeriod): ApiResult<StatsSummary> =
        when (val result = remote(period)) {
            is ApiResult.Success -> {
                val summary = result.data.toDomain(period)
                cache[period] = summary
                ApiResult.Success(summary)
            }
            is ApiResult.Error -> ApiResult.Error(result.error)
        }

    fun clearCache() = cache.clear()
}
