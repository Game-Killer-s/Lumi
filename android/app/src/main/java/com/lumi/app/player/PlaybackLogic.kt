package com.lumi.app.player

import com.google.gson.Gson
import com.lumi.app.data.model.Track
import kotlin.math.max
import kotlin.math.roundToLong

/** Режим повтору. Порядок перемикання: вимкнено → усі → один трек → вимкнено. */
enum class PlayerRepeatMode {
    OFF, ALL, ONE;

    fun next(): PlayerRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

/** Узагальнений стан відтворення для UI. */
enum class PlaybackStatus { IDLE, BUFFERING, PLAYING, PAUSED, ENDED }

/**
 * Чиста логіка плеєра без залежностей від Android/Media3, щоб її можна було
 * тестувати звичайним JUnit. [PlayerController] лише викликає ці функції.
 */
object PlaybackLogic {

    /** Дзеркалить androidx.media3.common.C.TIME_UNSET. */
    const val TIME_UNSET: Long = Long.MIN_VALUE + 1

    /** Якщо трек грає довше за цей поріг, "Попередній" перезапускає поточний трек. */
    const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

    // Дзеркалять Player.STATE_* (частина стабільного публічного API Media3).
    private const val STATE_BUFFERING = 2
    private const val STATE_READY = 3
    private const val STATE_ENDED = 4

    fun resolveStatus(
        playerState: Int,
        isPlaying: Boolean,
        playWhenReady: Boolean,
        hasMedia: Boolean
    ): PlaybackStatus {
        if (!hasMedia) return PlaybackStatus.IDLE
        return when (playerState) {
            STATE_BUFFERING -> if (playWhenReady) PlaybackStatus.BUFFERING else PlaybackStatus.PAUSED
            STATE_READY -> if (isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
            STATE_ENDED -> PlaybackStatus.ENDED
            else -> PlaybackStatus.IDLE
        }
    }

    /** Тривалість у мс: значення плеєра, а якщо воно ще невідоме — метадані треку (секунди). */
    fun normalizeDuration(rawMs: Long, fallbackSeconds: Int): Long =
        if (rawMs == TIME_UNSET || rawMs <= 0L) fallbackSeconds.coerceAtLeast(0) * 1000L else rawMs

    fun clampPosition(positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)

    fun progressFraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0) return 0f
        return (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
    }

    /** Завантажена частина шкали (0..1). Ніколи не менша за позицію відтворення. */
    fun bufferedFraction(positionMs: Long, bufferedMs: Long, durationMs: Long): Float {
        if (durationMs <= 0) return 0f
        val effective = max(bufferedMs, positionMs).coerceIn(0L, durationMs)
        return (effective.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
    }

    fun fractionToPosition(fraction: Float, durationMs: Long): Long {
        if (durationMs <= 0) return 0L
        return (fraction.coerceIn(0f, 1f).toDouble() * durationMs).roundToLong()
    }

    /** Буфер, який повідомив плеєр, приводимо до [позиція..тривалість]. */
    fun reconcileBuffered(positionMs: Long, reportedBufferedMs: Long, durationMs: Long): Long =
        clampPosition(max(reportedBufferedMs.coerceAtLeast(0L), positionMs), durationMs)

    /**
     * Оптимістична оцінка буфера одразу після перемотування.
     * Якщо ціль лежить у вже завантаженому діапазоні попереду поточної позиції,
     * буфер зберігається. Інакше (перемотка назад або за межі буфера)
     * завантаження починається з цілі, тож завантаженою є лише точка перемотки.
     */
    fun bufferAfterSeek(
        previousPositionMs: Long,
        previousBufferedMs: Long,
        targetMs: Long,
        durationMs: Long
    ): Long {
        val insideBuffered = targetMs >= previousPositionMs && targetMs <= previousBufferedMs
        val buffered = if (insideBuffered) previousBufferedMs else targetMs
        return clampPosition(buffered, durationMs)
    }

    fun shouldRestartTrackOnPrevious(positionMs: Long): Boolean =
        positionMs > PREVIOUS_RESTART_THRESHOLD_MS

    fun statusLabel(status: PlaybackStatus): String = when (status) {
        PlaybackStatus.IDLE -> "Зупинено"
        PlaybackStatus.BUFFERING -> "Буферизація…"
        PlaybackStatus.PLAYING -> "Відтворюється"
        PlaybackStatus.PAUSED -> "Пауза"
        PlaybackStatus.ENDED -> "Завершено"
    }

    fun repeatLabel(mode: PlayerRepeatMode): String = when (mode) {
        PlayerRepeatMode.OFF -> "Повтор: вимкнено"
        PlayerRepeatMode.ALL -> "Повтор: усі треки"
        PlayerRepeatMode.ONE -> "Повтор: один трек"
    }

    fun shuffleLabel(enabled: Boolean): String =
        if (enabled) "Перемішування: увімкнено" else "Перемішування: вимкнено"
}

/** Знімок черги й позиції, що зберігається між запусками застосунку. */
data class PlaybackSnapshot(
    val queue: List<Track>,
    val index: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: PlayerRepeatMode
)

object PlaybackSnapshotCodec {
    private val gson = Gson()

    fun encode(snapshot: PlaybackSnapshot): String = gson.toJson(snapshot)

    /** Повертає null для порожнього/пошкодженого JSON і виправляє неприпустимі значення. */
    fun decode(json: String?): PlaybackSnapshot? {
        if (json.isNullOrBlank()) return null
        return try {
            val raw = gson.fromJson(json, PlaybackSnapshot::class.java) ?: return null
            // Gson обходить null-безпеку Kotlin, тому відкидаємо пошкоджені треки.
            val queue = (raw.queue as List<Track?>?).orEmpty().filterNotNull().filter {
                runCatching { it.id.length + it.title.length }.isSuccess
            }
            if (queue.isEmpty()) return null
            PlaybackSnapshot(
                queue = queue,
                index = raw.index.coerceIn(0, queue.lastIndex),
                positionMs = raw.positionMs.coerceAtLeast(0L),
                shuffleEnabled = raw.shuffleEnabled,
                repeatMode = runCatching { raw.repeatMode ?: PlayerRepeatMode.OFF }
                    .getOrDefault(PlayerRepeatMode.OFF)
            )
        } catch (e: Exception) {
            null
        }
    }
}
