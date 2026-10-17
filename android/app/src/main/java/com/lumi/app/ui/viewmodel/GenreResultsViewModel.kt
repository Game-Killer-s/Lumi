package com.lumi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.TrackRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.state.LibraryStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface GenreResultsUiState {
    data object Loading : GenreResultsUiState
    data class Loaded(val tracks: List<Track>) : GenreResultsUiState
    data object Empty : GenreResultsUiState
    data class Error(val message: String) : GenreResultsUiState
}

/** Результати жанру — точка призначення для переходу "жанр → пошук" зі статистики. */
class GenreResultsViewModel(
    private val genre: String,
    private val repository: TrackRepository = TrackRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<GenreResultsUiState>(GenreResultsUiState.Loading)
    val uiState: StateFlow<GenreResultsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = GenreResultsUiState.Loading
            when (val result = repository.getTracksByGenre(genre)) {
                is ApiResult.Success -> {
                    val tracks = result.data.tracks
                    LibraryStateHolder.seedLiked(tracks.filter { it.isLiked }.map { it.id })
                    _uiState.value = if (tracks.isEmpty()) GenreResultsUiState.Empty
                    else GenreResultsUiState.Loaded(tracks)
                }
                is ApiResult.Error -> _uiState.value = GenreResultsUiState.Error(result.error.message)
            }
        }
    }
}
