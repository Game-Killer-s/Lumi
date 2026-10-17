package com.lumi.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumi.app.data.model.Track
import com.lumi.app.player.PlayerController
import com.lumi.app.state.TrackLikeController
import com.lumi.app.ui.component.TrackContextMenu
import com.lumi.app.ui.component.TrackRow
import com.lumi.app.ui.viewmodel.GenreResultsUiState
import com.lumi.app.ui.viewmodel.GenreResultsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreResultsScreen(genre: String, onOpenArtist: (String) -> Unit) {
    val viewModel: GenreResultsViewModel = viewModel(
        key = "genre-$genre",
        factory = viewModelFactory { initializer { GenreResultsViewModel(genre) } }
    )
    val uiState by viewModel.uiState.collectAsState()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val likeController = remember { TrackLikeController() }

    Column(Modifier.fillMaxSize().testTag("genre_results")) {
        Text(
            text = genre,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp).testTag("genre_title")
        )
        when (val state = uiState) {
            is GenreResultsUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is GenreResultsUiState.Error ->
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message)
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Спробувати знову")
                    }
                }
            is GenreResultsUiState.Empty ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("У цьому жанрі поки немає треків") }
            is GenreResultsUiState.Loaded ->
                LazyColumn {
                    itemsIndexed(state.tracks) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = { PlayerController.playQueue(state.tracks, index) },
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
