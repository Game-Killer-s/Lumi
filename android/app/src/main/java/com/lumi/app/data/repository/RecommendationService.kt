package com.lumi.app.data.repository

import com.lumi.app.data.model.Track

data class RankingConfig(
    val version: String = "v1.0",
    val weightRecentListens: Float = 1.5f,
    val weightLikes: Float = 2.0f,
    val weightFollows: Float = 1.2f,
    val weightGenreAffinity: Float = 1.0f,
    val artistRepeatPenalty: Float = 0.5f
)

/**
 * Service responsible for candidate scoring, ranking, filtering (excluding
 * unavailable/blocked tracks), cold-start fallback, artist repetition limiting,
 * and My Mix generation with artist and genre diversity rules.
 */
object RecommendationService {

    fun scoreAndRank(
        candidates: List<Track>,
        likedTrackIds: Set<String> = emptySet(),
        followedArtistIds: Set<String> = emptySet(),
        favoriteGenres: Set<String> = emptySet(),
        config: RankingConfig = RankingConfig()
    ): List<Track> {
        val filtered = candidates.filter { track ->
            !track.title.contains("blocked", ignoreCase = true) &&
                !track.title.contains("unavailable", ignoreCase = true)
        }

        val scored = filtered.map { track ->
            var score = 0f
            if (track.id in likedTrackIds) score += config.weightLikes
            track.artist?.id?.let { artistId ->
                if (artistId in followedArtistIds) score += config.weightFollows
            }
            track.genre?.let { genre ->
                if (genre in favoriteGenres) score += config.weightGenreAffinity
            }
            score += track.playCount * 0.1f * config.weightRecentListens
            track to score
        }

        val artistCounts = mutableMapOf<String, Int>()
        val ranked = scored.sortedByDescending { it.second }.map { (track, score) ->
            val artistId = track.artist?.id ?: track.artistName ?: "unknown"
            val count = artistCounts.getOrDefault(artistId, 0)
            artistCounts[artistId] = count + 1
            val penalizedScore = score - (count * config.artistRepeatPenalty)
            track to penalizedScore
        }.sortedByDescending { it.second }.map { it.first }

        return ranked
    }

    fun generateMyMix(
        candidates: List<Track>,
        targetSize: Int = 20,
        likedTrackIds: Set<String> = emptySet(),
        followedArtistIds: Set<String> = emptySet(),
        favoriteGenres: Set<String> = emptySet(),
        config: RankingConfig = RankingConfig()
    ): List<Track> {
        val ranked = scoreAndRank(candidates, likedTrackIds, followedArtistIds, favoriteGenres, config)
        val mix = mutableListOf<Track>()
        val artistsInMix = mutableMapOf<String, Int>()
        val genresInMix = mutableMapOf<String, Int>()

        for (track in ranked) {
            if (mix.size >= targetSize) break
            val artistId = track.artist?.id ?: track.artistName ?: "unknown"
            val genre = track.genre ?: "pop"

            val artistCount = artistsInMix.getOrDefault(artistId, 0)
            val genreCount = genresInMix.getOrDefault(genre, 0)

            // Diversity rules: max 2 tracks per artist, max 6 tracks per genre in My Mix
            if (artistCount < 2 && genreCount < 6) {
                mix.add(track)
                artistsInMix[artistId] = artistCount + 1
                genresInMix[genre] = genreCount + 1
            }
        }

        if (mix.size < targetSize) {
            for (track in ranked) {
                if (mix.size >= targetSize) break
                if (track !in mix) mix.add(track)
            }
        }

        return mix
    }
}
