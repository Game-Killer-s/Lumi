package com.lumi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.model.Genre
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.TrackRepository
import com.lumi.app.data.repository.UserRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.state.LibraryStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val recommended: List<Track> = emptyList(),
    val newReleases: List<Track> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val errorMessage: String? = null
)

class HomeViewModel(
    private val trackRepository: TrackRepository = TrackRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            fetch(isRefresh = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            fetch(isRefresh = true)
        }
    }

    private suspend fun fetch(isRefresh: Boolean) {
        // TODO(backend): recommended currently falls back to popular/new tracks,
        // see TrackRepository.getRecommendedTracks().
        val recommendedResult = trackRepository.getRecommendedTracks(limit = 20)
        val newReleasesResult = trackRepository.getNewTracks(limit = 10)
        val genresResult = userRepository.getGenres()

        val error = listOf(recommendedResult, newReleasesResult)
            .filterIsInstance<ApiResult.Error>()
            .firstOrNull()?.error?.message

        val recommended = (recommendedResult as? ApiResult.Success)?.data?.tracks ?: emptyList()
        val newReleases = (newReleasesResult as? ApiResult.Success)?.data?.tracks ?: emptyList()
        val genres = (genresResult as? ApiResult.Success)?.data?.genres ?: emptyList()

        LibraryStateHolder.seedLiked((recommended + newReleases).filter { it.isLiked }.map { it.id })

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isRefreshing = false,
            recommended = recommended,
            newReleases = newReleases,
            genres = genres,
            errorMessage = if (recommended.isEmpty() && newReleases.isEmpty()) error else null
        )
    }
}
