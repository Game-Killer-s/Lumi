package com.lumi.app.ui.player

import com.lumi.app.R
import com.lumi.app.data.model.AddHistoryRequest
import com.lumi.app.data.model.Comment
import com.lumi.app.data.model.CommentListResponse
import com.lumi.app.data.model.CreateCommentRequest
import com.lumi.app.data.model.DeletedResponse
import com.lumi.app.data.model.Track
import com.lumi.app.data.model.TrackListResponse
import com.lumi.app.data.repository.CommentsRepository
import com.lumi.app.data.repository.TrackRepository
import com.lumi.app.network.CommentsApi
import com.lumi.app.network.TracksApi
import com.lumi.app.ui.i18n.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/** Методи, які цей сценарій не використовує. */
private fun <T> unsupported(): Response<T> = throw UnsupportedOperationException("not used")

/** Фейк треків: популярні, сам трек і помилка замість мережі. */
private class FakeTracksApi(
    private val popular: List<Track> = emptyList(),
    private val track: Track? = null,
    var fails: Boolean = false
) : TracksApi {

    override suspend fun getTracks(page: Int, limit: Int, genre: String?) =
        unsupported<TrackListResponse>()

    override suspend fun getTrack(trackId: String): Response<Track> {
        if (fails) {
            throw IllegalStateException("network is down")
        }

        return Response.success(track)
    }

    override suspend fun getPopularTracks(limit: Int): Response<TrackListResponse> =
        Response.success(TrackListResponse(tracks = popular))

    override suspend fun getNewTracks(limit: Int) = unsupported<TrackListResponse>()

    override suspend fun searchTracks(query: String, page: Int, limit: Int) =
        unsupported<TrackListResponse>()

    override suspend fun getLikedTracks(page: Int, limit: Int) = unsupported<TrackListResponse>()

    override suspend fun likeTrack(trackId: String) = unsupported<Unit>()

    override suspend fun unlikeTrack(trackId: String) = unsupported<Unit>()

    override suspend fun getHistory(page: Int, limit: Int) = unsupported<TrackListResponse>()

    override suspend fun addToHistory(request: AddHistoryRequest) = unsupported<Unit>()
}

/** Фейк лічильника коментарів: у плеєрі потрібне лише поле total. */
private class FakeCommentsCountApi(var total: Int = 0) : CommentsApi {

    var lastLimit: Int? = null

    override suspend fun getComments(
        trackId: String,
        page: Int,
        limit: Int
    ): Response<CommentListResponse> {
        lastLimit = limit

        return Response.success(
            CommentListResponse(comments = emptyList(), total = total, page = page, limit = limit)
        )
    }

    override suspend fun addComment(trackId: String, request: CreateCommentRequest) =
        unsupported<Comment>()

    override suspend fun deleteComment(commentId: String) = unsupported<DeletedResponse>()
}

/**
 * Плеєр: який трек відкриваємо, звідки береться лічильник коментарів
 * і як поводиться екран без звуку (ТЗ 6.3.11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @Before
    fun setUp() {
        // viewModelScope працює на Main — у тестах його підміняємо.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        tracks: FakeTracksApi,
        comments: FakeCommentsCountApi = FakeCommentsCountApi()
    ) = PlayerViewModel(TrackRepository(tracks), CommentsRepository(comments))

    private fun track(id: String, duration: Int = 214, audioUrl: String? = "https://cdn/$id.mp3") =
        Track(id = id, title = "Пісня", artist = null, duration = duration, audioUrl = audioUrl)

    @Test
    fun `id from the route is opened`() = runTest {
        val api = FakeTracksApi(popular = listOf(track("popular")), track = track("route"))
        val viewModel = viewModel(api)

        viewModel.load("route")

        assertEquals("route", viewModel.uiState.track?.id)
        assertEquals(false, viewModel.uiState.isLoading)
        assertNull(viewModel.uiState.error)
    }

    @Test
    fun `empty id opens the first popular track`() = runTest {
        val api = FakeTracksApi(popular = listOf(track("popular")), track = track("popular"))
        val viewModel = viewModel(api)

        viewModel.load(null)

        assertEquals("popular", viewModel.uiState.track?.id)
    }

    @Test
    fun `without any track the empty state is shown`() = runTest {
        val viewModel = viewModel(FakeTracksApi())

        viewModel.load(null)

        assertNull(viewModel.uiState.track)
        assertEquals(UiText.Res(R.string.player_error_no_tracks), viewModel.uiState.error)
    }

    @Test
    fun `comments counter comes from the backend total`() = runTest {
        val comments = FakeCommentsCountApi(total = 7)
        val api = FakeTracksApi(popular = listOf(track("popular")), track = track("popular"))
        val viewModel = viewModel(api, comments)

        viewModel.load(null)

        assertEquals(7, viewModel.uiState.commentsCount)
        // Лічильник не тягне весь список: достатньо одного елемента.
        assertEquals(1, comments.lastLimit)
    }

    @Test
    fun `counter is re-read after a comment was added or deleted`() = runTest {
        val comments = FakeCommentsCountApi(total = 7)
        val api = FakeTracksApi(popular = listOf(track("popular")), track = track("popular"))
        val viewModel = viewModel(api, comments)
        viewModel.load(null)

        comments.total = 8
        viewModel.refreshCommentsCount()

        assertEquals(8, viewModel.uiState.commentsCount)
    }

    @Test
    fun `track error is shown and retry keeps the route id`() = runTest {
        val api = FakeTracksApi(track = track("route"), fails = true)
        val viewModel = viewModel(api)

        viewModel.load("route")

        assertEquals(UiText.Res(R.string.player_error_track), viewModel.uiState.error)

        api.fails = false
        viewModel.retry()

        assertNull(viewModel.uiState.error)
        assertEquals("route", viewModel.uiState.track?.id)
    }

    @Test
    fun `progress feeds the progress fraction of the track`() = runTest {
        val api = FakeTracksApi(track = track("route", duration = 100))
        val viewModel = viewModel(api)
        viewModel.load("route")

        viewModel.onProgress(50)
        assertEquals(0.5f, viewModel.uiState.progressFraction, 0.001f)

        viewModel.onProgress(-10)
        assertEquals(0, viewModel.uiState.positionSeconds)
    }
}
