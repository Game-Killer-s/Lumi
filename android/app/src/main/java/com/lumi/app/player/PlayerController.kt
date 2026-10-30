package com.lumi.app.player

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isBuffering: Boolean = false,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: PlayerRepeatMode = PlayerRepeatMode.OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
) {
    val progressFraction: Float
        get() = PlaybackLogic.progressFraction(positionMs, durationMs)

    val bufferedFraction: Float
        get() = PlaybackLogic.bufferedFraction(positionMs, bufferedPositionMs, durationMs)
}

/**
 * App-wide playback controller. Wraps a [MediaController] bound to
 * [PlaybackService] so playback survives navigation and keeps running in
 * the background. Used by the mini player, the full player screen and by
 * Playlist/Album "Play All" / "Shuffle".
 *
 * Besides transport controls it publishes the playback position, duration
 * and buffered position (polled while media is loaded), supports seeking,
 * shuffle / repeat modes, and persists queue + position so playback can be
 * restored after a restart.
 *
 * Call [init] once (e.g. from LumiApplication) — safe to call multiple times.
 */
object PlayerController {

    private const val TAG = "PlayerController"
    private const val PLAYING_POLL_MS = 250L
    private const val PAUSED_POLL_MS = 1000L
    private const val PERSIST_INTERVAL_MS = 5000L

    /** Одразу після seek плеєр ще може віддавати старий буфер — довіряємо власній оцінці. */
    private const val SEEK_BUFFER_GRACE_MS = 600L

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private var controller: MediaController? = null
    private var connecting = false
    private var store: PlaybackSnapshotStore? = null
    private val pendingActions = ArrayDeque<() -> Unit>()
    private val historyRepository = HistoryRepository()

    /** Останній трек, записаний в історію, щоб повторні події плеєра не дублювали рядок. */
    private var lastRecordedTrackId: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var lastSeekAt = 0L
    private var lastPersistAt = 0L

