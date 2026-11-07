package com.lumi.app.ui.component

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.app.R
import com.lumi.app.data.model.Playlist
import com.lumi.app.data.model.ReportReason
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.AlbumRepository
import com.lumi.app.data.repository.PlaylistRepository
import com.lumi.app.data.repository.ReportRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.player.DownloadManagerHolder
import com.lumi.app.player.PlayerController
import com.lumi.app.state.LibraryStateHolder
import com.lumi.app.state.TrackLikeController
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.i18n.uiText
import com.lumi.app.ui.theme.LumiGlassColors
import com.lumi.app.ui.theme.lumiGlass
import kotlinx.coroutines.launch

/** Текст пунктів шторки: 16px/15, як у CSS-експорті плеєра. */
private val TrackMenuTextStyle = TextStyle(
    fontFamily = Montserrat,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 15.sp,
    color = LumiGlassColors.Text
)

/**
 * Нижня шторка дій з треком у стилі макета: скляний градієнт, рамка
 * rgba(255,255,255,.1), radius 10, відступи 14/19 і ритм рядків 36px.
 *
 * Важливо: жодна дія не керує відтворенням напряму — меню лише додає трек
 * у чергу, плейлист, завантаження тощо. Навігація не змінює стан плеєра.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextMenu(
    track: Track,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onGoToAlbum: ((String) -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
    /** Коли задано, «Поскаржитися» відкриває повноекранний діалог плеєра. */
    onReportTrack: (() -> Unit)? = null,
    playlistRepository: PlaylistRepository = remember { PlaylistRepository() },
    reportRepository: ReportRepository = remember { ReportRepository() },
    albumRepository: AlbumRepository = remember { AlbumRepository() },
    /** Коли задано, у меню з'являється пункт «Прибрати з плейлиста». */
    onRemoveFromPlaylist: (() -> Unit)? = null
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
    var isReporting by remember { mutableStateOf(false) }

    fun report() {
        if (onReportTrack != null) {
            onDismiss()
            onReportTrack()
        } else {
            showReport = true
        }
    }

    fun openAlbum() {
        val target = onGoToAlbum ?: return
        val knownId = track.albumId
        if (!knownId.isNullOrBlank()) {
            target(knownId)
            onDismiss()
            return
        }
        scope.launch {
            val albumId = when (val result = albumRepository.getAlbums(limit = 50)) {
                is ApiResult.Success -> result.data.albums
                    .firstOrNull { album ->
                        album.title.equals(track.album.orEmpty(), ignoreCase = true) &&
                            (track.artistName.isNullOrBlank() ||
                                album.artist?.name == track.artist?.name)
                    }
                    ?.id
                is ApiResult.Error -> null
            }
            if (albumId != null) {
                target(albumId)
                onDismiss()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.track_menu_album_not_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        // Непрозорий фон: крізь шторку не просвічує екран під нею.
        // Градієнт lumiGlass напівпрозорий, тому йому потрібна щільна підкладка.
        containerColor = LumiGlassColors.Background,
        contentColor = LumiGlassColors.Text,
        scrimColor = Color.Black.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .lumiGlass(radius = 10.dp)
                .padding(horizontal = 19.dp, vertical = 14.dp)
        ) {
            ContextMenuAction(Icons.Outlined.Share, stringResource(R.string.track_menu_share)) {
                val shareText = "${track.title} — ${track.artist?.name ?: track.artistName ?: ""}"
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
                onDismiss()
            }

            ContextMenuAction(
                icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                label = stringResource(
                    if (isLiked) R.string.player_like_remove else R.string.player_like_add
                )
            ) {
                likeController.toggle(track)
            }

            ContextMenuAction(
                icon = if (isDownloaded) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
                label = when {
                    isDownloaded -> stringResource(R.string.track_download_remove)
                    isDownloading -> stringResource(R.string.track_downloading)
                    else -> stringResource(R.string.track_download)
                },
                iconSize = 15.dp
            ) {
                if (isDownloaded) DownloadManagerHolder.removeDownload(track.id)
                else DownloadManagerHolder.downloadTrack(track)
            }

            ContextMenuAction(
                icon = Icons.Outlined.QueueMusic,
                label = stringResource(R.string.track_menu_add_to_queue)
            ) {
                PlayerController.addToQueue(track)
                onDismiss()
            }

            ContextMenuAction(
                icon = Icons.Outlined.PlaylistAdd,
                label = stringResource(R.string.track_menu_add_to_playlist)
            ) {
                showAddToPlaylist = true
            }

            if (onGoToAlbum != null) {
                ContextMenuAction(
                    icon = Icons.Outlined.Album,
                    label = stringResource(R.string.track_menu_go_album)
                ) {
                    openAlbum()
                }
            }

            onRemoveFromLibrary?.let { remove ->
                ContextMenuAction(
                    icon = Icons.Outlined.RemoveCircleOutline,
                    label = stringResource(R.string.track_menu_remove_from_library),
                    iconSize = 15.dp
                ) {
                    remove()
                    onDismiss()
                }
            }

            ContextMenuAction(
                icon = Icons.Outlined.Person,
                label = stringResource(R.string.track_menu_go_artist)
            ) {
                track.artist?.id?.let(onGoToArtist)
                onDismiss()
            }

            onRemoveFromPlaylist?.let { remove ->
                ContextMenuAction(
                    icon = Icons.Outlined.RemoveCircleOutline,
                    label = stringResource(R.string.track_menu_remove_from_playlist),
                    iconSize = 15.dp
                ) {
                    remove()
                    onDismiss()
                }
            }

            ContextMenuAction(
                icon = Icons.Outlined.Report,
                label = stringResource(R.string.track_menu_report),
                iconSize = 13.dp
            ) {
                report()
            }
        }
    }


    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            track = track,
            repository = playlistRepository,
            onDismiss = { showAddToPlaylist = false },
            onAdded = {
                Toast.makeText(
                    context,
                    context.getString(R.string.track_menu_added_to_playlist),
                    Toast.LENGTH_SHORT
                ).show()
                showAddToPlaylist = false
                onDismiss()
            }
        )
    }

    if (showReport) {
        ReportTrackDialog(
            onDismiss = { showReport = false },
            onSubmit = { reason ->
                // Вікно закриваємо одразу, а результат показуємо Toast-ом:
                // у меню треку немає власного Snackbar-хоста.
                if (!isReporting) {
                    isReporting = true
                    showReport = false

                    scope.launch {
                        val result = reportRepository.reportTrack(track.id, reason)
                        isReporting = false

                        val message = if (result.isSuccess) {
                            uiText(R.string.report_sent)
                        } else {
                            uiText(R.string.report_error_send)
                        }

                        Toast.makeText(
                            context,
                            message.resolveCurrent(context),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}

/**
 * Пункт меню з ритмом 36px: сам текст займає 15px, решта — повітря
 * (21px), як у CSS-експорті. Клікабельна вся ширина рядка.
 */
@Composable
private fun ContextMenuAction(
    icon: ImageVector,
    label: String,
    iconSize: Dp = 14.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopStart
    ) {
        Row(
            modifier = Modifier.height(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LumiGlassColors.Text,
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = label, style = TrackMenuTextStyle)
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    track: Track,
    repository: PlaylistRepository,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }

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
            Column {
                when {
                    isLoading -> Text(stringResource(R.string.common_loading))
                    playlists.isEmpty() -> Text(stringResource(R.string.playlist_picker_empty))
                    else -> LazyColumn {
                        items(playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.title) },
                                modifier = Modifier.clickable {
                                    if (repository.addTrackToPlaylist(playlist.id, track) is ApiResult.Success) {
                                        onAdded()
                                    } else {
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { showCreate = true },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.library_add_playlist))
                }
            }
        }
    )

    if (showCreate) {
        PlaylistEditorDialog(
            initialTitle = "",
            initialDescription = "",
            isEdit = false,
            onDismiss = { showCreate = false },
            onConfirm = { title, description ->
                val created = (repository.createPlaylist(title, description) as? ApiResult.Success)?.data
                showCreate = false
                if (created == null) {
                    onDismiss()
                } else if (repository.addTrackToPlaylist(created.id, track) is ApiResult.Success) {
                    onAdded()
                } else {
                    onDismiss()
                }
            }
        )
    }
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == selected, onClick = { selected = reason })
                        Text(reportReasonLabel(reason), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(selected) }) {
                Text(stringResource(R.string.report_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
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

