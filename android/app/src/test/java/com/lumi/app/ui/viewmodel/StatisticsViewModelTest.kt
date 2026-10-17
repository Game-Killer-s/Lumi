package com.lumi.app.ui.viewmodel

import com.lumi.app.data.model.StatsPeriod
import com.lumi.app.data.model.StatsSummary
import com.lumi.app.data.repository.StatisticsSource
import com.lumi.app.network.ApiError
import com.lumi.app.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * ViewModel-тести без coroutines-test/MockK/Turbine: диспетчер Unconfined +
 * ручне завершення CompletableDeferred дають повністю детерміновану
 * послідовність подій.
 */
class StatisticsViewModelTest {

    private fun summary(period: StatsPeriod, tracks: Int = 5) = StatsSummary(
        period = period, tracksPlayed = tracks, listeningSeconds = tracks * 200L, playlistsCount = 2,
        topTracks = emptyList(), topArtists = emptyList(), topGenres = emptyList(), activity = emptyList()
    )

    /** Fake, де кожен виклик load() чекає на ручне завершення. */
    private class FakeSource(private val ignoreCancellation: Boolean = false) : StatisticsSource {
        val cache = mutableMapOf<StatsPeriod, StatsSummary>()
        val requests = mutableListOf<StatsPeriod>()
        val pending = mutableMapOf<StatsPeriod, MutableList<CompletableDeferred<ApiResult<StatsSummary>>>>()

        override fun cached(period: StatsPeriod) = cache[period]

        override suspend fun load(period: StatsPeriod): ApiResult<StatsSummary> {
            requests += period
            val d = CompletableDeferred<ApiResult<StatsSummary>>()
            pending.getOrPut(period) { mutableListOf() }.add(d)
            // ignoreCancellation моделює джерело, яке не реагує на cancel() і все одно повертає відповідь.
            val result = if (ignoreCancellation) withContext(NonCancellable) { d.await() } else d.await()
            if (result is ApiResult.Success) cache[period] = result.data
            return result
        }

