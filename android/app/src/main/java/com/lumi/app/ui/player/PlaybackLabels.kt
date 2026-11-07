package com.lumi.app.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lumi.app.R
import com.lumi.app.player.PlaybackStatus
import com.lumi.app.player.PlayerRepeatMode

/**
 * Локалізовані підписи плеєра. Сам [com.lumi.app.player.PlaybackLogic] лишається
 * чистою логікою без залежності від ресурсів, а тексти беруться з strings.xml
 * і автоматично перемикаються разом із мовою застосунку.
 */

@Composable
fun playbackStatusLabel(status: PlaybackStatus): String = stringResource(
    when (status) {
        PlaybackStatus.IDLE -> R.string.player_status_idle
        PlaybackStatus.BUFFERING -> R.string.player_status_buffering
        PlaybackStatus.PLAYING -> R.string.player_status_playing
        PlaybackStatus.PAUSED -> R.string.player_status_paused
        PlaybackStatus.ENDED -> R.string.player_status_ended
    }
)

@Composable
fun repeatModeLabel(mode: PlayerRepeatMode): String = stringResource(
    when (mode) {
        PlayerRepeatMode.OFF -> R.string.player_repeat_state_off
        PlayerRepeatMode.ALL -> R.string.player_repeat_state_all
        PlayerRepeatMode.ONE -> R.string.player_repeat_state_one
    }
)

@Composable
fun shuffleStateLabel(enabled: Boolean): String = stringResource(
    if (enabled) R.string.player_shuffle_state_on else R.string.player_shuffle_state_off
)

@Composable
fun playbackModeSummary(shuffleEnabled: Boolean, repeatMode: PlayerRepeatMode): String =
    stringResource(
        R.string.player_mode_summary,
        shuffleStateLabel(shuffleEnabled),
        repeatModeLabel(repeatMode)
    )
