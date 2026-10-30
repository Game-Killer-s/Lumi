package com.lumi.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.lumi.app.LumiApplication
import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.Playlist
import com.lumi.app.data.model.Track
import com.lumi.app.network.TokenManager
import com.lumi.app.state.LibraryStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Постійне сховище медіатеки на пристрої.
 *
 * Для плейлистів, історії та підписок у контракті backend немає робочих
 * endpoint'ів, тому вони живуть локально. Лайки дублюються тут, щоб серце
 * не «злітало» після перезапуску застосунку. Дані розділені за id користувача.
 */
object LibraryLocalStore {

    private const val PREFS_NAME = "lumi_library"
    private const val KEY_PREFIX = "library_"

    private val gson = Gson()
    private var loadedKey: String? = null
    private val _state = MutableStateFlow(LibrarySnapshot())
    val state: StateFlow<LibrarySnapshot> = _state

    private fun preferences() =
        LumiApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun currentKey(): String =
        KEY_PREFIX + (TokenManager.getCurrentUser()?.id ?: "guest")

    /**
     * Викликається після відновлення сесії / входу / виходу, щоб не показати
     * дані попереднього акаунта на цьому пристрої.
     */
    @Synchronized
    fun activateForCurrentUser() {
        load(currentKey(), publish = true)
    }

    @Synchronized
    fun snapshot(): LibrarySnapshot {
        ensureLoaded()
        return _state.value
    }

    fun playlists(): List<Playlist> = snapshot().playlists

    fun playlist(playlistId: String): Playlist? = playlists().firstOrNull { it.id == playlistId }

    fun likedTracks(): List<Track> = snapshot().likedTracks

    fun history(): List<HistoryEntry> = snapshot().history

    fun followedArtists(): List<Artist> = snapshot().followedArtists

    fun createPlaylist(title: String, description: String?, now: Long, id: String): Playlist {
        update { LibraryMutations.createPlaylist(it, title, description, now, id) }
        return checkNotNull(playlist(id)) { "Playlist was not created" }
    }

    fun updatePlaylist(playlistId: String, title: String, description: String?) = update {
        LibraryMutations.updatePlaylist(it, playlistId, title, description)
    }

    fun deletePlaylist(playlistId: String) = update {
        LibraryMutations.deletePlaylist(it, playlistId)
    }

    fun addTrack(playlistId: String, track: Track) = update {
        LibraryMutations.addTrack(it, playlistId, track)
    }

    fun removeTrack(playlistId: String, trackId: String) = update {
        LibraryMutations.removeTrack(it, playlistId, trackId)
    }

    fun setLiked(track: Track, liked: Boolean) = update {
        LibraryMutations.setLiked(it, track, liked)
    }

    fun recordHistory(track: Track, playedAt: Long = System.currentTimeMillis()) = update {
        LibraryMutations.recordHistory(it, track, playedAt)
    }

    fun clearHistory() = update { LibraryMutations.clearHistory(it) }

    fun followArtist(artist: Artist) = update { LibraryMutations.followArtist(it, artist) }

    fun unfollowArtist(artistId: String) = update {
        LibraryMutations.unfollowArtist(it, artistId)
    }

    /** Додає виконавців, яких сервер позначив як followed, не стираючи локальні підписки. */
    fun seedFollowed(artists: Collection<Artist>) {
        if (artists.isEmpty()) return
        update { LibraryMutations.seedFollowed(it, artists) }
    }

    @Synchronized
    fun replaceLikedFromServer(tracks: List<Track>) {
        val current = snapshot()
        val merged = (tracks + current.likedTracks)
            .associateBy { it.id }
            .values
            .map { it.copy(isLiked = true) }
        persist(current.copy(likedTracks = merged))
    }

    @Synchronized
    fun replaceFollowedFromServer(artists: List<Artist>) {
        val current = snapshot()
        val next = LibraryMutations.seedFollowed(current, artists)
        persist(next)
    }

    @Synchronized
    private fun update(transform: (LibrarySnapshot) -> LibrarySnapshot) {
        persist(transform(snapshot()))
    }

    private fun persist(next: LibrarySnapshot) {
        _state.value = next
        runCatching {
            preferences().edit().putString(currentKey(), gson.toJson(next)).apply()
        }
    }

    private fun ensureLoaded() {
        val key = currentKey()
        if (loadedKey != key) load(key, publish = false)
    }

    private fun load(key: String, publish: Boolean) {
        if (loadedKey == key && !publish) return
        val json = runCatching { preferences().getString(key, null) }.getOrNull()
        val decoded = json?.let {
            runCatching { gson.fromJson(it, LibrarySnapshot::class.java) }.getOrNull()
        } ?: LibrarySnapshot()
        loadedKey = key
        _state.value = decoded
        if (publish) {
            LibraryStateHolder.replaceAll(
                likedIds = decoded.likedTracks.map { it.id },
                followedIds = decoded.followedArtists.map { it.id }
            )
        }
    }
}
