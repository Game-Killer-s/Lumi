package com.lumi.app.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LibraryStateHolder is a process-wide singleton; each test below uses
 * its own unique id(s) so tests can run in any order without clobbering
 * each other's state.
 */
class LibraryStateHolderTest {

    @Test
    fun `setLiked then isLiked reflects optimistic update`() {
        LibraryStateHolder.setLiked("track-1", true)
        assertTrue(LibraryStateHolder.isLiked("track-1"))

        LibraryStateHolder.setLiked("track-1", false)
        assertFalse(LibraryStateHolder.isLiked("track-1"))
    }

    @Test
    fun `revertLike restores previous state after a failed api call`() {
        LibraryStateHolder.setLiked("track-2", true) // optimistic like
        LibraryStateHolder.revertLike("track-2", wasLiked = false) // API call failed, roll back

        assertFalse(LibraryStateHolder.isLiked("track-2"))
    }

    @Test
    fun `seedLiked only adds ids marked liked, never removes existing state`() {
        LibraryStateHolder.setLiked("track-3", true)
        LibraryStateHolder.seedLiked(listOf("track-4"), liked = false)

        assertTrue(LibraryStateHolder.isLiked("track-3"))
        assertFalse(LibraryStateHolder.isLiked("track-4"))
    }

    @Test
    fun `follow state is independent from like state`() {
        LibraryStateHolder.setFollowed("artist-1", true)
        assertTrue(LibraryStateHolder.isFollowed("artist-1"))
        assertFalse(LibraryStateHolder.isLiked("artist-1"))

        LibraryStateHolder.revertFollow("artist-1", wasFollowed = false)
        assertFalse(LibraryStateHolder.isFollowed("artist-1"))
    }
}
