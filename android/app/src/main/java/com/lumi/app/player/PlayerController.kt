package com.lumi.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.lumi.app.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isBuffering: Boolean = false
)

/**
 * App-wide playback controller. Wraps a [MediaController] bound to
 * [PlaybackService] so playback survives navigation and keeps running in
 * the background. Used by the mini player overlay and by Playlist/Album
 * "Play All" / "Shuffle".
 *
 * Call [init] once (e.g. from LumiApplication or the first Activity) —
 * safe to call multiple times.
 */
object PlayerController {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private var controller: MediaController? = null
    private var currentQueueTrackIds: List<String> = emptyList()

    fun init(context: Context) {
        if (controller != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get().also { attachListener(it) }
        }, MoreExecutors.directExecutor())
    }

    private fun attachListener(mediaController: MediaController) {
        mediaController.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = mediaController.currentMediaItemIndex
                val track = _state.value.queue.getOrNull(index)
                _state.update { it.copy(currentIndex = index, currentTrack = track) }
            }
        })
    }

    /**
     * Plays [tracks] starting at [startIndex]. Tracks are de-duplicated
     * by id first so a queue built from e.g. a playlist that references
     * the same track twice never plays/repeats it back to back.
     */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        val deduped = QueueBuilder.dedupeById(tracks)
        if (deduped.isEmpty()) return
        val clampedStart = startIndex.coerceIn(0, deduped.lastIndex)

        currentQueueTrackIds = deduped.map { it.id }
        _state.update {
            it.copy(
                queue = deduped,
                currentIndex = clampedStart,
                currentTrack = deduped[clampedStart]
            )
        }

        val mediaItems = deduped.map { DownloadManagerHolder.mediaItemFor(it) }
        controller?.apply {
            setMediaItems(mediaItems, clampedStart, 0L)
            prepare()
            play()
        }
    }

    /**
     * Shuffle-plays [tracks]: de-duplicates by id, then shuffles so the
     * resulting queue has no repeated track and (for playlists with 3+
     * tracks) never lands back on the original order.
     */
    fun shuffleQueue(tracks: List<Track>) {
        val shuffled = QueueBuilder.shuffledQueue(tracks)
        if (shuffled.isEmpty()) return
        playQueue(shuffled, startIndex = 0)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipToNext() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun skipToPrevious() {
        controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
