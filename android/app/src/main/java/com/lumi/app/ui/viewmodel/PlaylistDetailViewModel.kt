package com.lumi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.R
import com.lumi.app.data.local.LibraryLocalStore
import com.lumi.app.data.model.Playlist
import com.lumi.app.data.repository.PlaylistRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.player.PlayerController
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.uiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    data class Loaded(val playlist: Playlist) : PlaylistDetailUiState
    data class Empty(val playlist: Playlist) : PlaylistDetailUiState
    data class Error(val message: UiText) : PlaylistDetailUiState
}

class PlaylistDetailViewModel(
    private val playlistId: String,
    private val repository: PlaylistRepository = PlaylistRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState

    init {
        // Без цього перший кадр після deep link показував би «плейлист не знайдено».
        LibraryLocalStore.snapshot()
        viewModelScope.launch {
            LibraryLocalStore.state.collect { snapshot ->
                val playlist = snapshot.playlists.firstOrNull { it.id == playlistId }
                _uiState.value = when {
                    playlist == null -> PlaylistDetailUiState.Error(
                        uiText(R.string.playlist_not_found)
                    )
                    playlist.tracks.isNullOrEmpty() -> PlaylistDetailUiState.Empty(playlist)
                    else -> PlaylistDetailUiState.Loaded(playlist)
                }
            }
        }
    }

    private fun currentPlaylist(): Playlist? = when (val state = _uiState.value) {
        is PlaylistDetailUiState.Loaded -> state.playlist
        is PlaylistDetailUiState.Empty -> state.playlist
        else -> null
    }

    fun playAll() {
        val tracks = currentPlaylist()?.tracks.orEmpty()
        if (tracks.isNotEmpty()) PlayerController.playQueue(tracks, startIndex = 0)
    }

    fun playFrom(index: Int) {
        val tracks = currentPlaylist()?.tracks.orEmpty()
        if (tracks.isNotEmpty()) PlayerController.playQueue(tracks, startIndex = index)
    }

    fun shuffle() {
        val tracks = currentPlaylist()?.tracks.orEmpty()
        if (tracks.isNotEmpty()) PlayerController.shuffleQueue(tracks)
    }

    fun removeTrack(trackId: String) {
        repository.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun rename(title: String, description: String?): Boolean =
        repository.updatePlaylist(playlistId, title, description) is ApiResult.Success

    fun delete(): Boolean = repository.deletePlaylist(playlistId) is ApiResult.Success
}

