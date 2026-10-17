package com.lumi.app.data.repository

import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationServiceTest {

    @Test
    fun `scores and ranks candidates excluding blocked tracks`() {
        val tracks = listOf(
            Track(id = "1", title = "Normal Track", artist = Artist("a1", "Artist 1"), genre = "Rock", playCount = 5),
            Track(id = "2", title = "Unavailable Song", artist = Artist("a1", "Artist 1"), genre = "Rock", playCount = 10),
            Track(id = "3", title = "Liked Track", artist = Artist("a2", "Artist 2"), genre = "Pop", playCount = 1)
        )

        val ranked = RecommendationService.scoreAndRank(
            candidates = tracks,
            likedTrackIds = setOf("3"),
            followedArtistIds = setOf("a1"),
            favoriteGenres = setOf("Rock")
        )

        // "Unavailable Song" should be filtered out
        assertTrue(ranked.none { it.id == "2" })
        assertEquals(2, ranked.size)
    }

    @Test
    fun `generates My Mix respecting diversity and size`() {
        val tracks = (1..30).map { i ->
            Track(
                id = "$i",
                title = "Track $i",
                artist = Artist("a${i % 5}", "Artist ${i % 5}"),
                genre = if (i % 2 == 0) "Rock" else "Pop",
                playCount = i
            )
        }

        val myMix = RecommendationService.generateMyMix(
            candidates = tracks,
            targetSize = 10
        )

        assertEquals(10, myMix.size)
        // Check artist diversity: no artist should exceed 2 tracks
        val artistCounts = myMix.groupBy { it.artist?.id }.mapValues { it.value.size }
        for ((_, count) in artistCounts) {
            assertTrue(count <= 2)
        }
    }
}
