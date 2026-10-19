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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumi.app.R
import com.lumi.app.data.model.Track
import com.lumi.app.player.PlayerController
import com.lumi.app.ui.component.SkeletonCarouselRow
import com.lumi.app.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenArtist: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val genres = stringArrayResource(R.array.home_genres).toList()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> HomeSkeleton()
            uiState.errorMessage != null && uiState.recommended.isEmpty() && uiState.newReleases.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.home_load_error))
                }
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lumi",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.home_notifications))
                            }
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_profile))
                            }
                        }
                    }

                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            items(genres) { genre ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(genre) },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }

                    if (uiState.recommended.isNotEmpty()) {
                        item {
                            SectionTitle(stringResource(R.string.home_section_popular))
                            TrackCarousel(tracks = uiState.recommended) { index ->
                                PlayerController.playQueue(uiState.recommended, index)
                            }
                        }
                    }

                    if (uiState.newReleases.isNotEmpty()) {
                        item {
                            SectionTitle(stringResource(R.string.home_section_new_releases))
                            TrackCarousel(tracks = uiState.newReleases) { index ->
                                PlayerController.playQueue(uiState.newReleases, index)
                            }
                        }
                    }

                    item {
                        SectionTitle(stringResource(R.string.home_section_mixes))
                        TrackCarousel(tracks = uiState.recommended.shuffled()) { index ->
                            PlayerController.playQueue(uiState.recommended, index)
                        }
                    }

                    item {
                        SectionTitle(stringResource(R.string.home_section_recent))
                        TrackCarousel(tracks = uiState.newReleases.shuffled()) { index ->
                            PlayerController.playQueue(uiState.newReleases, index)
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
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPlay(index) }
            ) {
                AsyncImage(
                    model = track.coverUrl ?: track.albumCoverUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(12.dp))
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
        SectionTitle(stringResource(R.string.home_section_popular))
        SkeletonCarouselRow()
        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.home_section_new_releases))
        SkeletonCarouselRow()
    }
}
