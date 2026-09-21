package com.lumi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.model.Album
import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.AlbumRepository
import com.lumi.app.data.repository.TrackRepository
import com.lumi.app.data.repository.UserRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.state.LibraryStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ArtistUiState {
    data object Loading : ArtistUiState
    data class Loaded(
        val artist: Artist,
        val topTracks: List<Track>,
        val releases: List<Album>,
        val isFollowed: Boolean
    ) : ArtistUiState
    data class Error(val message: String) : ArtistUiState
}

class ArtistViewModel(
    private val artistId: String,
    private val userRepository: UserRepository = UserRepository(),
    private val trackRepository: TrackRepository = TrackRepository(),
    private val albumRepository: AlbumRepository = AlbumRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val uiState: StateFlow<ArtistUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ArtistUiState.Loading
            when (val artistResult = userRepository.getArtist(artistId)) {
                is ApiResult.Success -> {
                    val artist = artistResult.data
                    LibraryStateHolder.seedFollowed(listOf(artist.id), artist.isFollowed)

                    // TODO(backend): no /artists/{id}/tracks or artist-filtered
                    // /albums endpoint exists yet. Using search-by-name and an
                    // unfiltered album page as a stand-in — swap for real
                    // artist-scoped endpoints once available.
                    val topTracksResult = trackRepository.searchTracks(artist.name, limit = 10)
                    val albumsResult = albumRepository.getAlbums(limit = 20)

                    val topTracks = (topTracksResult as? ApiResult.Success)?.data?.tracks ?: emptyList()
                    val allAlbums = (albumsResult as? ApiResult.Success)?.data?.albums ?: emptyList()
                    val releases = allAlbums.filter { it.artist?.id == artist.id || it.artistName == artist.name }

                    _uiState.value = ArtistUiState.Loaded(
                        artist = artist,
                        topTracks = topTracks,
                        releases = releases,
                        isFollowed = LibraryStateHolder.isFollowed(artist.id)
                    )
                }
                is ApiResult.Error -> _uiState.value = ArtistUiState.Error(artistResult.error.message)
            }
        }
    }

    fun toggleFollow() {
        val current = _uiState.value as? ArtistUiState.Loaded ?: return
        val wasFollowed = current.isFollowed
        val nowFollowed = !wasFollowed

        LibraryStateHolder.setFollowed(current.artist.id, nowFollowed)
        _uiState.value = current.copy(isFollowed = nowFollowed)

        viewModelScope.launch {
            val result = if (nowFollowed) {
                userRepository.followArtist(current.artist.id)
            } else {
                userRepository.unfollowArtist(current.artist.id)
            }
            if (result is ApiResult.Error) {
                // revert optimistic update on failure
                LibraryStateHolder.revertFollow(current.artist.id, wasFollowed)
                _uiState.value = current.copy(isFollowed = wasFollowed)
            }
        }
    }
}
