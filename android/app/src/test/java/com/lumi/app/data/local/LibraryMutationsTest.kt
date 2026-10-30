package com.lumi.app.data.local

import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.Playlist
import com.lumi.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMutationsTest {

    private fun track(id: String, duration: Int = 180) = Track(
        id = id,
        title = "Track $id",
        artist = null,
        duration = duration
    )

    private fun artist(id: String) = Artist(id = id, name = "Artist $id")

    @Test
    fun `create playlist trims title and hides empty description`() {
        val snapshot = LibraryMutations.createPlaylist(
            snapshot = LibrarySnapshot(),
            title = "  Road trip  ",
            description = "   ",
            now = 1_000L,
            id = "p1"
        )

        val playlist = snapshot.playlists.single()
        assertEquals("p1", playlist.id)
        assertEquals("Road trip", playlist.title)
        assertNull(playlist.description)
        assertFalse(playlist.isPublic)
        assertEquals(0, playlist.tracksCount)
    }

    @Test
    fun `update playlist changes metadata but keeps tracks`() {
        val original = LibrarySnapshot(
            playlists = listOf(
                Playlist(
                    id = "p1",
                    title = "Old",
                    description = "Old description",
                    tracks = listOf(track("t1")),
                    tracksCount = 1
                )
            )
        )

        val updated = LibraryMutations.updatePlaylist(original, "p1", " New ", "Fresh")

        val playlist = updated.playlists.single()
        assertEquals("New", playlist.title)
        assertEquals("Fresh", playlist.description)
        assertEquals(listOf("t1"), playlist.tracks.orEmpty().map { it.id })
    }

    @Test
    fun `add track ignores duplicates and recomputes count and duration`() {
        val started = LibraryMutations.createPlaylist(
            LibrarySnapshot(), "Mix", null, now = 1L, id = "p1"
        )
        val once = LibraryMutations.addTrack(started, "p1", track("t1", 100))
        val twice = LibraryMutations.addTrack(once, "p1", track("t1", 100))
        val second = LibraryMutations.addTrack(twice, "p1", track("t2", 50))

        val playlist = second.playlists.single()
        assertEquals(listOf("t1", "t2"), playlist.tracks.orEmpty().map { it.id })
        assertEquals(2, playlist.tracksCount)
        assertEquals(150, playlist.duration)
    }

    @Test
    fun `remove track updates count and duration`() {
        val started = LibraryMutations.createPlaylist(
            LibrarySnapshot(), "Mix", null, now = 1L, id = "p1"
        )
        val withTracks = LibraryMutations.addTrack(
            LibraryMutations.addTrack(started, "p1", track("t1", 100)),
            "p1",
            track("t2", 50)
        )

        val removed = LibraryMutations.removeTrack(withTracks, "p1", "t1")

        val playlist = removed.playlists.single()
        assertEquals(listOf("t2"), playlist.tracks.orEmpty().map { it.id })
        assertEquals(1, playlist.tracksCount)
        assertEquals(50, playlist.duration)
    }

    @Test
    fun `delete playlist removes only the requested one`() {
        val snapshot = LibrarySnapshot(
            playlists = listOf(
                Playlist(id = "p1", title = "One"),
                Playlist(id = "p2", title = "Two")
            )
        )

        val result = LibraryMutations.deletePlaylist(snapshot, "p1")

        assertEquals(listOf("p2"), result.playlists.map { it.id })
    }

    @Test
    fun `liking prepends the track and unlike removes it`() {
        val snapshot = LibrarySnapshot(likedTracks = listOf(track("old")))

        val liked = LibraryMutations.setLiked(snapshot, track("new"), liked = true)
        assertEquals(listOf("new", "old"), liked.likedTracks.map { it.id })
        assertTrue(liked.likedTracks.first().isLiked)

        val unliked = LibraryMutations.setLiked(liked, track("new"), liked = false)
        assertEquals(listOf("old"), unliked.likedTracks.map { it.id })
    }

    @Test
    fun `history moves a replayed track to the top without duplicates`() {
        val first = LibraryMutations.recordHistory(LibrarySnapshot(), track("t1"), 100L)
        val second = LibraryMutations.recordHistory(first, track("t2"), 200L)
        val replay = LibraryMutations.recordHistory(second, track("t1"), 300L)

        assertEquals(listOf("t1", "t2"), replay.history.map { it.track.id })
        assertEquals(300L, replay.history.first().playedAt)
    }

    @Test
    fun `history is capped at the limit keeping the newest entries`() {
        var snapshot = LibrarySnapshot()
        repeat(5) { index ->
            snapshot = LibraryMutations.recordHistory(
                snapshot = snapshot,
                track = track("t$index"),
                playedAt = index.toLong(),
                limit = 3
            )
        }

        assertEquals(listOf("t4", "t3", "t2"), snapshot.history.map { it.track.id })
    }

    @Test
    fun `clear history keeps the rest of the library`() {
        val snapshot = LibrarySnapshot(
            playlists = listOf(Playlist(id = "p1", title = "Mix")),
            history = listOf(HistoryEntry(track("t1"), 10L))
        )

        val cleared = LibraryMutations.clearHistory(snapshot)

        assertTrue(cleared.history.isEmpty())
        assertEquals(1, cleared.playlists.size)
    }

    @Test
    fun `unfollowed artist is not re-added by a stale server seed`() {
        val snapshot = LibrarySnapshot(followedArtists = listOf(artist("a1"), artist("a2")))
        val unfollowed = LibraryMutations.unfollowArtist(snapshot, "a1")

        assertEquals(listOf("a2"), unfollowed.followedArtists.map { it.id })
        assertTrue("a1" in unfollowed.unfollowedArtistIds)

        val seeded = LibraryMutations.seedFollowed(unfollowed, listOf(artist("a1"), artist("a3")))
        assertEquals(listOf("a2", "a3"), seeded.followedArtists.map { it.id }.sorted())
    }

    @Test
    fun `following an artist clears the local unfollow guard`() {
        val unfollowed = LibraryMutations.unfollowArtist(
            LibrarySnapshot(followedArtists = listOf(artist("a1"))),
            "a1"
        )

        val followed = LibraryMutations.followArtist(unfollowed, artist("a1"))

        assertEquals(listOf("a1"), followed.followedArtists.map { it.id })
        assertTrue(followed.unfollowedArtistIds.isEmpty())
    }
}
