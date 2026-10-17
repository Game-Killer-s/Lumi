package com.lumi.app.state

import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.TrackRepository
import com.lumi.app.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Optimistically toggles like state via [LibraryStateHolder] and confirms
 * against the real API, reverting on failure. Shared by every screen that
 * renders a [com.lumi.app.ui.component.TrackRow] heart button so behaviour
 * (and the eventual API call) stays in one place.
 */
class TrackLikeController(private val repository: TrackRepository = TrackRepository()) {

    fun toggle(track: Track, scope: CoroutineScope) {
        val wasLiked = LibraryStateHolder.isLiked(track.id)
        val nowLiked = !wasLiked
        LibraryStateHolder.setLiked(track.id, nowLiked)
        scope.launch {
            val result = if (nowLiked) repository.likeTrack(track.id) else repository.unlikeTrack(track.id)
            if (result is ApiResult.Error) LibraryStateHolder.revertLike(track.id, wasLiked)
        }
    }
}
