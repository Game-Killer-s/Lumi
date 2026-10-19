package com.lumi.app.data.model

import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.time.YearMonth

// ==================== STATISTICS ====================
// Контракт API описано в docs/statistics-api-contract.md.
// TODO(backend): ендпоінт GET /statistics/summary ще не реалізований.

/** Період, за який показується статистика. */
enum class StatsPeriod(val apiValue: String) {
    WEEK("week"),
    MONTH("month"),
    ALL_TIME("all")
}

// ---------- DTO (як приходить з бекенду; усі поля nullable, бо Gson ігнорує дефолти Kotlin) ----------

data class StatsSummaryDto(
    @SerializedName("tracksPlayed") val tracksPlayed: Int? = null,
    @SerializedName("listeningSeconds") val listeningSeconds: Long? = null,
    @SerializedName("playlistsCount") val playlistsCount: Int? = null,
    @SerializedName("topTracks") val topTracks: List<TopTrackDto?>? = null,
    @SerializedName("topArtists") val topArtists: List<TopArtistDto?>? = null,
    @SerializedName("topGenres") val topGenres: List<TopGenreDto?>? = null,
    @SerializedName("activity") val activity: List<ActivityPointDto?>? = null
)

data class TopTrackDto(
    @SerializedName("track") val track: Track? = null,
    @SerializedName("plays") val plays: Int? = null
)

data class TopArtistDto(
    @SerializedName("artist") val artist: Artist? = null,
    @SerializedName("plays") val plays: Int? = null
)

data class TopGenreDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("plays") val plays: Int? = null
)

data class ActivityPointDto(
    /** "yyyy-MM-dd" (день) або "yyyy-MM" (місяць, для періоду "all"). */
    @SerializedName("date") val date: String? = null,
    @SerializedName("plays") val plays: Int? = null,
    @SerializedName("seconds") val seconds: Long? = null
)

// ---------- Domain ----------

data class TopTrack(val track: Track, val plays: Int)
data class TopArtist(val artist: Artist, val plays: Int)
data class TopGenre(val name: String, val plays: Int)
data class ActivityPoint(val date: LocalDate, val plays: Int, val seconds: Long)

data class StatsSummary(
    val period: StatsPeriod,
    val tracksPlayed: Int,
    val listeningSeconds: Long,
    val playlistsCount: Int,
    val topTracks: List<TopTrack>,
    val topArtists: List<TopArtist>,
    val topGenres: List<TopGenre>,
    val activity: List<ActivityPoint>
) {
    val topArtist: TopArtist? get() = topArtists.firstOrNull()
    val topGenre: TopGenre? get() = topGenres.firstOrNull()

    /** Немає жодного прослуховування за період. */
    val isEmpty: Boolean get() = tracksPlayed == 0 && listeningSeconds == 0L
}

/**
 * DTO → domain. Мапер захищений від неповних відповідей: від'ємні числа
 * стають 0, пошкоджені записи відкидаються, топи сортуються за кількістю
 * прослуховувань (стабільно), активність — за датою.
 */
fun StatsSummaryDto.toDomain(period: StatsPeriod): StatsSummary {
    val topTracks = topTracks.orEmpty().mapNotNull { dto ->
        val track = dto?.track ?: return@mapNotNull null
        if (!track.hasValidId()) return@mapNotNull null
        TopTrack(track, (dto.plays ?: 0).coerceAtLeast(0))
    }.sortedByDescending { it.plays }

    val topArtists = topArtists.orEmpty().mapNotNull { dto ->
        val artist = dto?.artist ?: return@mapNotNull null
        if (!artist.hasValidId()) return@mapNotNull null
        TopArtist(artist, (dto.plays ?: 0).coerceAtLeast(0))
    }.sortedByDescending { it.plays }

    val topGenres = topGenres.orEmpty().mapNotNull { dto ->
        val name = dto?.name?.trim().orEmpty()
        if (name.isEmpty()) return@mapNotNull null
        TopGenre(name, (dto?.plays ?: 0).coerceAtLeast(0))
    }.sortedByDescending { it.plays }

    val activity = activity.orEmpty().mapNotNull { dto ->
        val date = parseStatsDate(dto?.date) ?: return@mapNotNull null
        ActivityPoint(
            date = date,
            plays = (dto?.plays ?: 0).coerceAtLeast(0),
            seconds = (dto?.seconds ?: 0L).coerceAtLeast(0L)
        )
    }.sortedBy { it.date }

    return StatsSummary(
        period = period,
        tracksPlayed = (tracksPlayed ?: 0).coerceAtLeast(0),
        listeningSeconds = (listeningSeconds ?: 0L).coerceAtLeast(0L),
        playlistsCount = (playlistsCount ?: 0).coerceAtLeast(0),
        topTracks = topTracks,
        topArtists = topArtists,
        topGenres = topGenres,
        activity = activity
    )
}

/** Приймає "yyyy-MM-dd" та "yyyy-MM" (перший день місяця); усе інше — null. */
internal fun parseStatsDate(raw: String?): LocalDate? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return try {
        LocalDate.parse(value)
    } catch (e: Exception) {
        try {
            YearMonth.parse(value).atDay(1)
        } catch (e2: Exception) {
            null
        }
    }
}

// Gson обходить null-безпеку Kotlin: id може виявитися null попри тип String.
@Suppress("SENSELESS_COMPARISON")
private fun Track.hasValidId(): Boolean = id != null && id.isNotBlank()

@Suppress("SENSELESS_COMPARISON")
private fun Artist.hasValidId(): Boolean = id != null && id.isNotBlank()
