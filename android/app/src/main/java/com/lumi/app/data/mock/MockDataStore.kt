package com.lumi.app.data.mock

import com.lumi.app.data.model.*

/**
 * Local deterministic data source used while the backend is unavailable.
 * It intentionally mirrors the API contracts so switching to the real API
 * does not require UI changes.
 */
object MockDataStore {

    const val ENABLED = false

    private val artistsSeed = listOf(
        Artist("artist-1", "Luna Vale", bio = "Атмосферний pop та synthwave", monthlyListeners = 128_400, isFollowed = true),
        Artist("artist-2", "Neon District", bio = "Нічний electronic із глибоким басом", monthlyListeners = 94_200),
        Artist("artist-3", "Mira North", bio = "Indie / alternative з м'яким вокалом", monthlyListeners = 73_800),
        Artist("artist-4", "Echo Bloom", bio = "Dream pop і теплі гітарні текстури", monthlyListeners = 51_600),
        Artist("artist-5", "Astral Wave", bio = "Космічний ambient та chillout", monthlyListeners = 42_100),
        Artist("artist-6", "Solaris", bio = "Енергійний dance та club beats", monthlyListeners = 88_900)
    )

    private val artists = artistsSeed.toMutableList()

    private fun baseTracks(): List<Track> = listOf(
        Track("track-1", "Afterglow", artist = artists[0], artistName = artists[0].name, album = "Night Drive", duration = 218, genre = "Synthwave", playCount = 184_000, likesCount = 12_400, isLiked = true),
        Track("track-2", "Midnight Radio", artist = artists[1], artistName = artists[1].name, album = "Signal Lost", duration = 201, genre = "Electronic", playCount = 152_000, likesCount = 8_900),
        Track("track-3", "Paper Skies", artist = artists[2], artistName = artists[2].name, album = "Northbound", duration = 233, genre = "Indie", playCount = 96_000, likesCount = 7_200, isLiked = true),
        Track("track-4", "Static Hearts", artist = artists[1], artistName = artists[1].name, album = "Signal Lost", duration = 187, genre = "Electronic", playCount = 87_000, likesCount = 6_100),
        Track("track-5", "Slow Motion", artist = artists[3], artistName = artists[3].name, album = "Soft Focus", duration = 245, genre = "Dream Pop", playCount = 74_000, likesCount = 5_400),
        Track("track-6", "Parallel Lines", artist = artists[0], artistName = artists[0].name, album = "Night Drive", duration = 226, genre = "Synthwave", playCount = 68_000, likesCount = 4_800),
        Track("track-7", "Blue Hour", artist = artists[2], artistName = artists[2].name, album = "Northbound", duration = 214, genre = "Indie", playCount = 59_000, likesCount = 4_100),
        Track("track-8", "Glass City", artist = artists[3], artistName = artists[3].name, album = "Soft Focus", duration = 198, genre = "Dream Pop", playCount = 48_000, likesCount = 3_700),
        Track("track-9", "Voltage", artist = artists[1], artistName = artists[1].name, album = "Signal Lost", duration = 206, genre = "Electronic", playCount = 43_000, likesCount = 3_200),
        Track("track-10", "Homebound", artist = artists[0], artistName = artists[0].name, album = "Night Drive", duration = 252, genre = "Pop", playCount = 39_000, likesCount = 2_900),
        Track("track-11", "Stardust", artist = artists[4], artistName = artists[4].name, album = "Stellar", duration = 265, genre = "Ambient", playCount = 31_000, likesCount = 2_100),
        Track("track-12", "Nebula", artist = artists[4], artistName = artists[4].name, album = "Stellar", duration = 240, genre = "Ambient", playCount = 28_000, likesCount = 1_800),
        Track("track-13", "Pulse", artist = artists[5], artistName = artists[5].name, album = "Supernova", duration = 195, genre = "Electronic", playCount = 110_000, likesCount = 9_500),
        Track("track-14", "Adrenaline", artist = artists[5], artistName = artists[5].name, album = "Supernova", duration = 188, genre = "Electronic", playCount = 95_000, likesCount = 8_200),
        Track("track-15", "Golden Daze", artist = artists[2], artistName = artists[2].name, album = "Northbound", duration = 210, genre = "Indie", playCount = 82_000, likesCount = 6_400)
    )

    private val tracks = baseTracks().toMutableList()

