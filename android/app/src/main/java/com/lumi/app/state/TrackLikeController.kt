package com.lumi.app.state

import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.TrackRepository
import com.lumi.app.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Optimistically toggles like state via [LibraryStateHolder] and confirms
 * against the real API, reverting on failure. Shared by every screen that
 * renders a [com.lumi.app.ui.component.TrackRow] heart button so behaviour
 * (and the eventual API call) stays in one place.
 */
class TrackLikeController(private val repository: TrackRepository = TrackRepository()) {

    fun toggle(track: Track) {
        val wasLiked = LibraryStateHolder.isLiked(track.id)
        val nowLiked = !wasLiked
        LibraryStateHolder.setLiked(track.id, nowLiked)
        LibraryStateHolder.markLikePending(track.id, pending = true)
        appScope.launch {
            val result = if (nowLiked) repository.likeTrack(track.id) else repository.unlikeTrack(track.id)
            if (result is ApiResult.Error) LibraryStateHolder.revertLike(track.id, wasLiked)
            LibraryStateHolder.markLikePending(track.id, pending = false)
        }
    }

    private companion object {
        /**
         * Запит лайка не має скасовуватися, коли користувач одразу йде з екрана:
         * інакше оптимістичне серце залишиться, а на сервері лайка не буде
         * (саме так стан «серце біле, та в улюблених треку немає» і виникав).
         */
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
