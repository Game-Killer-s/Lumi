package com.lumi.app.ui.player

import com.lumi.app.R
import com.lumi.app.data.model.Comment
import com.lumi.app.data.model.CommentListResponse
import com.lumi.app.data.model.CreateCommentRequest
import com.lumi.app.data.model.DeletedResponse
import com.lumi.app.data.repository.CommentsRepository
import com.lumi.app.network.CommentsApi
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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

/** Фейк мережі коментарів: сторінки, лічильник і прапорці помилок. */
private class FakeCommentsApi(
    var pages: Map<Int, List<Comment>> = emptyMap(),
    var total: Int = 0
) : CommentsApi {

    val requestedPages = mutableListOf<Int>()
    val sentTexts = mutableListOf<String>()
    var deletedId: String? = null

    var loadFails = false
    var sendFails = false
    var deleteFails = false

    override suspend fun getComments(
        trackId: String,
        page: Int,
        limit: Int
    ): Response<CommentListResponse> {
        requestedPages += page

        if (loadFails) {
            throw IllegalStateException("network is down")
        }

        return Response.success(
            CommentListResponse(
                comments = pages[page].orEmpty(),
                total = total,
                page = page,
                limit = limit
            )
        )
    }

    override suspend fun addComment(
        trackId: String,
        request: CreateCommentRequest
    ): Response<Comment> {
        sentTexts += request.text

        if (sendFails) {
            throw IllegalStateException("network is down")
        }

        return Response.success(
            Comment(id = "new", text = request.text, userId = "u1", trackId = trackId)
        )
    }

    override suspend fun deleteComment(commentId: String): Response<DeletedResponse> {
        if (deleteFails) {
            throw IllegalStateException("network is down")
        }

        deletedId = commentId

        return Response.success(DeletedResponse(deleted = true))
    }
}

