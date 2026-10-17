package com.lumi.app.ui

import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.StatsPeriod
import com.lumi.app.data.model.StatsSummary
import com.lumi.app.data.model.TopArtist
import com.lumi.app.data.model.TopGenre
import com.lumi.app.data.model.TopTrack
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.StatisticsSource
import com.lumi.app.network.ApiResult

object TestData {
    fun track(id: String) = Track(
        id = id, title = "Song $id", artist = Artist(id = "a$id", name = "Artist $id"), duration = 210
    )

    fun summary(period: StatsPeriod = StatsPeriod.WEEK, tracksPlayed: Int = 1234) = StatsSummary(
        period = period,
        tracksPlayed = tracksPlayed,
        listeningSeconds = 9_000,
        playlistsCount = 3,
        topTracks = listOf(TopTrack(track("1"), 30), TopTrack(track("2"), 12)),
        topArtists = listOf(TopArtist(Artist(id = "art1", name = "Top Band"), 50)),
        topGenres = listOf(TopGenre("Rock", 70), TopGenre("Pop", 20)),
        activity = emptyList()
    )

    /** Миттєве джерело без мережі для навігаційних тестів. */
    class InstantSource(private val byPeriod: Map<StatsPeriod, StatsSummary>) : StatisticsSource {
        private val cache = mutableMapOf<StatsPeriod, StatsSummary>()
        override fun cached(period: StatsPeriod) = cache[period]
        override suspend fun load(period: StatsPeriod): ApiResult<StatsSummary> {
            val s = byPeriod.getValue(period)
            cache[period] = s
            return ApiResult.Success(s)
        }
    }
}
