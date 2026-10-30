package com.lumi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.local.LibraryLocalStore
import com.lumi.app.data.model.Album
import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.Playlist
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.AlbumRepository
import com.lumi.app.data.repository.FollowRepository
import com.lumi.app.data.repository.PlaylistRepository
import com.lumi.app.data.repository.TrackRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.state.LibraryStateHolder
import com.lumi.app.state.TrackLikeController
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.rawText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val followedArtists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val errorMessage: UiText? = null
)

/**
 * Стан вкладок «Бібліотеки». Джерело даних — [LibraryLocalStore], тому
 * створення плейлиста, лайк чи підписка одразу видно на всіх екранах.
 */
class LibraryViewModel(
    private val playlistRepository: PlaylistRepository = PlaylistRepository(),
    private val trackRepository: TrackRepository = TrackRepository(),
    private val followRepository: FollowRepository = FollowRepository(),
    private val likeController: TrackLikeController = TrackLikeController(),
    private val albumRepository: AlbumRepository = AlbumRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            LibraryLocalStore.state.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlists = snapshot.playlists,
                        likedTracks = snapshot.likedTracks,
                        followedArtists = snapshot.followedArtists
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val likedRevision = LibraryStateHolder.likedRevision()
            var refreshError: UiText? = null
            when (val liked = trackRepository.getLikedTracks(limit = 100)) {
                is ApiResult.Success -> {
                    val ids = liked.data.tracks.map { it.id }
                    if (liked.data.total <= ids.size) {
                        LibraryStateHolder.applyLikedSnapshot(ids, likedRevision)
                    } else {
                        LibraryStateHolder.seedLiked(ids)
                    }
                }
                is ApiResult.Error -> refreshError = rawText(liked.error.message)
            }

            when (val followed = followRepository.refresh()) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> if (refreshError == null) {
                    refreshError = rawText(followed.error.message)
                }
            }

            val albums = when (val result = albumRepository.getAlbums(limit = 50)) {
                is ApiResult.Success -> result.data.albums
                is ApiResult.Error -> _uiState.value.albums
            }
            _uiState.update { state ->
                /*
                 * Локальне сховище — джерело правди для вкладок. Якщо в ньому
                 * вже є плейлисти/лайки/підписки, збій фонового оновлення не
                 * має перетворювати робочу бібліотеку на екран помилки.
                 */
                val hasContent = state.playlists.isNotEmpty() ||
                    state.likedTracks.isNotEmpty() ||
                    state.followedArtists.isNotEmpty()
                state.copy(
                    isRefreshing = false,
                    albums = albums,
                    errorMessage = refreshError.takeUnless { hasContent }
                )
            }
        }
    }

    fun createPlaylist(title: String, description: String?): Playlist? {
        val created = (playlistRepository.createPlaylist(title, description) as? ApiResult.Success)?.data
        if (created != null) clearError()
        return created
    }

    fun renamePlaylist(playlistId: String, title: String, description: String?): Boolean {
        val updated = playlistRepository.updatePlaylist(playlistId, title, description)
        if (updated is ApiResult.Success) clearError()
        return updated is ApiResult.Success
    }

    fun deletePlaylist(playlistId: String): Boolean {
        val deleted = playlistRepository.deletePlaylist(playlistId)
        if (deleted is ApiResult.Success) clearError()
        return deleted is ApiResult.Success
    }

    fun addTrackToPlaylist(playlistId: String, track: Track): Boolean {
        val added = playlistRepository.addTrackToPlaylist(playlistId, track)
        if (added is ApiResult.Success) clearError()
        return added is ApiResult.Success
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val removed = playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
        if (removed is ApiResult.Success) clearError()
    }

    private fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleLike(track: Track) = likeController.toggle(track)

    fun unfollowArtist(artist: Artist) {
        viewModelScope.launch { followRepository.setFollowed(artist, followed = false) }
    }
}
