package com.lumi.app.ui.component

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumi.app.R
import com.lumi.app.data.model.Playlist
import com.lumi.app.data.model.ReportReason
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.PlaylistRepository
import com.lumi.app.data.repository.ReportRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.player.DownloadManagerHolder
import com.lumi.app.state.LibraryStateHolder
import com.lumi.app.state.TrackLikeController
import kotlinx.coroutines.launch

/**
 * Bottom sheet with contextual track actions. IMPORTANT: none of these
 * actions call [com.lumi.app.player.PlayerController] — opening the menu,
 * liking, adding to a playlist, sharing, downloading or reporting must
 * never start/stop/skip active playback. Navigation (go to artist) is the
 * only thing that can indirectly change screens; it does not touch the
 * player either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextMenu(
    track: Track,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onGoToArtist: (String) -> Unit,
    playlistRepository: PlaylistRepository = remember { PlaylistRepository() },
    reportRepository: ReportRepository = remember { ReportRepository() }
) {
    val scope = rememberCoroutineScope()
    val likeController = remember { TrackLikeController() }
    val context = LocalContext.current
    val likedIds by LibraryStateHolder.likedTrackIds.collectAsState()
    val downloadedIds by DownloadManagerHolder.downloadedTrackIds.collectAsState()
    val downloadingIds by DownloadManagerHolder.downloadingTrackIds.collectAsState()

    val isLiked = track.id in likedIds
    val isDownloaded = track.id in downloadedIds
    val isDownloading = track.id in downloadingIds

    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(track.title) },
                supportingContent = { Text(track.artist?.name ?: track.artistName ?: "") }
            )

            ContextMenuAction(
                icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                label = stringResource(
                    if (isLiked) R.string.player_like_remove else R.string.player_like_add
                )
            ) {
                likeController.toggle(track)
            }

            ContextMenuAction(
                icon = Icons.Outlined.PlaylistAdd,
                label = stringResource(R.string.track_menu_add_to_playlist)
            ) {
                showAddToPlaylist = true
            }

            ContextMenuAction(Icons.Filled.Share, stringResource(R.string.track_menu_share)) {
                val shareText = "${track.title} — ${track.artist?.name ?: track.artistName ?: ""}"
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
                onDismiss()
            }

            ContextMenuAction(Icons.Filled.Person, stringResource(R.string.track_menu_go_artist)) {
                track.artist?.id?.let(onGoToArtist)
                onDismiss()
            }

            // TODO(backend/model): Track only carries an album *name* string,
            // not an id, so "Go to Album" can't navigate to AlbumDetailScreen
            // yet. Wire this up once Track exposes albumId.

            ContextMenuAction(
                icon = if (isDownloaded) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
                label = when {
                    isDownloaded -> stringResource(R.string.track_download_remove)
                    isDownloading -> stringResource(R.string.track_downloading)
                    else -> stringResource(R.string.track_download)
                }
            ) {
                if (isDownloaded) DownloadManagerHolder.removeDownload(track.id)
                else DownloadManagerHolder.downloadTrack(track)
            }

            ContextMenuAction(Icons.Filled.Report, stringResource(R.string.track_menu_report)) {
                showReport = true
            }
        }
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            trackId = track.id,
            repository = playlistRepository,
            onDismiss = { showAddToPlaylist = false }
        )
    }

    if (showReport) {
        ReportTrackDialog(
            onDismiss = { showReport = false },
            onSubmit = { reason ->
                scope.launch { reportRepository.reportTrack(track.id, reason) }
                showReport = false
            }
        )
    }
}

@Composable
private fun ContextMenuAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun AddToPlaylistDialog(
    trackId: String,
    repository: PlaylistRepository,
    onDismiss: () -> Unit
) {
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val result = repository.getPlaylists()
        if (result is ApiResult.Success) playlists = result.data.playlists
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
        title = { Text(stringResource(R.string.track_menu_add_to_playlist)) },
        text = {
            if (isLoading) {
                Text(stringResource(R.string.common_loading))
            } else if (playlists.isEmpty()) {
                Text(stringResource(R.string.playlist_picker_empty))
            } else {
                LazyColumn {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.title) },
                            modifier = Modifier.clickable {
                                scope.launch { repository.addTrackToPlaylist(playlist.id, trackId) }
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ReportTrackDialog(onDismiss: () -> Unit, onSubmit: (ReportReason) -> Unit) {
    var selected by remember { mutableStateOf(ReportReason.EXPLICIT_CONTENT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_report_track)) },
        text = {
            Column {
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = reason == selected, onClick = { selected = reason }),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == selected, onClick = { selected = reason })
                        Text(reportReasonLabel(reason), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(selected) }) { Text(stringResource(R.string.report_submit)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun reportReasonLabel(reason: ReportReason): String = stringResource(
    when (reason) {
        ReportReason.EXPLICIT_CONTENT -> R.string.report_reason_explicit
        ReportReason.COPYRIGHT -> R.string.report_reason_copyright
        ReportReason.WRONG_METADATA -> R.string.report_reason_metadata
        ReportReason.SPAM -> R.string.report_reason_spam
        ReportReason.OTHER -> R.string.report_reason_other
    }
)
