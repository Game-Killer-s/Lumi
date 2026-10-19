package com.lumi.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumi.app.R
import com.lumi.app.player.PlaybackLogic
import com.lumi.app.util.DurationFormatter

/**
 * Інтерактивна шкала відтворення:
 *  - три шари: фон, завантажена (буферизована) частина та відтворена частина;
 *  - тап і перетягування перемотують трек, нова позиція передається в [onSeek]
 *    лише після відпускання пальця (під час drag показується позиція під пальцем);
 *  - під шкалою — поточний час і загальна тривалість.
 *
 * Компонент повністю stateless щодо плеєра: усі значення приходять параметрами.
 */
@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    bufferedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 7.dp,
    dragThumbRadius: Dp = 9.dp,
    horizontalPadding: Dp = 8.dp,
    timeTextStyle: TextStyle = MaterialTheme.typography.labelMedium
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val seekEnabled = enabled && durationMs > 0

    val playedFraction = dragFraction ?: PlaybackLogic.progressFraction(positionMs, durationMs)
    val bufferedFraction = PlaybackLogic.bufferedFraction(positionMs, bufferedPositionMs, durationMs)
    val shownPositionMs = dragFraction
        ?.let { PlaybackLogic.fractionToPosition(it, durationMs) }
        ?: positionMs
    val progressDescription = stringResource(R.string.player_progress_description)
    val progressState = stringResource(
        R.string.player_progress_state,
        DurationFormatter.formatMillis(shownPositionMs),
        DurationFormatter.formatMillis(durationMs)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .height(32.dp)
                .testTag("player_progress_bar")
                .semantics {
                    contentDescription = progressDescription
                    stateDescription = progressState
                    progressBarRangeInfo = ProgressBarRangeInfo(playedFraction, 0f..1f)
                    if (seekEnabled) {
                        setProgress { target ->
                            currentOnSeek(PlaybackLogic.fractionToPosition(target, durationMs))
                            true
                        }
                    }
                }
                .pointerInput(seekEnabled, durationMs) {
                    if (!seekEnabled) return@pointerInput
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        currentOnSeek(PlaybackLogic.fractionToPosition(fraction, durationMs))
                    }
                }
                .pointerInput(seekEnabled, durationMs) {
                    if (!seekEnabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let {
                                currentOnSeek(PlaybackLogic.fractionToPosition(it, durationMs))
                            }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barHeight = trackHeight.toPx()
                val top = (size.height - barHeight) / 2f
                val radius = CornerRadius(barHeight / 2f)
                val width = size.width

                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, top),
                    size = Size(width, barHeight),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = bufferedColor,
                    topLeft = Offset(0f, top),
                    size = Size(width * bufferedFraction, barHeight),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, top),
                    size = Size(width * playedFraction, barHeight),
                    cornerRadius = radius
                )
                val currentThumbRadius = if (dragFraction != null) dragThumbRadius.toPx()
                else thumbRadius.toPx()
                drawCircle(
                    color = activeColor,
                    radius = currentThumbRadius,
                    center = Offset(width * playedFraction, size.height / 2f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = DurationFormatter.formatMillis(shownPositionMs),
                style = timeTextStyle,
                modifier = Modifier.testTag("player_current_time")
            )
            Text(
                text = DurationFormatter.formatMillis(durationMs),
                style = timeTextStyle,
                modifier = Modifier.testTag("player_total_time")
            )
        }
    }
}
