package com.lumi.app.player

import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSnapshotCodecTest {

    private fun track(id: String) = Track(
        id = id, title = "Track $id", artist = Artist(id = "a$id", name = "Artist $id"), duration = 200
    )

    @Test
    fun `round trip keeps queue position and modes`() {
        val snapshot = PlaybackSnapshot(
            queue = listOf(track("1"), track("2"), track("3")),
            index = 1,
            positionMs = 42_500,
            shuffleEnabled = true,
            repeatMode = PlayerRepeatMode.ONE
        )

        val decoded = PlaybackSnapshotCodec.decode(PlaybackSnapshotCodec.encode(snapshot))!!

        assertEquals(listOf("1", "2", "3"), decoded.queue.map { it.id })
        assertEquals("Artist 2", decoded.queue[1].artist?.name)
        assertEquals(1, decoded.index)
        assertEquals(42_500L, decoded.positionMs)
        assertTrue(decoded.shuffleEnabled)
        assertEquals(PlayerRepeatMode.ONE, decoded.repeatMode)
    }

    @Test
    fun `null blank and corrupted json decode to null`() {
        assertNull(PlaybackSnapshotCodec.decode(null))
        assertNull(PlaybackSnapshotCodec.decode(""))
        assertNull(PlaybackSnapshotCodec.decode("{not json"))
        assertNull(PlaybackSnapshotCodec.decode("""{"queue":[],"index":0,"positionMs":0}"""))
    }

    @Test
    fun `out of range index and negative position are repaired`() {
        val json = PlaybackSnapshotCodec.encode(
            PlaybackSnapshot(listOf(track("1"), track("2")), index = 99, positionMs = -5,
                shuffleEnabled = false, repeatMode = PlayerRepeatMode.OFF)
        )
        val decoded = PlaybackSnapshotCodec.decode(json)!!
        assertEquals(1, decoded.index)
        assertEquals(0L, decoded.positionMs)
    }

    @Test
    fun `tracks with missing required fields are dropped`() {
        val json = """{"queue":[{"id":"1","title":"ok"},{"title":"no id"},{"id":"3"}],"index":0,"positionMs":10,"shuffleEnabled":false,"repeatMode":"ALL"}"""
        val decoded = PlaybackSnapshotCodec.decode(json)!!
        assertEquals(listOf("1"), decoded.queue.map { it.id })
        assertEquals(PlayerRepeatMode.ALL, decoded.repeatMode)
    }

    @Test
    fun `unknown repeat mode falls back to off`() {
        val json = """{"queue":[{"id":"1","title":"ok"}],"index":0,"positionMs":0,"shuffleEnabled":false}"""
        assertEquals(PlayerRepeatMode.OFF, PlaybackSnapshotCodec.decode(json)!!.repeatMode)
    }
}
