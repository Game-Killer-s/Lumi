package com.lumi.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.lumi.app.data.model.Track
import com.lumi.app.player.PlayerController
import com.lumi.app.state.TrackLikeController
import com.lumi.app.ui.component.TrackContextMenu
import com.lumi.app.ui.component.TrackRow
import com.lumi.app.ui.viewmodel.ArtistUiState
import com.lumi.app.ui.viewmodel.ArtistViewModel

@Composable
fun ArtistScreen(artistId: String, onOpenArtist: (String) -> Unit) {
    val viewModel: ArtistViewModel = viewModel(
        key = "artist-$artistId",
        factory = viewModelFactory { initializer { ArtistViewModel(artistId) } }
    )
    val uiState by viewModel.uiState.collectAsState()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val likeController = remember { TrackLikeController() }

    when (val state = uiState) {
        is ArtistUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is ArtistUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(state.message) }
        is ArtistUiState.Loaded -> {
            LazyColumn {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = state.artist.imageUrl,
                            contentDescription = state.artist.name,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(state.artist.name, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = "${state.artist.monthlyListeners} слухачів на місяць",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (state.isFollowed) {
                                OutlinedButton(onClick = { viewModel.toggleFollow() }) { Text("Ви підписані") }
                            } else {
                                Button(onClick = { viewModel.toggleFollow() }) { Text("Підписатися") }
                            }
                        }
                    }
                }
                if (state.topTracks.isNotEmpty()) {
                    item {
                        Text(
                            "Топ треки",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(state.topTracks.size) { index ->
                        val track = state.topTracks[index]
                        TrackRow(
                            track = track,
                            index = index,
                            onClick = { PlayerController.playQueue(state.topTracks, index) },
                            onToggleLike = { likeController.toggle(track, scope) },
                            onOpenMenu = { menuTrack = track }
                        )
                    }
                }
                if (state.releases.isNotEmpty()) {
                    item {
                        Text(
                            "Релізи",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                            items(state.releases) { album ->
                                Column(modifier = Modifier.width(120.dp).padding(end = 12.dp)) {
                                    AsyncImage(
                                        model = album.coverUrl,
                                        contentDescription = album.title,
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Text(
                                        album.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
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
