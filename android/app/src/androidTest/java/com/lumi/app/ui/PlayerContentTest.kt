package com.lumi.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.lumi.app.player.PlaybackStatus
import com.lumi.app.player.PlayerRepeatMode
import com.lumi.app.player.PlayerUiState
import com.lumi.app.ui.screen.PlayerContent
import com.lumi.app.ui.theme.LumiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** UI-тести повноекранного плеєра на довільних [PlayerUiState] (без реального ExoPlayer). */
class PlayerContentTest {

    @get:Rule
    val rule = createComposeRule()

    private var playPause = 0
    private var next = 0
    private var previous = 0
    private var shuffle = 0
    private var repeat = 0
    private val seeks = mutableListOf<Long>()

    private fun playing() = PlayerUiState(
        currentTrack = TestData.track("1"),
        isPlaying = true,
        status = PlaybackStatus.PLAYING,
        positionMs = 42_000,
        durationMs = 210_000,
        bufferedPositionMs = 120_000,
        hasNext = true
    )

    private fun show(state: PlayerUiState) {
        rule.setContent {
            LumiTheme {
                PlayerContent(
                    state = state,
                    onBack = {},
                    onTogglePlayPause = { playPause++ },
                    onNext = { next++ },
                    onPrevious = { previous++ },
                    onSeek = { seeks += it },
                    onToggleShuffle = { shuffle++ },
                    onCycleRepeat = { repeat++ }
                )
            }
        }
    }

    @Test
    fun showsTrackStatusAndTimes() {
        show(playing())

        rule.onNodeWithTag("player_title").assertTextEquals("Song 1")
        rule.onNodeWithTag("player_status").assertTextEquals("Відтворюється")
        rule.onNodeWithTag("player_current_time").assertTextEquals("0:42")
        rule.onNodeWithTag("player_total_time").assertTextEquals("3:30")
    }

    @Test
    fun pausedStateShowsPauseLabel() {
        show(playing().copy(isPlaying = false, status = PlaybackStatus.PAUSED))
        rule.onNodeWithTag("player_status").assertTextEquals("Пауза")
    }

    @Test
    fun bufferingShowsSpinnerInsteadOfPlayButton() {
        show(playing().copy(isBuffering = true, status = PlaybackStatus.BUFFERING))

        rule.onNodeWithTag("player_buffering").assertIsDisplayed()
        rule.onNodeWithTag("player_play_pause").assertDoesNotExist()
        rule.onNodeWithTag("player_status").assertTextEquals("Буферизація…")
    }

    @Test
    fun transportButtonsInvokeCallbacks() {
        show(playing())

        rule.onNodeWithTag("player_play_pause").performClick()
        rule.onNodeWithTag("player_next").performClick()
        rule.onNodeWithTag("player_previous").performClick()

        assertEquals(1, playPause)
        assertEquals(1, next)
        assertEquals(1, previous)
    }

    @Test
    fun nextIsDisabledWhenThereIsNoNextTrack() {
        show(playing().copy(hasNext = false))
        rule.onNodeWithTag("player_next").assertIsNotEnabled()
    }

    @Test
    fun nextIsEnabledWhenThereIsANextTrack() {
        show(playing())
        rule.onNodeWithTag("player_next").assertIsEnabled()
    }

    @Test
    fun shuffleAndRepeatButtonsInvokeCallbacks() {
        show(playing())

        rule.onNodeWithTag("player_shuffle").performClick()
        rule.onNodeWithTag("player_repeat").performClick()

        assertEquals(1, shuffle)
        assertEquals(1, repeat)
    }

    @Test
    fun activeModesAreDisplayed() {
        show(playing().copy(shuffleEnabled = true, repeatMode = PlayerRepeatMode.ONE))

        rule.onNodeWithTag("player_mode_label")
            .assertTextEquals("Перемішування: увімкнено · Повтор: один трек")
    }

    @Test
    fun defaultModesAreDisplayedAsOff() {
        show(playing())

        rule.onNodeWithTag("player_mode_label")
            .assertTextEquals("Перемішування: вимкнено · Повтор: вимкнено")
    }

    @Test
    fun tappingTheProgressBarSeeksProportionally() {
        show(playing())

        rule.onNodeWithTag("player_progress_bar").performTouchInput { click(center) }

        assertEquals(1, seeks.size)
        // Центр шкали ≈ середина треку (±1 с на округлення пікселів).
        assertTrue("seek=${seeks[0]}", seeks[0] in 104_000L..106_000L)
    }

    @Test
    fun accessibilitySetProgressSeeksToThatFraction() {
        show(playing())

        rule.onNodeWithTag("player_progress_bar")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }

        assertEquals(listOf(52_500L), seeks)
    }

    @Test
    fun emptyQueueShowsPlaceholder() {
        show(PlayerUiState())
        rule.onNodeWithTag("player_empty").assertIsDisplayed()
    }
}