    private val albums = mutableListOf(
        Album("album-1", "Night Drive", artist = artists[0], artistName = artists[0].name, releaseDate = "2026", tracks = tracks.filter { it.album == "Night Drive" }, tracksCount = 3, duration = 696),
        Album("album-2", "Signal Lost", artist = artists[1], artistName = artists[1].name, releaseDate = "2026", tracks = tracks.filter { it.album == "Signal Lost" }, tracksCount = 3, duration = 594),
        Album("album-3", "Northbound", artist = artists[2], artistName = artists[2].name, releaseDate = "2025", tracks = tracks.filter { it.album == "Northbound" }, tracksCount = 2, duration = 447),
        Album("album-4", "Soft Focus", artist = artists[3], artistName = artists[3].name, releaseDate = "2025", tracks = tracks.filter { it.album == "Soft Focus" }, tracksCount = 2, duration = 443)
    )

    private val genres = listOf(
        Genre("genre-1", "Pop"),
        Genre("genre-2", "Electronic"),
        Genre("genre-3", "Indie"),
        Genre("genre-4", "Synthwave"),
        Genre("genre-5", "Dream Pop")
    )

    private val user = UserProfile("user-1", "Game Killer", "demo@lumi.local", role = "listener", createdAt = "2026-01-10")

    private val playlists = mutableListOf(
        Playlist(
            id = "playlist-1",
            title = "Вечірній драйв",
            description = "Музика для дороги після заходу сонця",
            owner = user,
            ownerName = user.username,
            tracks = listOf(tracks[0], tracks[1], tracks[3], tracks[5]),
            tracksCount = 4,
            duration = tracks[0].duration + tracks[1].duration + tracks[3].duration + tracks[5].duration,
            isPublic = true,
            createdAt = "2026-08-12"
        ),
        Playlist(
            id = "playlist-2",
            title = "Спокійний ранок",
            description = "Повільний старт дня",
            owner = user,
            ownerName = user.username,
            tracks = listOf(tracks[2], tracks[4], tracks[6], tracks[7]),
            tracksCount = 4,
            duration = tracks[2].duration + tracks[4].duration + tracks[6].duration + tracks[7].duration,
            isPublic = false,
            createdAt = "2026-07-04"
        )
    )

    private val history = mutableListOf<Track>()
    private val likedIds = mutableSetOf("track-1", "track-3")
    private val followedIds = mutableSetOf("artist-1")
    private var currentUser: UserProfile = user

    private fun normalizedTrack(track: Track): Track = track.copy(isLiked = track.id in likedIds, likesCount = track.likesCount + if (track.id in likedIds && !track.isLiked) 1 else 0)

    fun allTracks(limit: Int = 20): TrackListResponse {
        val data = tracks.map(::normalizedTrack).take(limit)
        return TrackListResponse(data, total = tracks.size, page = 1, limit = limit)
    }

    fun popular(limit: Int): TrackListResponse = TrackListResponse(
        tracks.sortedByDescending { it.playCount }.map(::normalizedTrack).take(limit),
        total = tracks.size, page = 1, limit = limit
    )

    fun newReleases(limit: Int): TrackListResponse = TrackListResponse(
        tracks.asReversed().map(::normalizedTrack).take(limit),
        total = tracks.size, page = 1, limit = limit
    )

