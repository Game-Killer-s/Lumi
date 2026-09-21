package com.lumi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.model.Album
import com.lumi.app.data.repository.AlbumRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.player.PlayerController
import com.lumi.app.state.LibraryStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AlbumDetailUiState {
    data object Loading : AlbumDetailUiState
    data class Loaded(val album: Album) : AlbumDetailUiState
    data class Empty(val album: Album) : AlbumDetailUiState
    data class Error(val message: String) : AlbumDetailUiState
}

class AlbumDetailViewModel(
    private val albumId: String,
    private val repository: AlbumRepository = AlbumRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlbumDetailUiState>(AlbumDetailUiState.Loading)
    val uiState: StateFlow<AlbumDetailUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = AlbumDetailUiState.Loading
            when (val result = repository.getAlbum(albumId)) {
                is ApiResult.Success -> {
                    val album = result.data
                    LibraryStateHolder.seedLiked(album.tracks.orEmpty().filter { it.isLiked }.map { it.id })
                    _uiState.value = if (album.tracks.isNullOrEmpty()) {
                        AlbumDetailUiState.Empty(album)
                    } else {
                        AlbumDetailUiState.Loaded(album)
                    }
                }
                is ApiResult.Error -> _uiState.value = AlbumDetailUiState.Error(result.error.message)
            }
        }
    }

    fun playAll() {
        val tracks = (_uiState.value as? AlbumDetailUiState.Loaded)?.album?.tracks.orEmpty()
        PlayerController.playQueue(tracks, startIndex = 0)
    }

    fun playFrom(index: Int) {
        val tracks = (_uiState.value as? AlbumDetailUiState.Loaded)?.album?.tracks.orEmpty()
        PlayerController.playQueue(tracks, startIndex = index)
    }

    fun shuffle() {
        val tracks = (_uiState.value as? AlbumDetailUiState.Loaded)?.album?.tracks.orEmpty()
        PlayerController.shuffleQueue(tracks)
    }
}
