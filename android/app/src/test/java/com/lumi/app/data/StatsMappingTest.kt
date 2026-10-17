package com.lumi.app.data

import com.google.gson.Gson
import com.lumi.app.data.model.ActivityPointDto
import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.StatsPeriod
import com.lumi.app.data.model.StatsSummaryDto
import com.lumi.app.data.model.TopArtistDto
import com.lumi.app.data.model.TopGenreDto
import com.lumi.app.data.model.TopTrackDto
import com.lumi.app.data.model.Track
import com.lumi.app.data.model.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsMappingTest {

    private fun track(id: String) = Track(id = id, title = "T$id", artist = null)
    private fun artist(id: String) = Artist(id = id, name = "A$id")

    @Test
    fun `maps a full response`() {
        val dto = StatsSummaryDto(
            tracksPlayed = 120, listeningSeconds = 7_200, playlistsCount = 4,
            topTracks = listOf(TopTrackDto(track("1"), 30)),
            topArtists = listOf(TopArtistDto(artist("a1"), 50)),
            topGenres = listOf(TopGenreDto("Rock", 70)),
            activity = listOf(ActivityPointDto("2026-10-05", 12, 2_400))
        )

        val s = dto.toDomain(StatsPeriod.WEEK)

        assertEquals(StatsPeriod.WEEK, s.period)
        assertEquals(120, s.tracksPlayed)
        assertEquals(7_200L, s.listeningSeconds)
        assertEquals(4, s.playlistsCount)
        assertEquals("a1", s.topArtist?.artist?.id)
        assertEquals("Rock", s.topGenre?.name)
        assertEquals(LocalDate.of(2026, 10, 5), s.activity.single().date)
        assertFalse(s.isEmpty)
    }

    @Test
    fun `empty dto becomes an empty summary`() {
        val s = StatsSummaryDto().toDomain(StatsPeriod.MONTH)
        assertTrue(s.isEmpty)
        assertNull(s.topArtist)
        assertNull(s.topGenre)
        assertTrue(s.topTracks.isEmpty())
    }

    @Test
    fun `negative numbers are clamped to zero`() {
        val s = StatsSummaryDto(
            tracksPlayed = -4, listeningSeconds = -9, playlistsCount = -1,
            topGenres = listOf(TopGenreDto("Pop", -3))
        ).toDomain(StatsPeriod.WEEK)
        assertEquals(0, s.tracksPlayed)
        assertEquals(0L, s.listeningSeconds)
        assertEquals(0, s.playlistsCount)
        assertEquals(0, s.topGenres.single().plays)
    }

    @Test
    fun `broken entries are dropped`() {
        val dto = StatsSummaryDto(
            tracksPlayed = 3,
            topTracks = listOf(null, TopTrackDto(null, 5), TopTrackDto(track("ok"), 2)),
            topArtists = listOf(TopArtistDto(null, 1), null, TopArtistDto(artist("good"), 9)),
            topGenres = listOf(TopGenreDto(null, 1), TopGenreDto("  ", 2), TopGenreDto(" Jazz ", 3)),
            activity = listOf(ActivityPointDto("not-a-date", 1, 1), ActivityPointDto(null, 1, 1), null)
        )
        val s = dto.toDomain(StatsPeriod.WEEK)
        assertEquals(listOf("ok"), s.topTracks.map { it.track.id })
        assertEquals(listOf("good"), s.topArtists.map { it.artist.id })
        assertEquals(listOf("Jazz"), s.topGenres.map { it.name })
        assertTrue(s.activity.isEmpty())
    }

    @Test
    fun `tops are sorted by plays descending and top item is the first`() {
        val dto = StatsSummaryDto(
            topArtists = listOf(TopArtistDto(artist("low"), 2), TopArtistDto(artist("high"), 20)),
            topGenres = listOf(TopGenreDto("Pop", 4), TopGenreDto("Rock", 40)),
            topTracks = listOf(TopTrackDto(track("a"), 1), TopTrackDto(track("b"), 8))
        )
        val s = dto.toDomain(StatsPeriod.ALL_TIME)
        assertEquals("high", s.topArtist?.artist?.id)
        assertEquals("Rock", s.topGenre?.name)
        assertEquals(listOf("b", "a"), s.topTracks.map { it.track.id })
    }

    @Test
    fun `activity is sorted by date and accepts day and month formats`() {
        val dto = StatsSummaryDto(
            activity = listOf(
                ActivityPointDto("2026-10-07", 1, 10),
                ActivityPointDto("2026-09", 5, 50),
                ActivityPointDto("2026-10-01", 2, 20)
            )
        )
        val dates = dto.toDomain(StatsPeriod.ALL_TIME).activity.map { it.date }
        assertEquals(
            listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7)),
            dates
        )
    }

    @Test
    fun `summary with plays or time is not empty`() {
        assertFalse(StatsSummaryDto(tracksPlayed = 2, listeningSeconds = 0).toDomain(StatsPeriod.WEEK).isEmpty)
        assertFalse(StatsSummaryDto(tracksPlayed = 0, listeningSeconds = 30).toDomain(StatsPeriod.WEEK).isEmpty)
    }

    @Test
    fun `parses the documented json contract`() {
        val json = """
            {
              "tracksPlayed": 42,
              "listeningSeconds": 9000,
              "playlistsCount": 3,
              "topTracks": [{"track": {"id": "t1", "title": "Song", "audioUrl": "http://x/a.mp3"}, "plays": 7}],
              "topArtists": [{"artist": {"id": "a1", "name": "Band"}, "plays": 12}],
              "topGenres": [{"name": "Rock", "plays": 20}],
              "activity": [{"date": "2026-10-05", "plays": 5, "seconds": 900}]
            }
        """.trimIndent()

        val s = Gson().fromJson(json, StatsSummaryDto::class.java).toDomain(StatsPeriod.WEEK)

        assertEquals(42, s.tracksPlayed)
        assertEquals("http://x/a.mp3", s.topTracks.single().track.audioUrl)
        assertEquals("Band", s.topArtist?.artist?.name)
        assertEquals(7, s.topTracks.single().plays)
    }

    @Test
    fun `json with missing fields still parses`() {
        val s = Gson().fromJson("{}", StatsSummaryDto::class.java).toDomain(StatsPeriod.WEEK)
        assertTrue(s.isEmpty)
    }

    @Test
    fun `period api values match the contract`() {
        assertEquals("week", StatsPeriod.WEEK.apiValue)
        assertEquals("month", StatsPeriod.MONTH.apiValue)
        assertEquals("all", StatsPeriod.ALL_TIME.apiValue)
    }
}
