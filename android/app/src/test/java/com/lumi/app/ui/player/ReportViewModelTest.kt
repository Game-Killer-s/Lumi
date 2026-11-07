package com.lumi.app.ui.player

import com.lumi.app.R
import com.lumi.app.data.model.Comment
import com.lumi.app.data.model.CreateReportRequest
import com.lumi.app.data.model.ReportResponse
import com.lumi.app.data.repository.ReportRepository
import com.lumi.app.network.ReportApi
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.uiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/** Фейк мережі скарг: запам'ятовує запити, вміє «зависати» й падати. */
private class FakeReportApi : ReportApi {

    val requests = mutableListOf<CreateReportRequest>()

    /** Коли true — відповідь чекає на [gate]: так перевіряємо стан «надсилається». */
    var pause = false
    var fails = false

    val gate = CompletableDeferred<Unit>()

    override suspend fun sendReport(request: CreateReportRequest): Response<ReportResponse> {
        requests += request

        if (pause) {
            gate.await()
        }

        if (fails) {
            throw IllegalStateException("network is down")
        }

        return Response.success(ReportResponse(id = "r1", status = "NEW"))
    }
}

/**
 * Діалог скарги: на що скаржимось, коли можна надсилати і що показуємо
 * після відповіді бекенду (ТЗ 6.3.11, ТЗ 6.7.2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakeReportApi = FakeReportApi()) =
        ReportViewModel(ReportRepository(api))

    private val foreignComment = Comment(id = "c9", text = "текст", userId = "u2")

    // ---------- Відкриття ----------

    @Test
    fun `a track report points at the track`() = runTest {
        val viewModel = viewModel()

        viewModel.openTrackReport("t1")

        assertTrue(viewModel.uiState.isOpen)
        assertEquals(ReportTarget.TRACK, viewModel.uiState.target)
        assertEquals("t1", viewModel.uiState.trackId)
        assertEquals("t1", viewModel.uiState.targetId)
        assertNull(viewModel.uiState.reason)
    }

    @Test
    fun `a report without a track does not open the dialog`() = runTest {
        val viewModel = viewModel()

        viewModel.openTrackReport("   ")

        assertFalse(viewModel.uiState.isOpen)
    }

    @Test
    fun `a comment report points at the comment`() = runTest {
        val viewModel = viewModel()

        viewModel.openCommentReport("t1", foreignComment)

        assertTrue(viewModel.uiState.isOpen)
        assertEquals(ReportTarget.COMMENT, viewModel.uiState.target)
        assertEquals("t1", viewModel.uiState.trackId)
        assertEquals("c9", viewModel.uiState.targetId)
    }

    // ---------- Чи можна надсилати ----------

    @Test
    fun `without a reason nothing is sent and the hint is shown`() = runTest {
        val api = FakeReportApi()
        val viewModel = viewModel(api)
        viewModel.openTrackReport("t1")

        assertFalse(viewModel.uiState.canSubmit)
        viewModel.submit()

        assertEquals(UiText.Res(R.string.report_error_reason_required), viewModel.uiState.error)
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `choosing a reason clears the hint and opens the submit`() = runTest {
        val viewModel = viewModel()
        viewModel.openTrackReport("t1")
        viewModel.submit()

        viewModel.selectReason(ReportReason.SPAM)

        assertNull(viewModel.uiState.error)
        assertTrue(viewModel.uiState.canSubmit)
    }

    @Test
    fun `details longer than the limit are flagged`() = runTest {
        val viewModel = viewModel()
        viewModel.openTrackReport("t1")

        viewModel.onDetailsChange("a".repeat(ReportRules.COMMENT_MAX_LENGTH + 1))

        assertTrue(viewModel.uiState.isDetailsTooLong)
    }

    // ---------- Відправка ----------

    @Test
    fun `a successful report closes the dialog and tells the user`() = runTest {
        val api = FakeReportApi()
        val viewModel = viewModel(api)
        viewModel.openCommentReport("t1", foreignComment)
        viewModel.selectReason(ReportReason.OFFENSIVE)
        viewModel.onDetailsChange("  Образи в тексті ")

        viewModel.submit()

        val request = api.requests.single()
        assertEquals("comment", request.target)
        assertEquals("c9", request.targetId)
        assertEquals("t1", request.trackId)
        assertEquals("offensive", request.reason)
        assertEquals("Образи в тексті", request.comment)

        assertFalse(viewModel.uiState.isOpen)
        assertEquals(
            ReportEvent.Message(uiText(R.string.report_sent)),
            viewModel.events.first()
        )
    }

    @Test
    fun `a failed report keeps the dialog open with the error`() = runTest {
        val api = FakeReportApi().apply { fails = true }
        val viewModel = viewModel(api)
        viewModel.openTrackReport("t1")
        viewModel.selectReason(ReportReason.OTHER)

        viewModel.submit()

        assertTrue(viewModel.uiState.isOpen)
        assertFalse(viewModel.uiState.isSending)
        assertEquals(UiText.Res(R.string.report_error_send), viewModel.uiState.error)
        assertEquals(1, api.requests.size)
    }

    @Test
    fun `the dialog cannot be dismissed while the report is in flight`() = runTest {
        val api = FakeReportApi().apply { pause = true }
        val viewModel = viewModel(api)
        viewModel.openTrackReport("t1")
        viewModel.selectReason(ReportReason.SPAM)

        viewModel.submit()
        assertTrue(viewModel.uiState.isSending)

        viewModel.dismiss()
        assertTrue(viewModel.uiState.isOpen)

        api.gate.complete(Unit)
        assertFalse(viewModel.uiState.isOpen)
    }

    @Test
    fun `dismiss wipes the whole report state`() = runTest {
        val viewModel = viewModel()
        viewModel.openCommentReport("t1", foreignComment)
        viewModel.selectReason(ReportReason.BAD_QUALITY)
        viewModel.onDetailsChange("шум")

        viewModel.dismiss()

        assertEquals(ReportUiState(), viewModel.uiState)
    }
}
