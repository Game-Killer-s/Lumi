package com.lumi.app.ui.player

import com.lumi.app.data.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила коментарів: ліміт довжини, пагінація, дублікати та право
 * видаляти лише свій коментар (ТЗ 6.3.11, ТЗ 6.7.2).
 */
class CommentsRulesTest {

    private fun comment(id: String, userId: String = "u1", isHidden: Boolean = false) =
        Comment(id = id, text = "Класна пісня", userId = userId, isHidden = isHidden)

    // ---------- Чернетка ----------

    @Test
    fun `draft must be not blank to be sent`() {
        assertTrue(CommentsRules.canSend("Класна пісня", isSending = false))
        assertFalse(CommentsRules.canSend("    ", isSending = false))
    }

    @Test
    fun `draft at the limit is fine and longer is not`() {
        assertTrue(
            CommentsRules.canSend("a".repeat(CommentsRules.MAX_LENGTH), isSending = false)
        )
        assertFalse(
            CommentsRules.canSend("a".repeat(CommentsRules.MAX_LENGTH + 1), isSending = false)
        )
    }

    @Test
    fun `the request in flight blocks a second tap`() {
        assertFalse(CommentsRules.canSend("Класна пісня", isSending = true))
    }

    @Test
    fun `too long draft is flagged for the hint under the field`() {
        assertTrue(CommentsRules.isTooLong("a".repeat(CommentsRules.MAX_LENGTH + 1)))
        assertFalse(CommentsRules.isTooLong("a".repeat(CommentsRules.MAX_LENGTH)))
    }

    // ---------- Пагінація ----------

    @Test
    fun `next page is loaded only when the backend has more`() {
        assertTrue(CommentsRules.canLoadMore(loaded = 20, total = 35, isLoadingMore = false))
        assertFalse(CommentsRules.canLoadMore(loaded = 20, total = 20, isLoadingMore = false))
        assertFalse(CommentsRules.canLoadMore(loaded = 35, total = 35, isLoadingMore = false))
    }

    @Test
    fun `loading in flight does not start a second page`() {
        assertFalse(CommentsRules.canLoadMore(loaded = 20, total = 35, isLoadingMore = true))
    }

    // ---------- Чий коментар ----------

    @Test
    fun `only the comment of the current user is own`() {
        assertTrue(CommentsRules.isOwn(comment("c1", userId = "u1"), currentUserId = "u1"))
        assertFalse(CommentsRules.isOwn(comment("c1", userId = "u2"), currentUserId = "u1"))
    }

    @Test
    fun `without a signed in user nothing is own`() {
        assertFalse(CommentsRules.isOwn(comment("c1", userId = "u1"), currentUserId = null))
        assertFalse(CommentsRules.isOwn(comment("c1", userId = "u1"), currentUserId = "  "))
    }

    // ---------- Злиття сторінок ----------

    @Test
    fun `duplicates are not added twice`() {
        val existing = listOf(comment("c1"), comment("c2"))

        val merged = CommentsRules.merge(existing, listOf(comment("c2"), comment("c3")))

        assertEquals(listOf("c1", "c2", "c3"), merged.map { it.id })
    }

    @Test
    fun `hidden comments are never shown`() {
        val merged = CommentsRules.merge(
            existing = emptyList(),
            page = listOf(comment("c1"), comment("c2", isHidden = true))
        )

        assertEquals(listOf("c1"), merged.map { it.id })
    }

    @Test
    fun `a page of hidden comments keeps the existing list`() {
        val existing = listOf(comment("c1"))

        val merged = CommentsRules.merge(existing, listOf(comment("c2", isHidden = true)))

        assertEquals(listOf("c1"), merged.map { it.id })
    }

    @Test
    fun `visible count ignores hidden comments`() {
        val comments = listOf(comment("c1"), comment("c2", isHidden = true))

        assertEquals(1, CommentsRules.visibleCount(comments))
        assertEquals(0, CommentsRules.visibleCount(emptyList()))
    }
}