/**
 * Шторка коментарів: пагінація, надсилання, видалення лише свого
 * та помилки мережі (ТЗ 6.3.11, ТЗ 6.7.2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakeCommentsApi) =
        CommentsViewModel(CommentsRepository(api), currentUserId = "u1")

    private fun comment(id: String, userId: String = "u1") =
        Comment(id = id, text = "Коментар $id", userId = userId, trackId = "t1")

    // ---------- Завантаження ----------

    @Test
    fun `open loads the first page with the backend total`() = runTest {
        val api = FakeCommentsApi(
            pages = mapOf(1 to listOf(comment("c1"), comment("c2"))),
            total = 5
        )
        val viewModel = viewModel(api)

        viewModel.open("t1")

        assertEquals(listOf(1), api.requestedPages)
        assertEquals(listOf("c1", "c2"), viewModel.uiState.comments.map { it.id })
        assertEquals(5, viewModel.uiState.total)
        assertEquals(1, viewModel.uiState.page)
        assertFalse(viewModel.uiState.isLoading)
        assertTrue(viewModel.uiState.canLoadMore)
    }

    @Test
    fun `opening the same track again does not re-read the list`() = runTest {
        val api = FakeCommentsApi(pages = mapOf(1 to listOf(comment("c1"))), total = 1)
        val viewModel = viewModel(api)
        viewModel.open("t1")

        viewModel.open("t1")

        assertEquals(listOf(1), api.requestedPages)
    }

    @Test
    fun `a blank track does not load anything`() = runTest {
        val api = FakeCommentsApi()
        val viewModel = viewModel(api)

        viewModel.open("   ")

        assertTrue(api.requestedPages.isEmpty())
    }

    @Test
    fun `network error is shown and retry re-reads the first page`() = runTest {
        val api = FakeCommentsApi(pages = mapOf(1 to listOf(comment("c1"))), total = 1)
        api.loadFails = true
        val viewModel = viewModel(api)

        viewModel.open("t1")

        assertEquals(UiText.Res(R.string.comments_error), viewModel.uiState.error)

        api.loadFails = false
        viewModel.retry()

        assertNull(viewModel.uiState.error)
        assertEquals(listOf("c1"), viewModel.uiState.comments.map { it.id })
    }

    @Test
    fun `load more appends the next page`() = runTest {
        val api = FakeCommentsApi(
            pages = mapOf(
                1 to listOf(comment("c1"), comment("c2")),
                2 to listOf(comment("c3"))
            ),
            total = 3
        )
        val viewModel = viewModel(api)
        viewModel.open("t1")

        viewModel.loadMore()

        assertEquals(listOf(1, 2), api.requestedPages)
        assertEquals(listOf("c1", "c2", "c3"), viewModel.uiState.comments.map { it.id })
        assertEquals(2, viewModel.uiState.page)
        assertFalse(viewModel.uiState.canLoadMore)
    }

    @Test
    fun `the same comment is not added twice after a reload`() = runTest {
        val api = FakeCommentsApi(
            pages = mapOf(1 to listOf(comment("c1")), 2 to listOf(comment("c1"))),
            total = 5
        )
        val viewModel = viewModel(api)
        viewModel.open("t1")

        viewModel.loadMore()

        assertEquals(listOf("c1"), viewModel.uiState.comments.map { it.id })
    }

    // ---------- Новий коментар ----------

    @Test
    fun `a new comment goes to the top and the draft is cleared`() = runTest {
        val api = FakeCommentsApi(pages = mapOf(1 to listOf(comment("c1"))), total = 1)
        val viewModel = viewModel(api)
        viewModel.open("t1")
        viewModel.onDraftChange("  Новий коментар  ")

        viewModel.send()

        assertEquals(listOf("Новий коментар"), api.sentTexts)
        assertEquals(listOf("new", "c1"), viewModel.uiState.comments.map { it.id })
        assertEquals("", viewModel.uiState.draft)
        assertEquals(2, viewModel.uiState.total)
        assertFalse(viewModel.uiState.isSending)
        assertEquals(
            listOf(CommentsEvent.Message(uiText(R.string.comments_added)), CommentsEvent.Changed),
            viewModel.events.take(2).toList()
        )
    }

    @Test
    fun `an empty draft is not sent`() = runTest {
        val api = FakeCommentsApi()
        val viewModel = viewModel(api)
        viewModel.open("t1")

        viewModel.onDraftChange("   ")
        viewModel.send()

        assertTrue(api.sentTexts.isEmpty())
    }

    @Test
    fun `too long draft is flagged and not sent`() = runTest {
        val api = FakeCommentsApi()
        val viewModel = viewModel(api)
        viewModel.open("t1")

        viewModel.onDraftChange("a".repeat(CommentsRules.MAX_LENGTH + 1))

        assertTrue(viewModel.uiState.isDraftTooLong)
        assertFalse(viewModel.uiState.canSend)

        viewModel.send()
        assertTrue(api.sentTexts.isEmpty())
    }

    @Test
    fun `a failed send keeps the draft with the error`() = runTest {
        val api = FakeCommentsApi().apply { sendFails = true }
        val viewModel = viewModel(api)
        viewModel.open("t1")
        viewModel.onDraftChange("Новий коментар")

        viewModel.send()

        assertEquals(UiText.Res(R.string.comments_error_send), viewModel.uiState.error)
        assertEquals("Новий коментар", viewModel.uiState.draft)
        assertFalse(viewModel.uiState.isSending)
    }

    // ---------- Видалення ----------

    @Test
    fun `own comment asks for confirmation and is removed after it`() = runTest {
        val own = comment("c1", userId = "u1")
        val api = FakeCommentsApi(pages = mapOf(1 to listOf(own, comment("c2", "u2"))), total = 2)
        val viewModel = viewModel(api)
        viewModel.open("t1")

        viewModel.requestDelete(own)
        assertEquals(own, viewModel.uiState.pendingDelete)

        viewModel.confirmDelete()

        assertEquals("c1", api.deletedId)
        assertNull(viewModel.uiState.pendingDelete)
        assertEquals(listOf("c2"), viewModel.uiState.comments.map { it.id })
        assertEquals(1, viewModel.uiState.total)
        assertEquals(
            listOf(CommentsEvent.Message(uiText(R.string.comments_deleted)), CommentsEvent.Changed),
            viewModel.events.take(2).toList()
        )
    }

    @Test
    fun `a foreign comment cannot be deleted`() = runTest {
        val foreign = comment("c2", userId = "u2")
        val api = FakeCommentsApi(pages = mapOf(1 to listOf(foreign)), total = 1)
        val viewModel = viewModel(api)
        viewModel.open("t1")

        viewModel.requestDelete(foreign)

        assertNull(viewModel.uiState.pendingDelete)
        // Чужий коментар — це привід для скарги, а не для видалення.
        assertFalse(viewModel.isOwn(foreign))
        assertTrue(viewModel.isOwn(comment("c1", userId = "u1")))
    }

    @Test
    fun `cancelling the confirmation keeps the comment`() = runTest {
        val own = comment("c1")
        val api = FakeCommentsApi(pages = mapOf(1 to listOf(own)), total = 1)
        val viewModel = viewModel(api)
        viewModel.open("t1")

        viewModel.requestDelete(own)
        viewModel.cancelDelete()

        assertNull(viewModel.uiState.pendingDelete)
        assertEquals(listOf("c1"), viewModel.uiState.comments.map { it.id })
    }

    @Test
    fun `a failed delete keeps the comment and shows the error`() = runTest {
        val own = comment("c1")
        val api = FakeCommentsApi(pages = mapOf(1 to listOf(own)), total = 1)
        val viewModel = viewModel(api)
        viewModel.open("t1")

        viewModel.requestDelete(own)
        api.deleteFails = true
        viewModel.confirmDelete()

        assertEquals(listOf("c1"), viewModel.uiState.comments.map { it.id })
        assertEquals(UiText.Res(R.string.comments_error_delete), viewModel.uiState.error)
        assertNull(viewModel.uiState.deletingId)
    }

    @Test
    fun `a dismissed error leaves the list as is`() = runTest {
        val api = FakeCommentsApi(pages = mapOf(1 to listOf(comment("c1"))), total = 1)
        api.loadFails = true
        val viewModel = viewModel(api)
        viewModel.open("t1")

        viewModel.dismissError()

        assertNull(viewModel.uiState.error)
    }
}
