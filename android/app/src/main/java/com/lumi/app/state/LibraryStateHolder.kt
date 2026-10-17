package com.lumi.app.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide source of truth for "is this track liked" / "is this
 * artist followed", so the track context menu, Home carousels, Playlist
 * /Album/Artist screens and the mini player all agree on the same state
 * without each screen re-fetching it.
 *
 * Screens seed this from list responses (Track.isLiked / Artist.isFollowed)
 * and then mutate it optimistically on user actions; repositories are the
 * source of truth for the actual network call/result.
 */
object LibraryStateHolder {

    private val _likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds: StateFlow<Set<String>> = _likedTrackIds

    private val _followedArtistIds = MutableStateFlow<Set<String>>(emptySet())
    val followedArtistIds: StateFlow<Set<String>> = _followedArtistIds

    /** Seed initial liked/followed ids from a freshly-loaded list, without clobbering other entries. */
    fun seedLiked(ids: Collection<String>, liked: Boolean = true) {
        if (!liked) return
        _likedTrackIds.update { it + ids }
    }

    fun seedFollowed(ids: Collection<String>, followed: Boolean = true) {
        if (!followed) return
        _followedArtistIds.update { it + ids }
    }

    fun isLiked(trackId: String): Boolean = trackId in _likedTrackIds.value

    fun isFollowed(artistId: String): Boolean = artistId in _followedArtistIds.value

    /** Optimistic toggle — call before the API result comes back, then [revertLike] if it fails. */
    fun setLiked(trackId: String, liked: Boolean) {
        _likedTrackIds.update { if (liked) it + trackId else it - trackId }
    }

    fun revertLike(trackId: String, wasLiked: Boolean) = setLiked(trackId, wasLiked)

    fun setFollowed(artistId: String, followed: Boolean) {
        _followedArtistIds.update { if (followed) it + artistId else it - artistId }
    }

    fun revertFollow(artistId: String, wasFollowed: Boolean) = setFollowed(artistId, wasFollowed)
}
