package com.lumi.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.lumi.app.data.model.Album
import com.lumi.app.data.model.Track
import com.lumi.app.state.TrackLikeController
import com.lumi.app.ui.component.TrackContextMenu
import com.lumi.app.ui.component.TrackRow
import com.lumi.app.ui.viewmodel.AlbumDetailUiState
import com.lumi.app.ui.viewmodel.AlbumDetailViewModel

@Composable
fun AlbumDetailScreen(albumId: String, onOpenArtist: (String) -> Unit) {
    val viewModel: AlbumDetailViewModel = viewModel(
        key = "album-$albumId",
        factory = viewModelFactory { initializer { AlbumDetailViewModel(albumId) } }
    )
    val uiState by viewModel.uiState.collectAsState()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val likeController = remember { TrackLikeController() }

    when (val state = uiState) {
        is AlbumDetailUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is AlbumDetailUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(state.message) }
        is AlbumDetailUiState.Empty -> Column {
            AlbumHeader(state.album, canPlay = false, onPlayAll = {}, onShuffle = {})
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("У цьому альбомі ще немає треків") }
        }
        is AlbumDetailUiState.Loaded -> {
            val tracks = state.album.tracks.orEmpty()
            LazyColumn {
                item {
                    AlbumHeader(
                        album = state.album,
                        canPlay = true,
                        onPlayAll = { viewModel.playAll() },
                        onShuffle = { viewModel.shuffle() }
                    )
                }
                items(tracks.size) { index ->
                    val track = tracks[index]
                    TrackRow(
                        track = track,
                        index = index,
                        onClick = { viewModel.playFrom(index) },
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
private fun AlbumHeader(
    album: Album,
    canPlay: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        AsyncImage(
            model = album.coverUrl,
            contentDescription = album.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(album.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = album.artist?.name ?: album.artistName ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = "${album.tracksCount} треків" + (album.releaseDate?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            Button(onClick = onPlayAll, enabled = canPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("Відтворити все")
            }
            Spacer(modifier = Modifier.size(8.dp))
            OutlinedButton(onClick = onShuffle, enabled = canPlay) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("Перемішати")
            }
        }
    }
}
