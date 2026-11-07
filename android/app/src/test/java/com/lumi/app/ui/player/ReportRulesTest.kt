package com.lumi.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Формування скарги: що саме летить модератору (ТЗ 6.3.11, ТЗ 6.7.2).
 */
class ReportRulesTest {

    // ---------- Деталі ----------

    @Test
    fun `empty details become null`() {
        assertNull(ReportRules.optionalComment(""))
        assertNull(ReportRules.optionalComment("   "))
    }

    @Test
    fun `details are trimmed but kept`() {
        assertEquals("Реклама і спам", ReportRules.optionalComment("  Реклама і спам "))
    }

    @Test
    fun `too long details are cut to the limit`() {
        val long = "a".repeat(ReportRules.COMMENT_MAX_LENGTH + 50)

        val comment = ReportRules.optionalComment(long)

        assertEquals(ReportRules.COMMENT_MAX_LENGTH, comment?.length)
    }

    // ---------- Чи можна надсилати ----------

    @Test
    fun `reason and target are both required`() {
        assertTrue(ReportRules.canSubmit(ReportReason.SPAM, "c1", isSending = false))
        assertFalse(ReportRules.canSubmit(null, "c1", isSending = false))
        assertFalse(ReportRules.canSubmit(ReportReason.SPAM, "   ", isSending = false))
    }

    @Test
    fun `request in flight blocks a second report`() {
        assertFalse(ReportRules.canSubmit(ReportReason.SPAM, "c1", isSending = true))
    }

    // ---------- Запит ----------

    @Test
    fun `report on a track points at the track`() {
        val request = ReportRules.build(
            target = ReportTarget.TRACK,
            trackId = "t1",
            targetId = "t1",
            reason = ReportReason.COPYRIGHT,
            details = "   "
        )

        assertEquals("track", request.target)
        assertEquals("t1", request.trackId)
        assertEquals("t1", request.targetId)
        assertEquals("copyright", request.reason)
        assertNull(request.comment)
    }

    @Test
    fun `report on a comment points at the comment and keeps the track`() {
        val request = ReportRules.build(
            target = ReportTarget.COMMENT,
            trackId = "t1",
            targetId = "c9",
            reason = ReportReason.OFFENSIVE,
            details = "Образи в тексті"
        )

        assertEquals("comment", request.target)
        assertEquals("c9", request.targetId)
        // Скарга завжди привʼязана до треку: у Report.trackId обовʼязковий.
        assertEquals("t1", request.trackId)
        assertEquals("offensive", request.reason)
        assertEquals("Образи в тексті", request.comment)
    }

    // ---------- Причини ----------

    @Test
    fun `every reason has its own value for the backend`() {
        val wireValues = ReportReason.values().map { it.wireValue }

        assertEquals(wireValues.size, wireValues.toSet().size)
        assertTrue(wireValues.all { it.isNotBlank() })
    }

    @Test
    fun `every reason has a label to show`() {
        // labelRes вказує на реальний рядок у strings.xml — інакше тут був би 0.
        assertTrue(ReportReason.values().all { it.labelRes != 0 })
    }
}
