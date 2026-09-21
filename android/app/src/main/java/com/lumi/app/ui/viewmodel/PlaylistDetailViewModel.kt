package com.lumi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.model.Playlist
import com.lumi.app.data.repository.PlaylistRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.player.PlayerController
import com.lumi.app.state.LibraryStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    data class Loaded(val playlist: Playlist) : PlaylistDetailUiState
    data class Empty(val playlist: Playlist) : PlaylistDetailUiState
    data class Error(val message: String) : PlaylistDetailUiState
}

class PlaylistDetailViewModel(
    private val playlistId: String,
    private val repository: PlaylistRepository = PlaylistRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PlaylistDetailUiState.Loading
            when (val result = repository.getPlaylist(playlistId)) {
                is ApiResult.Success -> {
                    val playlist = result.data
                    LibraryStateHolder.seedLiked(playlist.tracks.orEmpty().filter { it.isLiked }.map { it.id })
                    _uiState.value = if (playlist.tracks.isNullOrEmpty()) {
                        PlaylistDetailUiState.Empty(playlist)
                    } else {
                        PlaylistDetailUiState.Loaded(playlist)
                    }
                }
                is ApiResult.Error -> _uiState.value = PlaylistDetailUiState.Error(result.error.message)
            }
        }
    }

    fun playAll() {
        val tracks = (_uiState.value as? PlaylistDetailUiState.Loaded)?.playlist?.tracks.orEmpty()
        PlayerController.playQueue(tracks, startIndex = 0)
    }

    fun playFrom(index: Int) {
        val tracks = (_uiState.value as? PlaylistDetailUiState.Loaded)?.playlist?.tracks.orEmpty()
        PlayerController.playQueue(tracks, startIndex = index)
    }

    fun shuffle() {
        val tracks = (_uiState.value as? PlaylistDetailUiState.Loaded)?.playlist?.tracks.orEmpty()
        PlayerController.shuffleQueue(tracks)
    }
}
