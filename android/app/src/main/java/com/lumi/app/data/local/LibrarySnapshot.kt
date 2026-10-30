package com.lumi.app.data.local

import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.Playlist
import com.lumi.app.data.model.Track
import java.util.UUID

/** Один трек в історії прослуховувань. Час потрібен для сортування й підпису рядка. */
data class HistoryEntry(
    val track: Track,
    val playedAt: Long
)

/** Локальний знімок медіатеки користувача. */
data class LibrarySnapshot(
    val playlists: List<Playlist> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val followedArtists: List<Artist> = emptyList(),
    /** Локально скасовані підписки: захищають від повторного додавання зі stale-відповіді. */
    val unfollowedArtistIds: Set<String> = emptySet()
)

/**
 * Чисті операції над [LibrarySnapshot]. Виокремлені від Android/SharedPreferences,
 * щоб їх можна було перевірити звичайним JUnit-тестом.
 */
object LibraryMutations {

    /** Скільки останніх прослуховувань зберігаємо. */
    const val HISTORY_LIMIT = 100

    /** Назва плейлиста в UI не може бути порожньою або задовгою. */
    const val PLAYLIST_TITLE_MAX_LENGTH = 80
    const val PLAYLIST_DESCRIPTION_MAX_LENGTH = 300

    fun createPlaylist(
        snapshot: LibrarySnapshot,
        title: String,
        description: String?,
        now: Long,
        id: String = UUID.randomUUID().toString()
    ): LibrarySnapshot {
        val playlist = Playlist(
            id = id,
            title = title.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            isPublic = false,
            tracksCount = 0,
            createdAt = java.time.Instant.ofEpochMilli(now).toString()
        )
        return snapshot.copy(playlists = snapshot.playlists + playlist)
    }

    fun updatePlaylist(
        snapshot: LibrarySnapshot,
        playlistId: String,
        title: String,
        description: String?
    ): LibrarySnapshot = snapshot.copy(
        playlists = snapshot.playlists.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else {
                playlist.copy(
                    title = title.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
        }
    )

    fun deletePlaylist(snapshot: LibrarySnapshot, playlistId: String): LibrarySnapshot =
        snapshot.copy(playlists = snapshot.playlists.filterNot { it.id == playlistId })

    fun addTrack(snapshot: LibrarySnapshot, playlistId: String, track: Track): LibrarySnapshot =
        snapshot.copy(
            playlists = snapshot.playlists.map { playlist ->
                if (playlist.id != playlistId || playlist.tracks.orEmpty().any { it.id == track.id }) {
                    playlist
                } else {
                    val tracks = playlist.tracks.orEmpty() + track
                    playlist.withTracks(tracks)
                }
            }
        )

    fun removeTrack(snapshot: LibrarySnapshot, playlistId: String, trackId: String): LibrarySnapshot =
        snapshot.copy(
            playlists = snapshot.playlists.map { playlist ->
                if (playlist.id != playlistId) {
                    playlist
                } else {
                    playlist.withTracks(playlist.tracks.orEmpty().filterNot { it.id == trackId })
                }
            }
        )

    fun setLiked(snapshot: LibrarySnapshot, track: Track, liked: Boolean): LibrarySnapshot {
        val current = snapshot.likedTracks
        val without = current.filterNot { it.id == track.id }
        return snapshot.copy(
            likedTracks = if (liked) listOf(track.copy(isLiked = true)) + without else without
        )
    }

    /**
     * Повторне прослуховування того самого треку не дублює рядок,
     * а піднімає його на початок із новим часом.
     */
    fun recordHistory(
        snapshot: LibrarySnapshot,
        track: Track,
        playedAt: Long,
        limit: Int = HISTORY_LIMIT
    ): LibrarySnapshot {
        val rest = snapshot.history.filterNot { it.track.id == track.id }
        val history = (listOf(HistoryEntry(track, playedAt)) + rest)
            .sortedByDescending { it.playedAt }
            .take(limit.coerceAtLeast(1))
        return snapshot.copy(history = history)
    }

    fun clearHistory(snapshot: LibrarySnapshot): LibrarySnapshot = snapshot.copy(history = emptyList())

    fun followArtist(snapshot: LibrarySnapshot, artist: Artist): LibrarySnapshot =
        snapshot.copy(
            followedArtists = listOf(artist) +
                snapshot.followedArtists.filterNot { it.id == artist.id },
            unfollowedArtistIds = snapshot.unfollowedArtistIds - artist.id
        )

    fun unfollowArtist(snapshot: LibrarySnapshot, artistId: String): LibrarySnapshot =
        snapshot.copy(
            followedArtists = snapshot.followedArtists.filterNot { it.id == artistId },
            unfollowedArtistIds = snapshot.unfollowedArtistIds + artistId
        )

    /** Додає відомих виконавців, не втрачаючи вже збережених підписок. */
    fun seedFollowed(snapshot: LibrarySnapshot, artists: Collection<Artist>): LibrarySnapshot {
        val known = snapshot.followedArtists.associateBy { it.id }.toMutableMap()
        artists
            .filterNot { it.id in snapshot.unfollowedArtistIds }
            .forEach { artist -> known[artist.id] = artist }
        return snapshot.copy(followedArtists = known.values.toList())
    }

    private fun Playlist.withTracks(tracks: List<Track>): Playlist = copy(
        tracks = tracks,
        tracksCount = tracks.size,
        duration = tracks.sumOf { it.duration.coerceAtLeast(0) }
    )
}