        fun complete(period: StatsPeriod, result: ApiResult<StatsSummary>, index: Int = 0) {
            pending.getValue(period)[index].complete(result)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun vm(source: FakeSource, initial: StatsPeriod = StatsPeriod.WEEK) =
        StatisticsViewModel(source, initial, scope)

    @Test
    fun `starts in loading with the initial period and requests it`() {
        val source = FakeSource()
        val vm = vm(source)

        assertEquals(StatisticsStatus.LOADING, vm.uiState.value.status)
        assertEquals(StatsPeriod.WEEK, vm.uiState.value.selectedPeriod)
        assertEquals(listOf(StatsPeriod.WEEK), source.requests)
    }

    @Test
    fun `success moves to content with the summary`() {
        val source = FakeSource()
        val vm = vm(source)
        val data = summary(StatsPeriod.WEEK, 12)

        source.complete(StatsPeriod.WEEK, ApiResult.Success(data))

        val s = vm.uiState.value
        assertEquals(StatisticsStatus.CONTENT, s.status)
        assertSame(data, s.summary)
        assertFalse(s.isRefreshing)
        assertNull(s.errorMessage)
    }

    @Test
    fun `summary without activity is the empty state`() {
        val source = FakeSource()
        val vm = vm(source)

        source.complete(StatsPeriod.WEEK, ApiResult.Success(summary(StatsPeriod.WEEK, tracks = 0)))

        assertEquals(StatisticsStatus.EMPTY, vm.uiState.value.status)
    }

    @Test
    fun `failure moves to error with the message and retry recovers`() {
        val source = FakeSource()
        val vm = vm(source)

        source.complete(StatsPeriod.WEEK, ApiResult.Error(ApiError.NETWORK_ERROR))
        assertEquals(StatisticsStatus.ERROR, vm.uiState.value.status)
        assertEquals(ApiError.NETWORK_ERROR.message, vm.uiState.value.errorMessage)

        vm.retry()
        assertEquals(StatisticsStatus.LOADING, vm.uiState.value.status)
        assertNull(vm.uiState.value.errorMessage)

        source.complete(StatsPeriod.WEEK, ApiResult.Success(summary(StatsPeriod.WEEK)), index = 1)
        assertEquals(StatisticsStatus.CONTENT, vm.uiState.value.status)
        assertEquals(2, source.requests.size)
    }

    @Test
    fun `switching period requests it and shows local loading while keeping the selection`() {
        val source = FakeSource()
        val vm = vm(source)
        source.complete(StatsPeriod.WEEK, ApiResult.Success(summary(StatsPeriod.WEEK)))

        vm.selectPeriod(StatsPeriod.MONTH)

        val s = vm.uiState.value
        assertEquals(StatsPeriod.MONTH, s.selectedPeriod)
        assertEquals(StatisticsStatus.LOADING, s.status)
        assertNull(s.summary) // дані іншого періоду не показуємо під новою вкладкою
        assertEquals(listOf(StatsPeriod.WEEK, StatsPeriod.MONTH), source.requests)
    }

    @Test
    fun `selecting the same period again does nothing`() {
        val source = FakeSource()
        val vm = vm(source)
        source.complete(StatsPeriod.WEEK, ApiResult.Success(summary(StatsPeriod.WEEK)))

        vm.selectPeriod(StatsPeriod.WEEK)

        assertEquals(1, source.requests.size)
        assertEquals(StatisticsStatus.CONTENT, vm.uiState.value.status)
    }

    @Test
    fun `returning to a cached period shows cached data immediately and refreshes`() {
        val source = FakeSource()
        val vm = vm(source)
        val week = summary(StatsPeriod.WEEK, 11)
        source.complete(StatsPeriod.WEEK, ApiResult.Success(week))
        vm.selectPeriod(StatsPeriod.MONTH)
        source.complete(StatsPeriod.MONTH, ApiResult.Success(summary(StatsPeriod.MONTH, 40)))

        vm.selectPeriod(StatsPeriod.WEEK)

        val immediate = vm.uiState.value
        assertEquals(StatisticsStatus.CONTENT, immediate.status)
        assertSame(week, immediate.summary)
        assertEquals(true, immediate.isRefreshing)

        val fresh = summary(StatsPeriod.WEEK, 13)
        source.complete(StatsPeriod.WEEK, ApiResult.Success(fresh), index = 1)
        assertSame(fresh, vm.uiState.value.summary)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `failed refresh of cached period keeps content and reports the error`() {
        val source = FakeSource()
        val vm = vm(source)
        val week = summary(StatsPeriod.WEEK)
        source.complete(StatsPeriod.WEEK, ApiResult.Success(week))
        vm.selectPeriod(StatsPeriod.MONTH)
        source.complete(StatsPeriod.MONTH, ApiResult.Success(summary(StatsPeriod.MONTH)))
        vm.selectPeriod(StatsPeriod.WEEK)

        source.complete(StatsPeriod.WEEK, ApiResult.Error(ApiError.TIMEOUT_ERROR), index = 1)

        val s = vm.uiState.value
        assertEquals(StatisticsStatus.CONTENT, s.status)
        assertSame(week, s.summary)
        assertFalse(s.isRefreshing)
        assertEquals(ApiError.TIMEOUT_ERROR.message, s.errorMessage)
    }

    @Test
    fun `rapid switching week month all ignores stale responses`() {
        val source = FakeSource()
        val vm = vm(source)

        vm.selectPeriod(StatsPeriod.MONTH)
        vm.selectPeriod(StatsPeriod.ALL_TIME)
        assertEquals(StatsPeriod.ALL_TIME, vm.uiState.value.selectedPeriod)

        // Відповіді приходять у зворотному порядку й із запізненням.
        val all = summary(StatsPeriod.ALL_TIME, 99)
        source.complete(StatsPeriod.ALL_TIME, ApiResult.Success(all))
        source.complete(StatsPeriod.MONTH, ApiResult.Success(summary(StatsPeriod.MONTH, 7)))
        source.complete(StatsPeriod.WEEK, ApiResult.Success(summary(StatsPeriod.WEEK, 3)))

        val s = vm.uiState.value
        assertEquals(StatsPeriod.ALL_TIME, s.selectedPeriod)
        assertSame(all, s.summary)
        assertEquals(StatisticsStatus.CONTENT, s.status)
    }

    @Test
    fun `stale error does not clobber the newly selected period`() {
        val source = FakeSource()
        val vm = vm(source)
        vm.selectPeriod(StatsPeriod.MONTH)
        val month = summary(StatsPeriod.MONTH, 8)
        source.complete(StatsPeriod.MONTH, ApiResult.Success(month))

        source.complete(StatsPeriod.WEEK, ApiResult.Error(ApiError.NETWORK_ERROR))

        assertEquals(StatisticsStatus.CONTENT, vm.uiState.value.status)
        assertSame(month, vm.uiState.value.summary)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `selected period survives an error`() {
        val source = FakeSource()
        val vm = vm(source)
        vm.selectPeriod(StatsPeriod.MONTH)

        source.complete(StatsPeriod.MONTH, ApiResult.Error(ApiError.UNKNOWN_ERROR))

        assertEquals(StatsPeriod.MONTH, vm.uiState.value.selectedPeriod)
        assertEquals(StatisticsStatus.ERROR, vm.uiState.value.status)
    }

    @Test
    fun `reselecting the failed period retries it`() {
        val source = FakeSource()
        val vm = vm(source)
        source.complete(StatsPeriod.WEEK, ApiResult.Error(ApiError.NETWORK_ERROR))

        vm.selectPeriod(StatsPeriod.WEEK)

        assertEquals(StatisticsStatus.LOADING, vm.uiState.value.status)
        assertEquals(2, source.requests.size)
    }

    @Test
    fun `cached initial period is shown right away`() {
        val source = FakeSource()
        val cached = summary(StatsPeriod.WEEK, 21)
        source.cache[StatsPeriod.WEEK] = cached

        val vm = vm(source)

        assertSame(cached, vm.uiState.value.summary)
        assertEquals(StatisticsStatus.CONTENT, vm.uiState.value.status)
        assertNotNull(source.pending[StatsPeriod.WEEK]) // і паралельно оновлюється
    }

    @Test
    fun `initial period other than week is honoured`() {
        val source = FakeSource()
        val vm = vm(source, StatsPeriod.ALL_TIME)

        assertEquals(StatsPeriod.ALL_TIME, vm.uiState.value.selectedPeriod)
        assertEquals(listOf(StatsPeriod.ALL_TIME), source.requests)
    }

    // ---- Захист від застарілих відповідей незалежно від скасування корутини ----

    @Test
    fun `stale success from a source that ignores cancellation is dropped`() {
        val source = FakeSource(ignoreCancellation = true)
        val vm = vm(source)

        vm.selectPeriod(StatsPeriod.MONTH)
        val month = summary(StatsPeriod.MONTH, 8)
        source.complete(StatsPeriod.MONTH, ApiResult.Success(month))
        // Запит WEEK не скасувався і завершується пізніше.
        source.complete(StatsPeriod.WEEK, ApiResult.Success(summary(StatsPeriod.WEEK, 3)))

        assertEquals(StatsPeriod.MONTH, vm.uiState.value.selectedPeriod)
        assertSame(month, vm.uiState.value.summary)
    }

    @Test
    fun `stale error from a source that ignores cancellation is dropped`() {
        val source = FakeSource(ignoreCancellation = true)
        val vm = vm(source)

        vm.selectPeriod(StatsPeriod.MONTH)
        source.complete(StatsPeriod.WEEK, ApiResult.Error(ApiError.NETWORK_ERROR))

        assertEquals(StatsPeriod.MONTH, vm.uiState.value.selectedPeriod)
        assertEquals(StatisticsStatus.LOADING, vm.uiState.value.status)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `rapid week month all with non cancellable source ends on all time`() {
        val source = FakeSource(ignoreCancellation = true)
        val vm = vm(source)

        vm.selectPeriod(StatsPeriod.MONTH)
        vm.selectPeriod(StatsPeriod.ALL_TIME)
        val all = summary(StatsPeriod.ALL_TIME, 99)
        source.complete(StatsPeriod.ALL_TIME, ApiResult.Success(all))
        source.complete(StatsPeriod.MONTH, ApiResult.Success(summary(StatsPeriod.MONTH, 7)))
        source.complete(StatsPeriod.WEEK, ApiResult.Success(summary(StatsPeriod.WEEK, 3)))

        assertEquals(StatsPeriod.ALL_TIME, vm.uiState.value.selectedPeriod)
        assertSame(all, vm.uiState.value.summary)
    }
}
