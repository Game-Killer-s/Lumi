package com.lumi.app.player

import com.lumi.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueBuilderTest {

    private fun track(id: String) = Track(
        id = id,
        title = "Track $id",
        artist = null,
        duration = 180
    )

    @Test
    fun `dedupeById removes repeated tracks keeping first occurrence`() {
        val a = track("a")
        val b = track("b")
        val aAgain = track("a") // same id, would be a distinct instance if it came from a fresh API response

        val result = QueueBuilder.dedupeById(listOf(a, b, aAgain, b))

        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `shuffledQueue never drops or duplicates a track`() {
        val tracks = (1..10).map { track("t$it") }

        val shuffled = QueueBuilder.shuffledQueue(tracks)

        assertEquals(tracks.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
        assertEquals(tracks.size, shuffled.size)
    }

    @Test
    fun `shuffledQueue dedupes duplicate ids before shuffling`() {
        val tracks = listOf(track("a"), track("b"), track("a"), track("c"))

        val shuffled = QueueBuilder.shuffledQueue(tracks)

        assertEquals(listOf("a", "b", "c").toSet(), shuffled.map { it.id }.toSet())
        assertEquals(3, shuffled.size)
    }

    @Test
    fun `shuffledQueue avoids reproducing original order for larger playlists`() {
        val tracks = (1..8).map { track("t$it") }

        val shuffled = QueueBuilder.shuffledQueue(tracks)

        assertNotEquals(tracks.map { it.id }, shuffled.map { it.id })
    }

    @Test
    fun `shuffledQueue on empty list returns empty list`() {
        assertTrue(QueueBuilder.shuffledQueue(emptyList()).isEmpty())
    }
}
