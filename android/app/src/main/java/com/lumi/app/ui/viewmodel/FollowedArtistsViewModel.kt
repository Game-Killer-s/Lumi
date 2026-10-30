package com.lumi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.local.LibraryLocalStore
import com.lumi.app.data.model.Artist
import com.lumi.app.data.repository.FollowRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.rawText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowedArtistsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val artists: List<Artist> = emptyList(),
    val errorMessage: UiText? = null
)

class FollowedArtistsViewModel(
    private val repository: FollowRepository = FollowRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowedArtistsUiState())
    val uiState: StateFlow<FollowedArtistsUiState> = _uiState

    init {
        viewModelScope.launch {
            LibraryLocalStore.state.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        artists = snapshot.followedArtists
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
            when (val result = repository.refresh()) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> {
                    if (LibraryLocalStore.followedArtists().isEmpty()) {
                        _uiState.update { it.copy(errorMessage = rawText(result.error.message)) }
                    }
                }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun unfollow(artist: Artist) {
        viewModelScope.launch { repository.setFollowed(artist, followed = false) }
    }
}
