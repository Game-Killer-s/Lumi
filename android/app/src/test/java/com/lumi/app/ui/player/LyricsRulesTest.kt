package com.lumi.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Розбір тексту пісні: рядки та підсвічування «в такт» (ТЗ 6.3.11).
 */
class LyricsRulesTest {

    @Test
    fun `blank lines and indents are dropped`() {
        val lines = LyricsRules.lines("  Перший рядок  \n\n   \nДругий рядок")

        assertEquals(listOf("Перший рядок", "Другий рядок"), lines)
    }

    @Test
    fun `missing lyrics give no lines`() {
        assertTrue(LyricsRules.lines(null).isEmpty())
        assertTrue(LyricsRules.lines("   \n  \n").isEmpty())
        assertFalse(LyricsRules.hasLyrics(null))
        assertFalse(LyricsRules.hasLyrics("  "))
        assertTrue(LyricsRules.hasLyrics("Куплет"))
    }

    @Test
    fun `the first line is highlighted at the very beginning`() {
        assertEquals(0, LyricsRules.highlightedLine(progressFraction = 0f, lineCount = 10))
        assertEquals(0, LyricsRules.highlightedLine(progressFraction = -1f, lineCount = 10))
    }

    @Test
    fun `the last line is highlighted at the end`() {
        assertEquals(9, LyricsRules.highlightedLine(1f, 10))
        assertEquals(9, LyricsRules.highlightedLine(5f, 10))
    }

    @Test
    fun `progress in the middle highlights the matching line`() {
        // Індекс — це частка прослуханого, помножена на кількість рядків.
        assertEquals(5, LyricsRules.highlightedLine(0.55f, 10))
        assertEquals(2, LyricsRules.highlightedLine(0.2f, 10))
        assertEquals(0, LyricsRules.highlightedLine(0.5f, 1))
    }

    @Test
    fun `without lines nothing is highlighted`() {
        assertEquals(LyricsRules.NO_LINE, LyricsRules.highlightedLine(0.5f, 0))
        assertEquals(LyricsRules.NO_LINE, LyricsRules.highlightedLine(0.5f, -3))
    }
}
