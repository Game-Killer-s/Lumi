package com.lumi.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumi.app.data.model.Track
import com.lumi.app.player.PlayerController
import com.lumi.app.ui.component.SkeletonCarouselRow
import com.lumi.app.ui.viewmodel.HomeViewModel

/**
 * Home feed. Sections map to the design reference: personalized
 * recommendations ("Популярно зараз" — TODO(backend): backed by
 * popular/new tracks until /tracks/recommended exists, see
 * TrackRepository.getRecommendedTracks) and "Свіжі релізи" (new
 * releases). Bottom nav + persistent mini player are provided by
 * MainScaffold, which hosts this screen.
 */
@Composable
fun HomeScreen(
    onOpenArtist: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> HomeSkeleton()
            uiState.errorMessage != null && uiState.recommended.isEmpty() && uiState.newReleases.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage ?: "Помилка завантаження")
                }
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
                    item {
                        Text(
                            text = "Lumi",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (uiState.recommended.isNotEmpty()) {
                        item {
                            SectionTitle("Популярно зараз")
                            TrackCarousel(tracks = uiState.recommended) { index ->
                                PlayerController.playQueue(uiState.recommended, index)
                            }
                        }
                    }
                    if (uiState.newReleases.isNotEmpty()) {
                        item {
                            SectionTitle("Свіжі релізи")
                            TrackCarousel(tracks = uiState.newReleases) { index ->
                                PlayerController.playQueue(uiState.newReleases, index)
                            }
                        }
                    }
                    if (uiState.genres.isNotEmpty()) {
                        item {
                            SectionTitle("Категорії музики")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(uiState.genres) { genre ->
                                    AssistChip(onClick = {}, label = { Text(genre.name) })
                                }
                            }
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun TrackCarousel(
    tracks: List<Track>,
    onPlay: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(tracks.size) { index ->
            val track = tracks[index]
            Column(
                modifier = Modifier
                    .size(width = 130.dp, height = 170.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPlay(index) }
            ) {
                AsyncImage(
                    model = track.coverUrl ?: track.albumCoverUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist?.name ?: track.artistName ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        SectionTitle("Популярно зараз")
        SkeletonCarouselRow()
        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("Свіжі релізи")
        SkeletonCarouselRow()
    }
}
