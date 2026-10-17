package com.lumi.app.ui.player

import com.lumi.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила плеєра: який трек відкриваємо, чи є що програвати, скільки триває
 * та яка частка прогресу (ТЗ 6.3.11).
 */
class PlayerRulesTest {

    private fun track(
        id: String = "t1",
        duration: Int = 214,
        audioUrl: String? = "https://cdn.lumi/t1.mp3"
    ) = Track(id = id, title = "Пісня", artist = null, duration = duration, audioUrl = audioUrl)

    // ---------- Який трек відкриваємо ----------

    @Test
    fun `id from the route wins over the fallback`() {
        assertEquals("route-track", PlayerRules.resolveTrackId("route-track", "popular-track"))
    }

    @Test
    fun `blank id falls back to the first popular track`() {
        assertEquals("popular-track", PlayerRules.resolveTrackId(null, "popular-track"))
        assertEquals("popular-track", PlayerRules.resolveTrackId("   ", "popular-track"))
    }

    @Test
    fun `without both ids there is nothing to open`() {
        assertNull(PlayerRules.resolveTrackId(null, null))
        assertNull(PlayerRules.resolveTrackId("", "  "))
    }

    // ---------- Чи є що програвати ----------

    @Test
    fun `track with an audio url can be played`() {
        assertTrue(PlayerRules.hasAudio(track()))
    }

    @Test
    fun `track without an audio url shows metadata only`() {
        assertFalse(PlayerRules.hasAudio(track(audioUrl = null)))
        assertFalse(PlayerRules.hasAudio(track(audioUrl = "   ")))
        assertFalse(PlayerRules.hasAudio(null))
    }

    // ---------- Тривалість і прогрес ----------

    @Test
    fun `duration from the database is shown as minutes and seconds`() {
        // У моделі duration — секунди (у seed: 214), а форматер працює з мс.
        assertEquals("3:34", PlayerRules.durationLabel(214))
        assertEquals("0:00", PlayerRules.durationLabel(0))
        assertEquals("1:05:03", PlayerRules.durationLabel(3903))
    }

    @Test
    fun `progress fraction is clamped to the track duration`() {
        assertEquals(0.5f, PlayerRules.progressFraction(107, 214), 0.001f)
        assertEquals(1f, PlayerRules.progressFraction(500, 214), 0.001f)
        assertEquals(0f, PlayerRules.progressFraction(-30, 214), 0.001f)
    }

    @Test
    fun `unknown duration keeps the progress at zero`() {
        assertEquals(0f, PlayerRules.progressFraction(30, 0), 0.001f)
    }
}