    fun init(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        store = PlaybackSnapshotStore(context)

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            connecting = false
            val connected = try {
                future.get()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to PlaybackService", e)
                return@addListener
            }
            controller = connected
            attachListener(connected)
            restoreSnapshotIfNeeded(connected)
            while (pendingActions.isNotEmpty()) pendingActions.removeFirst().invoke()
            syncFromController(connected)
            ensureProgressLoop()
        }, MoreExecutors.directExecutor())
    }

    private fun attachListener(mediaController: MediaController) {
        mediaController.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val c = controller ?: return
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    // Новий трек: позиція й буфер починаються з нуля.
                    _state.update { it.copy(positionMs = 0L, bufferedPositionMs = 0L) }
                }
                syncFromController(c)
                if (events.containsAny(
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION
                    )
                ) {
                    recordHistoryIfPlaying(c)
                }
                if (events.containsAny(
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED
                    )
                ) {
                    persist()
                }
                ensureProgressLoop()
            }
        })
    }

    // ------------------------------------------------------------------
    // Синхронізація стану з MediaController
    // ------------------------------------------------------------------

    private fun syncFromController(c: MediaController) {
        val hasMedia = c.mediaItemCount > 0
        val status = PlaybackLogic.resolveStatus(c.playbackState, c.isPlaying, c.playWhenReady, hasMedia)

        if (!hasMedia) {
            // Контролер ще порожній (наприклад playQueue чекає на підключення) — не затираємо чергу.
            _state.update {
                it.copy(isPlaying = false, isBuffering = false, status = status)
            }
            return
        }

        val queue = _state.value.queue
        val index = c.currentMediaItemIndex
        val track = queue.getOrNull(index)
        val duration = PlaybackLogic.normalizeDuration(c.duration, track?.duration ?: 0)
        val position = PlaybackLogic.clampPosition(c.currentPosition, duration)
        val inSeekGrace = SystemClock.elapsedRealtime() - lastSeekAt < SEEK_BUFFER_GRACE_MS

        _state.update {
            val buffered = if (inSeekGrace) {
                PlaybackLogic.reconcileBuffered(position, it.bufferedPositionMs, duration)
            } else {
                PlaybackLogic.reconcileBuffered(position, c.bufferedPosition, duration)
            }
            it.copy(
                currentIndex = index,
                currentTrack = track ?: it.currentTrack,
                isPlaying = c.isPlaying,
                isBuffering = status == PlaybackStatus.BUFFERING,
                status = status,
                positionMs = position,
                durationMs = duration,
                bufferedPositionMs = buffered,
                shuffleEnabled = c.shuffleModeEnabled,
                repeatMode = c.repeatMode.toRepeatMode(),
                hasNext = c.hasNextMediaItem(),
                hasPrevious = c.hasPreviousMediaItem()
            )
        }
    }

    /** Пишемо трек в історію лише коли він реально почав відтворюватися. */
    private fun recordHistoryIfPlaying(c: MediaController) {
        if (!c.isPlaying && !c.playWhenReady) return
        val track = _state.value.currentTrack ?: return
        if (track.id == lastRecordedTrackId) return
        lastRecordedTrackId = track.id
        historyRepository.record(track)
    }

    /** Поки є завантажений медіаконтент, опитуємо позицію/буфер (частіше під час відтворення). */
    private fun ensureProgressLoop() {
        val c = controller ?: return
        if (!shouldPoll(c)) {
            progressJob?.cancel()
            progressJob = null
            return
        }
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                val ctl = controller ?: break
                if (!shouldPoll(ctl)) break
                syncFromController(ctl)
                if (ctl.isPlaying) maybePersist()
                val fast = ctl.isPlaying || ctl.playbackState == Player.STATE_BUFFERING
                delay(if (fast) PLAYING_POLL_MS else PAUSED_POLL_MS)
            }
        }
    }

    private fun shouldPoll(c: MediaController): Boolean =
        c.mediaItemCount > 0 &&
            c.playbackState != Player.STATE_IDLE &&
            c.playbackState != Player.STATE_ENDED

    // ------------------------------------------------------------------
    // Збереження / відновлення
    // ------------------------------------------------------------------

    private fun maybePersist() {
        if (SystemClock.elapsedRealtime() - lastPersistAt >= PERSIST_INTERVAL_MS) persist()
    }

    private fun persist() {
        val s = _state.value
        if (s.queue.isEmpty() || s.currentIndex !in s.queue.indices) return
        lastPersistAt = SystemClock.elapsedRealtime()
        store?.save(
            PlaybackSnapshot(
                queue = s.queue,
                index = s.currentIndex,
                positionMs = s.positionMs,
                shuffleEnabled = s.shuffleEnabled,
                repeatMode = s.repeatMode
            )
        )
    }

    /**
     * Після холодного старту піднімаємо чергу з останньою позицією в паузі.
     * prepare()/play() навмисно не викликаємо: мережа не чіпається, доки
     * користувач не натисне Play ([togglePlayPause] підготує плеєр).
     */
    private fun restoreSnapshotIfNeeded(c: MediaController) {
        if (c.mediaItemCount > 0) return
        val snapshot = store?.load() ?: return
        val track = snapshot.queue[snapshot.index]
        _state.update {
            it.copy(
                queue = snapshot.queue,
                currentIndex = snapshot.index,
                currentTrack = track,
                positionMs = snapshot.positionMs,
                durationMs = PlaybackLogic.normalizeDuration(PlaybackLogic.TIME_UNSET, track.duration),
                bufferedPositionMs = 0L,
                shuffleEnabled = snapshot.shuffleEnabled,
                repeatMode = snapshot.repeatMode
            )
        }
        c.setMediaItems(
            snapshot.queue.map { DownloadManagerHolder.mediaItemFor(it) },
            snapshot.index,
            snapshot.positionMs
        )
        c.shuffleModeEnabled = snapshot.shuffleEnabled
        c.repeatMode = snapshot.repeatMode.toExoRepeatMode()
    }

    // ------------------------------------------------------------------
    // Черга
    // ------------------------------------------------------------------

    /**
     * Запускає трек зі списку. Якщо це вже поточна черга й пауза — просто
     * продовжує збережену позицію (пункт ТЗ «продовжити з місця зупинки»).
     */
    fun playQueueOrResume(tracks: List<Track>, startIndex: Int = 0) {
        val deduped = QueueBuilder.dedupeById(tracks)
        if (deduped.isEmpty()) return
        val clampedStart = startIndex.coerceIn(0, deduped.lastIndex)
        val s = _state.value
        if (PlaybackLogic.isSameQueueAt(s.queue, s.currentIndex, deduped, clampedStart)) {
            if (!s.isPlaying) togglePlayPause()
            return
        }
        playQueue(deduped, clampedStart)
    }

    /**
     * Plays [tracks] starting at [startIndex]. Tracks are de-duplicated
     * by id first so a queue built from e.g. a playlist that references
     * the same track twice never plays/repeats it back to back.
     *
     * [shuffle]: null leaves the current shuffle mode unchanged.
     */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean? = null) {
        val deduped = QueueBuilder.dedupeById(tracks)
        if (deduped.isEmpty()) return
        val clampedStart = startIndex.coerceIn(0, deduped.lastIndex)
        val startTrack = deduped[clampedStart]
        lastRecordedTrackId = null

        _state.update {
            it.copy(
                queue = deduped,
                currentIndex = clampedStart,
                currentTrack = startTrack,
                positionMs = 0L,
                bufferedPositionMs = 0L,
                durationMs = PlaybackLogic.normalizeDuration(PlaybackLogic.TIME_UNSET, startTrack.duration),
                shuffleEnabled = shuffle ?: it.shuffleEnabled
            )
        }

        val mediaItems = deduped.map { DownloadManagerHolder.mediaItemFor(it) }
        runWhenConnected { c ->
            if (shuffle != null) c.shuffleModeEnabled = shuffle
            c.setMediaItems(mediaItems, clampedStart, 0L)
            c.prepare()
            c.play()
        }
        persist()
    }

    /**
     * Shuffle-plays [tracks]: de-duplicates by id, then shuffles so the
     * resulting queue has no repeated track and (for playlists with 3+
     * tracks) never lands back on the original order. Also switches the
     * player's shuffle mode on so the UI reflects it.
     */
    fun shuffleQueue(tracks: List<Track>) {
        val shuffled = QueueBuilder.shuffledQueue(tracks)
        if (shuffled.isEmpty()) return
        playQueue(shuffled, startIndex = 0, shuffle = true)
    }

    /** Додає трек у кінець поточної черги (пункт контекстного меню). */
    fun addToQueue(track: Track) {
        if (_state.value.queue.any { it.id == track.id }) return
        _state.update { it.copy(queue = it.queue + track) }
        runWhenConnected { c -> c.addMediaItem(DownloadManagerHolder.mediaItemFor(track)) }
        persist()
    }

    private fun runWhenConnected(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pendingActions.addLast { controller?.let(block) }
        }
    }

    // ------------------------------------------------------------------
    // Керування відтворенням
    // ------------------------------------------------------------------

    fun togglePlayPause() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_ENDED -> {
                c.seekToDefaultPosition(0)
                c.play()
            }
            c.playbackState == Player.STATE_IDLE -> {
                c.prepare()
                c.play()
            }
            else -> c.play()
        }
        ensureProgressLoop()
    }

    fun skipToNext() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    /** Стандартна поведінка: після 3 с відтворення "Попередній" перезапускає трек. */
    fun skipToPrevious() {
        val c = controller ?: return
        if (PlaybackLogic.shouldRestartTrackOnPrevious(c.currentPosition) || !c.hasPreviousMediaItem()) {
            seekTo(0L)
        } else {
            c.seekToPreviousMediaItem()
        }
    }

    /** Перемотує на [positionMs], одразу оновлює стан і зберігає нову позицію. */
    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        val previous = _state.value
        val target = PlaybackLogic.clampPosition(positionMs, previous.durationMs)

        c.seekTo(target)
        lastSeekAt = SystemClock.elapsedRealtime()
        _state.update {
            it.copy(
                positionMs = target,
                bufferedPositionMs = PlaybackLogic.bufferAfterSeek(
                    previousPositionMs = previous.positionMs,
                    previousBufferedMs = previous.bufferedPositionMs,
                    targetMs = target,
                    durationMs = previous.durationMs
                )
            )
        }
        persist()
        ensureProgressLoop()
    }

    fun seekToFraction(fraction: Float) {
        seekTo(PlaybackLogic.fractionToPosition(fraction, _state.value.durationMs))
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = c.repeatMode.toRepeatMode().next().toExoRepeatMode()
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        controller?.release()
        controller = null
        connecting = false
    }

    private fun Int.toRepeatMode(): PlayerRepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
        Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
        else -> PlayerRepeatMode.OFF
    }

    private fun PlayerRepeatMode.toExoRepeatMode(): Int = when (this) {
        PlayerRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        PlayerRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        PlayerRepeatMode.OFF -> Player.REPEAT_MODE_OFF
    }
}
