package com.lumi.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLogicTest {

    // ---------- режими повтору / shuffle ----------

    @Test
    fun `repeat mode cycles off - all - one - off`() {
        assertEquals(PlayerRepeatMode.ALL, PlayerRepeatMode.OFF.next())
        assertEquals(PlayerRepeatMode.ONE, PlayerRepeatMode.ALL.next())
        assertEquals(PlayerRepeatMode.OFF, PlayerRepeatMode.ONE.next())
    }

    // ---------- стан відтворення ----------

    @Test
    fun `status is idle without media`() {
        assertEquals(PlaybackStatus.IDLE, PlaybackLogic.resolveStatus(3, true, true, hasMedia = false))
    }

    @Test
    fun `status maps ready states`() {
        assertEquals(PlaybackStatus.PLAYING, PlaybackLogic.resolveStatus(3, true, true, true))
        assertEquals(PlaybackStatus.PAUSED, PlaybackLogic.resolveStatus(3, false, false, true))
    }

    @Test
    fun `status buffering only when playback is wanted`() {
        assertEquals(PlaybackStatus.BUFFERING, PlaybackLogic.resolveStatus(2, false, true, true))
        assertEquals(PlaybackStatus.PAUSED, PlaybackLogic.resolveStatus(2, false, false, true))
    }

    @Test
    fun `status ended and idle player with media`() {
        assertEquals(PlaybackStatus.ENDED, PlaybackLogic.resolveStatus(4, false, false, true))
        assertEquals(PlaybackStatus.IDLE, PlaybackLogic.resolveStatus(1, false, false, true))
    }

    // ---------- тривалість / позиція ----------

    @Test
    fun `duration falls back to track metadata when player duration is unknown`() {
        assertEquals(180_000L, PlaybackLogic.normalizeDuration(PlaybackLogic.TIME_UNSET, 180))
        assertEquals(180_000L, PlaybackLogic.normalizeDuration(0L, 180))
        assertEquals(200_500L, PlaybackLogic.normalizeDuration(200_500L, 180))
        assertEquals(0L, PlaybackLogic.normalizeDuration(PlaybackLogic.TIME_UNSET, -5))
    }

    @Test
    fun `position is clamped into duration`() {
        assertEquals(0L, PlaybackLogic.clampPosition(-10, 1000))
        assertEquals(1000L, PlaybackLogic.clampPosition(5000, 1000))
        assertEquals(400L, PlaybackLogic.clampPosition(400, 1000))
        assertEquals(400L, PlaybackLogic.clampPosition(400, 0))
    }

    @Test
    fun `progress fraction`() {
        assertEquals(0.25f, PlaybackLogic.progressFraction(25_000, 100_000), 0.0001f)
        assertEquals(0f, PlaybackLogic.progressFraction(5_000, 0), 0f)
        assertEquals(1f, PlaybackLogic.progressFraction(200_000, 100_000), 0f)
        assertEquals(0f, PlaybackLogic.progressFraction(-5, 100_000), 0f)
    }

    @Test
    fun `fraction to position round trips`() {
        assertEquals(50_000L, PlaybackLogic.fractionToPosition(0.5f, 100_000))
        assertEquals(0L, PlaybackLogic.fractionToPosition(-1f, 100_000))
        assertEquals(100_000L, PlaybackLogic.fractionToPosition(2f, 100_000))
        assertEquals(0L, PlaybackLogic.fractionToPosition(0.5f, 0))
    }

    @Test
    fun `previous restarts the track only after threshold`() {
        assertFalse(PlaybackLogic.shouldRestartTrackOnPrevious(3_000))
        assertTrue(PlaybackLogic.shouldRestartTrackOnPrevious(3_001))
    }

    // ---------- буфер ----------

    @Test
    fun `buffered fraction is never behind playback position`() {
        assertEquals(0.4f, PlaybackLogic.bufferedFraction(40_000, 10_000, 100_000), 0.0001f)
        assertEquals(0.7f, PlaybackLogic.bufferedFraction(40_000, 70_000, 100_000), 0.0001f)
        assertEquals(1f, PlaybackLogic.bufferedFraction(40_000, 500_000, 100_000), 0f)
        assertEquals(0f, PlaybackLogic.bufferedFraction(40_000, 70_000, 0), 0f)
    }

    @Test
    fun `reconcile buffered clamps reported value`() {
        assertEquals(40_000L, PlaybackLogic.reconcileBuffered(40_000, 0, 100_000))
        assertEquals(40_000L, PlaybackLogic.reconcileBuffered(40_000, -5, 100_000))
        assertEquals(100_000L, PlaybackLogic.reconcileBuffered(40_000, 900_000, 100_000))
        assertEquals(70_000L, PlaybackLogic.reconcileBuffered(40_000, 70_000, 100_000))
    }

    @Test
    fun `buffer grows as playback advances`() {
        // Моделюємо кілька тіків: позиція рухається, плеєр дозавантажує вперед.
        var previous = 0f
        for ((pos, buffered) in listOf(0L to 10_000L, 1_000L to 25_000L, 2_000L to 40_000L)) {
            val f = PlaybackLogic.bufferedFraction(pos, buffered, 100_000)
            assertTrue(f >= previous)
            previous = f
        }
    }

    @Test
    fun `seek forward inside buffered range keeps the buffer`() {
        val result = PlaybackLogic.bufferAfterSeek(
            previousPositionMs = 10_000, previousBufferedMs = 60_000, targetMs = 30_000, durationMs = 100_000
        )
        assertEquals(60_000L, result)
    }

    @Test
    fun `seek beyond buffered range restarts buffer from the target`() {
        val result = PlaybackLogic.bufferAfterSeek(
            previousPositionMs = 10_000, previousBufferedMs = 60_000, targetMs = 80_000, durationMs = 100_000
        )
        assertEquals(80_000L, result)
    }

    @Test
    fun `seek backwards restarts buffer from the target`() {
        val result = PlaybackLogic.bufferAfterSeek(
            previousPositionMs = 50_000, previousBufferedMs = 90_000, targetMs = 20_000, durationMs = 100_000
        )
        assertEquals(20_000L, result)
    }

    @Test
    fun `seek to the exact buffered edge keeps buffer`() {
        val result = PlaybackLogic.bufferAfterSeek(10_000, 60_000, 60_000, 100_000)
        assertEquals(60_000L, result)
    }

    @Test
    fun `buffer after seek never exceeds duration`() {
        val result = PlaybackLogic.bufferAfterSeek(10_000, 60_000, 500_000, 100_000)
        assertEquals(100_000L, result)
    }
}
