package com.lumi.app.data.repository

import com.lumi.app.data.model.StatsPeriod
import com.lumi.app.data.model.StatsSummaryDto
import com.lumi.app.network.ApiError
import com.lumi.app.network.ApiResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsRepositoryTest {

    private val requested = mutableListOf<StatsPeriod>()
    private var response: ApiResult<StatsSummaryDto> = ApiResult.Success(StatsSummaryDto(tracksPlayed = 1))

    private fun repo() = StatisticsRepository { period ->
        requested += period
        response
    }

    @Test
    fun `nothing is cached before the first load`() {
        assertNull(repo().cached(StatsPeriod.WEEK))
    }

    @Test
    fun `successful load fills the cache for that period only`() = runBlocking {
        val r = repo()
        response = ApiResult.Success(StatsSummaryDto(tracksPlayed = 10))

        val loaded = (r.load(StatsPeriod.WEEK) as ApiResult.Success).data

        assertSame(loaded, r.cached(StatsPeriod.WEEK))
        assertEquals(10, r.cached(StatsPeriod.WEEK)?.tracksPlayed)
        assertNull(r.cached(StatsPeriod.MONTH))
        assertNull(r.cached(StatsPeriod.ALL_TIME))
    }

    @Test
    fun `the period is passed to the remote call`() = runBlocking {
        val r = repo()
        r.load(StatsPeriod.WEEK); r.load(StatsPeriod.MONTH); r.load(StatsPeriod.ALL_TIME)
        assertEquals(listOf(StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.ALL_TIME), requested)
    }

    @Test
    fun `cache keeps the latest result per period`() = runBlocking {
        val r = repo()
        response = ApiResult.Success(StatsSummaryDto(tracksPlayed = 1))
        r.load(StatsPeriod.WEEK)
        response = ApiResult.Success(StatsSummaryDto(tracksPlayed = 2))
        r.load(StatsPeriod.WEEK)
        assertEquals(2, r.cached(StatsPeriod.WEEK)?.tracksPlayed)
    }

    @Test
    fun `failed load does not overwrite the cache`() = runBlocking {
        val r = repo()
        response = ApiResult.Success(StatsSummaryDto(tracksPlayed = 5))
        r.load(StatsPeriod.MONTH)
        response = ApiResult.Error(ApiError.NETWORK_ERROR)

        val result = r.load(StatsPeriod.MONTH)

        assertTrue(result is ApiResult.Error)
        assertEquals(5, r.cached(StatsPeriod.MONTH)?.tracksPlayed)
    }

    @Test
    fun `failed first load leaves the cache empty and forwards the error`() = runBlocking {
        val r = repo()
        response = ApiResult.Error(ApiError.TIMEOUT_ERROR)

        val result = r.load(StatsPeriod.WEEK) as ApiResult.Error

        assertEquals(ApiError.TIMEOUT_ERROR, result.error)
        assertNull(r.cached(StatsPeriod.WEEK))
    }

    @Test
    fun `clearCache drops everything`() = runBlocking {
        val r = repo()
        r.load(StatsPeriod.WEEK)
        r.clearCache()
        assertNull(r.cached(StatsPeriod.WEEK))
    }

    @Test
    fun `loaded summary carries the requested period`() = runBlocking {
        val summary = (repo().load(StatsPeriod.ALL_TIME) as ApiResult.Success).data
        assertEquals(StatsPeriod.ALL_TIME, summary.period)
    }
}
