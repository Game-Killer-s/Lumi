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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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

    LaunchedEffect(reloadKey, tab) {
        when (tab) {
            0 -> when (val result = playlistRepository.getPlaylists()) {
                is ApiResult.Success -> playlists = result.data.playlists
                is ApiResult.Error -> playlists = emptyList()
            }
            1 -> when (val result = trackRepository.getLikedTracks()) {
                is ApiResult.Success -> {
                    liked = result.data.tracks
                    LibraryStateHolder.seedLiked(liked.map { it.id })
                }
                is ApiResult.Error -> liked = emptyList()
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
            Text("Бібліотека", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenProfile) { Icon(Icons.Filled.Person, contentDescription = "Профіль") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Плейлисти", "Улюблені", "Альбоми", "Історія").forEachIndexed { index, label ->
                FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(label) })
            }
        }
        Spacer(Modifier.size(6.dp))

        when (tab) {
            0 -> PlaylistLibrary(playlists, onOpenPlaylist, onCreate = { showCreate = true })
            1 -> TrackLibrary(
                liked,
                emptyMessage = "Улюблених треків поки немає",
                onPlay = { PlayerController.playQueue(liked, it) },
                onLike = { track ->
                    liked = liked.filterNot { it.id == track.id }
                    likeController.toggle(track, scope)
                },
                onOpenArtist = onOpenArtist
            )
            2 -> AlbumLibrary(albums, onOpenAlbum)
            3 -> TrackLibrary(history, emptyMessage = "Історія прослуховування порожня", onPlay = { PlayerController.playQueue(history, it) }, onLike = { track -> likeController.toggle(track, scope) }, onOpenArtist = onOpenArtist)
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
            Text("Ваші плейлисти", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = onCreate) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.size(6.dp)); Text("Створити") }
        }
        if (playlists.isEmpty()) {
            EmptyState("Створіть перший плейлист")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(playlist.id) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(playlist.coverUrl, playlist.title, Modifier.size(62.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.title, style = MaterialTheme.typography.titleMedium)
                            Text("${playlist.tracksCount} треків · ${if (playlist.isPublic) "Публічний" else "Приватний"}", style = MaterialTheme.typography.bodySmall)
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
        EmptyState("Альбомів поки немає", Icons.Filled.Album)
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(albums, key = { it.id }) { album ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(album.id) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(album.coverUrl, album.title, Modifier.size(62.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.title, style = MaterialTheme.typography.titleMedium)
                        Text("${album.artist?.name ?: album.artistName.orEmpty()} · ${album.tracksCount} треків", style = MaterialTheme.typography.bodySmall)
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
        title = { Text("Новий плейлист") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Назва") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Опис") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                    Spacer(Modifier.size(8.dp))
                    Text(if (isPublic) "Публічний" else "Приватний")
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title, description.ifBlank { null }, isPublic) }) { Text("Створити") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}
