package com.lumi.app.ui.player

import com.lumi.app.R
import com.lumi.app.data.model.LyricsResponse
import com.lumi.app.data.repository.LyricsRepository
import com.lumi.app.network.LyricsApi
import com.lumi.app.ui.i18n.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** Фейк мережі: текст пісні без HTTP (за замовчуванням — помилка мережі). */
private class FakeLyricsApi(
    var lyrics: String? = null,
    var fails: Boolean = false
) : LyricsApi {

    override suspend fun getTrackLyrics(trackId: String): Response<LyricsResponse> {
        if (fails) {
            throw IllegalStateException("network is down")
        }

        return Response.success(LyricsResponse(trackId = trackId, lyrics = lyrics))
    }
}

/**
 * Текст пісні: завантаження, порожній трек, помилка та підсвічування рядка.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModelTest {

    @Before
    fun setUp() {
        // viewModelScope працює на Main — у тестах його підміняємо.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakeLyricsApi) = LyricsViewModel(LyricsRepository(api))

    @Test
    fun `lyrics are split into lines and the first one is highlighted`() = runTest {
        val viewModel = viewModel(FakeLyricsApi(lyrics = "Перший рядок\n\n  Другий рядок "))

        viewModel.load("t1")

        assertEquals("t1", viewModel.uiState.trackId)
        assertEquals(listOf("Перший рядок", "Другий рядок"), viewModel.uiState.lines)
        assertFalse(viewModel.uiState.isLoading)
        assertNull(viewModel.uiState.error)
        assertEquals(0, viewModel.uiState.highlightedLine)
    }

    @Test
    fun `blank track id clears the previously loaded lyrics`() = runTest {
        val viewModel = viewModel(FakeLyricsApi(lyrics = "Куплет"))
        viewModel.load("t1")

        viewModel.load("   ")

        assertTrue(viewModel.uiState.lines.isEmpty())
        assertEquals("", viewModel.uiState.trackId)
        assertEquals(LyricsRules.NO_LINE, viewModel.uiState.highlightedLine)
    }

    @Test
    fun `lyrics without text show no lines but no error either`() = runTest {
        val viewModel = viewModel(FakeLyricsApi(lyrics = null))

        viewModel.load("t1")

        assertTrue(viewModel.uiState.lines.isEmpty())
        assertFalse(viewModel.uiState.isLoading)
        assertNull(viewModel.uiState.error)
    }

    @Test
    fun `network error is shown and retry re-reads the same track`() = runTest {
        val api = FakeLyricsApi(lyrics = "Куплет", fails = true)
        val viewModel = viewModel(api)

        viewModel.load("t1")

        assertEquals(UiText.Res(R.string.lyrics_error), viewModel.uiState.error)
        assertEquals("t1", viewModel.uiState.trackId)

        api.fails = false
        viewModel.retry()

        assertNull(viewModel.uiState.error)
        assertEquals(listOf("Куплет"), viewModel.uiState.lines)
    }

    @Test
    fun `progress highlights the matching line`() = runTest {
        val tenLines = (1..10).joinToString("\n") { "Рядок $it" }
        val viewModel = viewModel(FakeLyricsApi(lyrics = tenLines))
        viewModel.load("t1")

        viewModel.onProgress(0.5f)
        assertEquals(5, viewModel.uiState.highlightedLine)

        viewModel.onProgress(1f)
        assertEquals(9, viewModel.uiState.highlightedLine)
    }
}
