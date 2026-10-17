package com.lumi.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lumi.app.player.PlaybackLogic
import com.lumi.app.player.PlayerController
import com.lumi.app.player.PlayerRepeatMode
import com.lumi.app.player.PlayerUiState
import com.lumi.app.ui.component.PlayerSeekBar

/** Повноекранний плеєр, що працює з глобальним [PlayerController]. */
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val state by PlayerController.state.collectAsState()
    PlayerContent(
        state = state,
        onBack = onBack,
        onTogglePlayPause = PlayerController::togglePlayPause,
        onNext = PlayerController::skipToNext,
        onPrevious = PlayerController::skipToPrevious,
        onSeek = PlayerController::seekTo,
        onToggleShuffle = PlayerController::toggleShuffle,
        onCycleRepeat = PlayerController::cycleRepeatMode
    )
}

/** Stateless-частина екрана: легко перевіряється UI-тестами з довільним [PlayerUiState]. */
@Composable
fun PlayerContent(
    state: PlayerUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("player_back")) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Згорнути плеєр")
            }
            Text(
                text = "Зараз грає",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        if (track == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нічого не відтворюється", modifier = Modifier.testTag("player_empty"))
            }
            return@Column
        }

        Spacer(modifier = Modifier.height(16.dp))
        AsyncImage(
            model = track.coverUrl ?: track.albumCoverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("player_title")
        )
        Text(
            text = track.artist?.name ?: track.artistName ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = PlaybackLogic.statusLabel(state.status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("player_status")
        )

        Spacer(modifier = Modifier.height(16.dp))
        PlayerSeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            bufferedPositionMs = state.bufferedPositionMs,
            onSeek = onSeek
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle, modifier = Modifier.testTag("player_shuffle")) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = PlaybackLogic.shuffleLabel(state.shuffleEnabled),
                    tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.testTag("player_previous")) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Попередній трек",
                    modifier = Modifier.size(36.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp).testTag("player_buffering"),
                        strokeWidth = 3.dp,
                        color = Color.White
                    )
                } else {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(68.dp).testTag("player_play_pause")
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Пауза" else "Відтворити",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
            IconButton(
                onClick = onNext,
                enabled = state.hasNext,
                modifier = Modifier.testTag("player_next")
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Наступний трек",
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = onCycleRepeat, modifier = Modifier.testTag("player_repeat")) {
                Icon(
                    imageVector = if (state.repeatMode == PlayerRepeatMode.ONE) Icons.Filled.RepeatOne
                    else Icons.Filled.Repeat,
                    contentDescription = PlaybackLogic.repeatLabel(state.repeatMode),
                    tint = if (state.repeatMode != PlayerRepeatMode.OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = PlaybackLogic.shuffleLabel(state.shuffleEnabled) + " · " +
                PlaybackLogic.repeatLabel(state.repeatMode),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.testTag("player_mode_label")
        )
    }
}