    fun searchTracks(query: String, page: Int, limit: Int): TrackListResponse {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) tracks else tracks.filter {
            it.title.lowercase().contains(q) ||
                (it.artist?.name ?: it.artistName.orEmpty()).lowercase().contains(q) ||
                it.album.orEmpty().lowercase().contains(q) ||
                it.genre.orEmpty().lowercase().contains(q)
        }
        return TrackListResponse(filtered.map(::normalizedTrack).drop((page - 1) * limit).take(limit), filtered.size, page, limit)
    }

    fun track(id: String): Track? = tracks.find { it.id == id }?.let(::normalizedTrack)

    fun liked(page: Int, limit: Int): TrackListResponse {
        val data = tracks.filter { it.id in likedIds }.map(::normalizedTrack)
        return TrackListResponse(data.drop((page - 1) * limit).take(limit), data.size, page, limit)
    }

    fun like(id: String) { likedIds += id }
    fun unlike(id: String) { likedIds -= id }

    fun history(page: Int, limit: Int): TrackListResponse {
        val data = history.map(::normalizedTrack)
        return TrackListResponse(data.drop((page - 1) * limit).take(limit), data.size, page, limit)
    }

    fun addHistory(id: String) {
        track(id)?.let { t ->
            history.removeAll { it.id == t.id }
            history.add(0, t)
            while (history.size > 30) history.removeAt(history.lastIndex)
        }
    }

    fun getArtists(page: Int, limit: Int): ArtistListResponse = ArtistListResponse(
        artists.map { it.copy(isFollowed = it.id in followedIds) }.drop((page - 1) * limit).take(limit),
        artists.size, page, limit
    )

    fun artist(id: String): Artist? = artists.find { it.id == id }?.copy(isFollowed = id in followedIds)
    fun followArtist(id: String) { followedIds += id }
    fun unfollowArtist(id: String) { followedIds -= id }
    fun genres(): GenreListResponse = GenreListResponse(genres)

    fun albums(page: Int, limit: Int): AlbumListResponse = AlbumListResponse(albums.drop((page - 1) * limit).take(limit), albums.size, page, limit)
    fun album(id: String): Album? = albums.find { it.id == id }

    fun playlists(page: Int, limit: Int): PlaylistListResponse = PlaylistListResponse(
        playlists.drop((page - 1) * limit).take(limit), playlists.size, page, limit
    )
    fun playlist(id: String): Playlist? = playlists.find { it.id == id }

    fun createPlaylist(title: String, description: String?, isPublic: Boolean): Playlist {
        val playlist = Playlist(
            id = "playlist-${System.currentTimeMillis()}",
            title = title.trim(),
            description = description?.trim().takeUnless { it.isNullOrEmpty() },
            owner = currentUser,
            ownerName = currentUser.username,
            tracks = emptyList(), tracksCount = 0, duration = 0, isPublic = isPublic, createdAt = "2026-09-21"
        )
        playlists.add(0, playlist)
        return playlist
    }

    fun updatePlaylist(id: String, title: String?, description: String?, isPublic: Boolean?): Playlist? {
        val index = playlists.indexOfFirst { it.id == id }
        if (index < 0) return null
        val current = playlists[index]
        val updated = current.copy(
            title = title ?: current.title,
            description = description ?: current.description,
            isPublic = isPublic ?: current.isPublic
        )
        playlists[index] = updated
        return updated
    }

    fun deletePlaylist(id: String): Boolean = playlists.removeAll { it.id == id }

    fun addTrackToPlaylist(playlistId: String, trackId: String): Playlist? {
        val index = playlists.indexOfFirst { it.id == playlistId }
        val track = track(trackId) ?: return null
        if (index < 0) return null
        val current = playlists[index]
        val list = current.tracks.orEmpty()
        val updatedTracks = if (list.any { it.id == track.id }) list else list + track
        return current.copy(tracks = updatedTracks, tracksCount = updatedTracks.size, duration = updatedTracks.sumOf { it.duration }).also { playlists[index] = it }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String): Playlist? {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return null
        val current = playlists[index]
        val updatedTracks = current.tracks.orEmpty().filterNot { it.id == trackId }
        return current.copy(tracks = updatedTracks, tracksCount = updatedTracks.size, duration = updatedTracks.sumOf { it.duration }).also { playlists[index] = it }
    }

    fun profile(): UserProfile = currentUser
    fun registerUser(username: String, email: String): UserProfile {
        currentUser = currentUser.copy(username = username.trim(), email = email.trim())
        playlists.replaceAll { it.copy(owner = currentUser, ownerName = currentUser.username) }
        return currentUser
    }

    fun updateProfile(username: String?, avatarUrl: String?): UserProfile {
        currentUser = currentUser.copy(username = username?.trim().takeUnless { it.isNullOrEmpty() } ?: currentUser.username, avatarUrl = avatarUrl ?: currentUser.avatarUrl)
        playlists.replaceAll { it.copy(owner = currentUser, ownerName = currentUser.username) }
        return currentUser
    }

    fun searchEverything(query: String): Triple<List<Track>, List<Artist>, List<Album>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        val matchingTracks = tracks.filter { it.title.lowercase().contains(q) || (it.artist?.name ?: "").lowercase().contains(q) || it.album.orEmpty().lowercase().contains(q) }.map(::normalizedTrack)
        val matchingArtists = artists.filter { it.name.lowercase().contains(q) }.map { it.copy(isFollowed = it.id in followedIds) }
        val matchingAlbums = albums.filter { it.title.lowercase().contains(q) || (it.artist?.name ?: "").lowercase().contains(q) }
        return Triple(matchingTracks, matchingArtists, matchingAlbums)
    }
}
