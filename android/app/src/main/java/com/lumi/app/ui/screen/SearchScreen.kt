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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumi.app.data.mock.MockDataStore
import com.lumi.app.data.model.Album
import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.Track
import com.lumi.app.state.TrackLikeController
import com.lumi.app.ui.component.CoverArt
import com.lumi.app.ui.component.TrackContextMenu
import com.lumi.app.ui.component.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenProfile: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val likeController = remember { TrackLikeController() }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(query) {
        val (matchingTracks, matchingArtists, matchingAlbums) = MockDataStore.searchEverything(query)
        tracks = matchingTracks
        artists = matchingArtists
        albums = matchingAlbums
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Трек, виконавець або альбом") },
                shape = RoundedCornerShape(18.dp)
            )
            IconButton(onClick = onOpenProfile) { Icon(Icons.Filled.Person, contentDescription = "Профіль") }
        }

        if (query.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.size(12.dp))
                    Text("Пошукаємо музику", style = MaterialTheme.typography.titleLarge)
                    Text("Введіть назву треку, виконавця або альбому", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }

        if (tracks.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нічого не знайдено")
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            if (artists.isNotEmpty()) {
                item { SearchSectionTitle("Виконавці") }
                items(artists) { artist ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenArtist(artist.id) }.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverArt(artist.imageUrl, artist.name, Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artist.name, style = MaterialTheme.typography.titleMedium)
                            Text("${artist.monthlyListeners} слухачів на місяць", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Filled.Person, contentDescription = null)
                    }
                }
            }
            if (albums.isNotEmpty()) {
                item { SearchSectionTitle("Альбоми") }
                items(albums) { album ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenAlbum(album.id) }.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverArt(album.coverUrl, album.title, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(album.title, style = MaterialTheme.typography.titleMedium)
                            Text(album.artist?.name ?: album.artistName.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Filled.Album, contentDescription = null)
                    }
                }
            }
            if (tracks.isNotEmpty()) {
                item { SearchSectionTitle("Треки") }
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onClick = { com.lumi.app.player.PlayerController.playQueue(tracks, tracks.indexOf(track)) },
                        onToggleLike = { likeController.toggle(track, scope) },
                        onOpenMenu = { menuTrack = track }
                    )
                }
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
private fun SearchSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}
