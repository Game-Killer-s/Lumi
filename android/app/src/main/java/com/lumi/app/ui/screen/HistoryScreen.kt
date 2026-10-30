package com.lumi.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumi.app.R
import com.lumi.app.data.local.HistoryEntry
import com.lumi.app.data.model.Track
import com.lumi.app.player.PlayerController
import com.lumi.app.ui.component.CoverArt
import com.lumi.app.ui.component.TrackContextMenu
import com.lumi.app.ui.history.HistoryDayBucket
import com.lumi.app.ui.history.HistoryRules
import com.lumi.app.ui.i18n.currentAppLocale
import com.lumi.app.ui.viewmodel.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val now = remember { System.currentTimeMillis() }
    val locale = currentAppLocale()
    val grouped = remember(state.entries) {
        state.entries.groupBy { HistoryRules.bucket(it.playedAt, now) }
    }
    val tracks = state.entries.map { it.track }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }
            Text(
                text = stringResource(R.string.library_history_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showClearDialog = true },
                enabled = state.entries.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.history_clear)
                )
            }
        }

        if (state.entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.library_history_empty),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                HistoryDayBucket.entries.forEach { bucket ->
                    val bucketEntries = grouped[bucket].orEmpty()
                    if (bucketEntries.isEmpty()) return@forEach

                    item(key = "header-$bucket") {
                        Text(
                            text = stringResource(bucketLabel(bucket)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(bucketEntries, key = { it.track.id }) { entry ->
                        HistoryRow(
                            entry = entry,
                            isEarlier = bucket == HistoryDayBucket.EARLIER,
                            locale = locale,
                            onPlay = {
                                val index = tracks.indexOfFirst { it.id == entry.track.id }
                                PlayerController.playQueueOrResume(tracks, index.coerceAtLeast(0))
                            },
                            onOpenMenu = { menuTrack = entry.track }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) {
                    Text(
                        text = stringResource(R.string.history_clear_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    menuTrack?.let { track ->
        TrackContextMenu(
            track = track,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { menuTrack = null },
            onGoToArtist = onOpenArtist
        )
    }
}

private fun bucketLabel(bucket: HistoryDayBucket): Int = when (bucket) {
    HistoryDayBucket.TODAY -> R.string.history_today
    HistoryDayBucket.YESTERDAY -> R.string.history_yesterday
    HistoryDayBucket.EARLIER -> R.string.history_earlier
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    isEarlier: Boolean,
    locale: java.util.Locale,
    onPlay: () -> Unit,
    onOpenMenu: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(
            model = entry.track.coverUrl ?: entry.track.albumCoverUrl,
            contentDescription = entry.track.title,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.track.artist?.name ?: entry.track.artistName.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (isEarlier) {
                    HistoryRules.dateLabel(entry.playedAt, locale)
                } else {
                    HistoryRules.timeLabel(entry.playedAt, locale)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (isEarlier) {
                Text(
                    text = HistoryRules.timeLabel(entry.playedAt, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        IconButton(onClick = onOpenMenu) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.track_more_actions)
            )
        }
    }
}

