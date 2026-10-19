package com.lumi.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumi.app.R
import com.lumi.app.data.model.Album
import com.lumi.app.data.model.Playlist
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.AlbumRepository
import com.lumi.app.data.repository.PlaylistRepository
import com.lumi.app.data.repository.TrackRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.player.PlayerController
import com.lumi.app.state.LibraryStateHolder
import com.lumi.app.state.TrackLikeController
import com.lumi.app.ui.component.CoverArt
import com.lumi.app.ui.component.TrackRow
import com.lumi.app.ui.component.TrackContextMenu
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenProfile: () -> Unit
) {
    val playlistRepository = remember { PlaylistRepository() }
    val trackRepository = remember { TrackRepository() }
    val albumRepository = remember { AlbumRepository() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var liked by remember { mutableStateOf<List<Track>>(emptyList()) }
    var history by remember { mutableStateOf<List<Track>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val likeController = remember { TrackLikeController() }

    // Улюблені — спільний стан: коли серце перемикають на плеєрі чи в іншій
    // вкладці, список має одразу перезавантажитися, а не чекати на повторний
    // вхід у вкладку.
    val likedIds by LibraryStateHolder.likedTrackIds.collectAsState()
    val pendingLikeIds by LibraryStateHolder.pendingLikeIds.collectAsState()
    val likedKey = if (tab == 1) likedIds else emptySet<String>()
    val pendingLikedKey = if (tab == 1) pendingLikeIds else emptySet<String>()

    LaunchedEffect(reloadKey, tab, likedKey, pendingLikedKey) {
        when (tab) {
            0 -> when (val result = playlistRepository.getPlaylists()) {
                is ApiResult.Success -> playlists = result.data.playlists
                is ApiResult.Error -> playlists = emptyList()
            }
            1 -> {
                val revision = LibraryStateHolder.likedRevision()
                when (val result = trackRepository.getLikedTracks(limit = 100)) {
                    is ApiResult.Success -> {
                        liked = result.data.tracks
                        val ids = result.data.tracks.map { it.id }
                        if (result.data.total <= ids.size) {
                            LibraryStateHolder.applyLikedSnapshot(ids, revision)
                        } else {
                            LibraryStateHolder.seedLiked(ids)
                        }
                    }
                    is ApiResult.Error -> liked = emptyList()
                }
            }
            2 -> when (val result = albumRepository.getAlbums()) {
                is ApiResult.Success -> albums = result.data.albums
                is ApiResult.Error -> albums = emptyList()
            }
            3 -> when (val result = trackRepository.getHistory()) {
                is ApiResult.Success -> history = result.data.tracks
                is ApiResult.Error -> history = emptyList()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenProfile) { Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_profile)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                R.string.library_tab_playlists,
                R.string.library_tab_liked,
                R.string.library_tab_albums,
                R.string.library_tab_history
            ).forEachIndexed { index, labelRes ->
                FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(stringResource(labelRes)) })
            }
        }
        Spacer(Modifier.size(6.dp))

        when (tab) {
            0 -> PlaylistLibrary(playlists, onOpenPlaylist, onCreate = { showCreate = true })
            1 -> TrackLibrary(
                liked,
                emptyMessage = stringResource(R.string.library_liked_empty),
                onPlay = { PlayerController.playQueue(liked, it) },
                onLike = { track ->
                    liked = liked.filterNot { it.id == track.id }
                    likeController.toggle(track)
                },
                onOpenArtist = onOpenArtist
            )
            2 -> AlbumLibrary(albums, onOpenAlbum)
            3 -> TrackLibrary(history, emptyMessage = stringResource(R.string.library_history_empty), onPlay = { PlayerController.playQueue(history, it) }, onLike = { track -> likeController.toggle(track) }, onOpenArtist = onOpenArtist)
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { title, description, isPublic ->
                scope.launch { playlistRepository.createPlaylist(title, description, isPublic); reloadKey++ }
                showCreate = false
                tab = 0
            }
        )
    }
}

@Composable
private fun PlaylistLibrary(playlists: List<Playlist>, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.library_playlists_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = onCreate) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.library_create)) }
        }
        if (playlists.isEmpty()) {
            EmptyState(stringResource(R.string.library_empty_playlists))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(playlist.id) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(playlist.coverUrl, playlist.title, Modifier.size(62.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    R.string.library_playlist_subtitle,
                                    pluralStringResource(
                                        R.plurals.tracks_count,
                                        playlist.tracksCount,
                                        playlist.tracksCount
                                    ),
                                    stringResource(
                                        if (playlist.isPublic) R.string.settings_value_public
                                        else R.string.settings_value_private
                                    )
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackLibrary(
    tracks: List<Track>,
    emptyMessage: String,
    onPlay: (Int) -> Unit,
    onLike: (Track) -> Unit,
    onOpenArtist: (String) -> Unit
) {
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    if (tracks.isEmpty()) {
        EmptyState(emptyMessage, icon = Icons.Filled.History)
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onClick = { onPlay(tracks.indexOf(track)) },
                    onToggleLike = { onLike(track) },
                    onOpenMenu = { menuTrack = track }
                )
            }
        }
    }
    menuTrack?.let { track ->
        TrackContextMenu(
            track = track,
            sheetState = sheetState,
            onDismiss = { menuTrack = null },
            onGoToArtist = onOpenArtist
        )
    }
}

@Composable
private fun AlbumLibrary(albums: List<Album>, onOpen: (String) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState(stringResource(R.string.library_albums_empty), Icons.Filled.Album)
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(albums, key = { it.id }) { album ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(album.id) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(album.coverUrl, album.title, Modifier.size(62.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.library_album_subtitle,
                                album.artist?.name ?: album.artistName.orEmpty(),
                                pluralStringResource(
                                    R.plurals.tracks_count,
                                    album.tracksCount,
                                    album.tracksCount
                                )
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.History) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.size(10.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String, String?, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_new_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.library_name)) }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.library_description)) }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(if (isPublic) R.string.settings_value_public else R.string.settings_value_private))
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title, description.ifBlank { null }, isPublic) }) { Text(stringResource(R.string.library_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
