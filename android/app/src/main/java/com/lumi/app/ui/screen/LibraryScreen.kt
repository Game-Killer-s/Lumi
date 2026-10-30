package com.lumi.app.ui.screen

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumi.app.R
import com.lumi.app.data.model.Album
import com.lumi.app.data.model.Artist
import com.lumi.app.data.model.Playlist
import com.lumi.app.data.model.Track
import com.lumi.app.player.PlayerController
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.component.CoverArt
import com.lumi.app.ui.component.PlaylistEditorDialog
import com.lumi.app.ui.component.TrackContextMenu
import com.lumi.app.ui.i18n.localized
import com.lumi.app.ui.theme.LumiGlassColors
import com.lumi.app.ui.theme.lumiAccentGradient
import com.lumi.app.ui.theme.lumiSecondaryGradient
import com.lumi.app.ui.viewmodel.LibraryUiState
import com.lumi.app.ui.viewmodel.LibraryViewModel

private enum class LibraryTab(@StringRes val labelRes: Int) {
    All(R.string.library_tab_all),
    Playlists(R.string.library_tab_playlists),
    Albums(R.string.library_tab_albums),
    Tracks(R.string.library_tab_tracks)
}

private val LibraryTitleStyle = TextStyle(
    fontFamily = Montserrat,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 15.sp,
    color = LumiGlassColors.Text
)

private val LibrarySectionStyle = TextStyle(
    fontFamily = Montserrat,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 19.sp,
    color = LumiGlassColors.Text
)

private val LibraryChipStyle = TextStyle(
    fontFamily = Montserrat,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 14.sp,
    color = LumiGlassColors.Text
)

private val LibraryRowTitleStyle = TextStyle(
    fontFamily = Montserrat,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 14.sp,
    color = LumiGlassColors.Text
)

private val LibraryRowSubtitleStyle = TextStyle(
    fontFamily = Montserrat,
    fontWeight = FontWeight.Normal,
    fontSize = 9.sp,
    lineHeight = 9.sp,
    color = LumiGlassColors.Text
)

private val LibraryEmptyStyle = TextStyle(
    fontFamily = Montserrat,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    color = LumiGlassColors.SecondaryText.copy(alpha = 0.72f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var tabIndex by rememberSaveable { mutableIntStateOf(LibraryTab.All.ordinal) }
    val tab = LibraryTab.entries[tabIndex.coerceIn(0, LibraryTab.entries.lastIndex)]
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var showArtists by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showArtists) { showArtists = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumiGlassColors.Background)
    ) {
        Column(Modifier.fillMaxSize()) {
            if (showArtists) {
                LibrarySubHeader(
                    title = stringResource(R.string.library_liked_artists),
                    onBack = { showArtists = false }
                )
                ArtistsTab(
                    artists = state.followedArtists,
                    onOpenArtist = onOpenArtist,
                    onUnfollow = viewModel::unfollowArtist,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LibraryHeader(onOpenHistory = onOpenHistory)

                Spacer(Modifier.height(33.dp))

                LibraryChips(
                    selected = tab,
                    onSelect = { tabIndex = it.ordinal }
                )

                Spacer(Modifier.height(36.dp))

                state.errorMessage?.let { message ->
                    Text(
                        text = message.localized(),
                        style = LibraryRowSubtitleStyle.copy(color = LumiGlassColors.Error),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = LumiGlassColors.SecondaryText,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        when (tab) {
                            LibraryTab.All -> AllTab(
                                state = state,
                                onOpenPlaylist = onOpenPlaylist,
                                onOpenAlbum = onOpenAlbum,
                                onOpenArtists = { showArtists = true },
                                onOpenTracks = { tabIndex = LibraryTab.Tracks.ordinal },
                                onOpenPlaylists = { tabIndex = LibraryTab.Playlists.ordinal },
                                onCreatePlaylist = { showCreateDialog = true },
                                onEditPlaylist = { playlistToEdit = it },
                                onDeletePlaylist = { playlistToDelete = it },
                                modifier = Modifier.fillMaxSize()
                            )

                            LibraryTab.Playlists -> PlaylistsTab(
                                playlists = state.playlists,
                                onCreate = { showCreateDialog = true },
                                onOpen = onOpenPlaylist,
                                onEdit = { playlistToEdit = it },
                                onDelete = { playlistToDelete = it },
                                modifier = Modifier.fillMaxSize()
                            )

                            LibraryTab.Albums -> AlbumsTab(
                                albums = state.albums,
                                onOpenAlbum = onOpenAlbum,
                                modifier = Modifier.fillMaxSize()
                            )

                            LibraryTab.Tracks -> TracksTab(
                                tracks = state.likedTracks,
                                onOpenArtist = onOpenArtist,
                                onOpenAlbum = onOpenAlbum,
                                onToggleLike = viewModel::toggleLike,
                                onOpenMenu = { menuTrack = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }


    menuTrack?.let { track ->
        TrackContextMenu(
            track = track,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { menuTrack = null },
            onGoToArtist = onOpenArtist,
            onGoToAlbum = onOpenAlbum,
            onRemoveFromLibrary = if (state.likedTracks.any { it.id == track.id }) {
                { viewModel.toggleLike(track) }
            } else {
                null
            }
        )
    }

    if (showCreateDialog || playlistToEdit != null) {
        val editing = playlistToEdit
        PlaylistEditorDialog(
            initialTitle = editing?.title.orEmpty(),
            initialDescription = editing?.description.orEmpty(),
            isEdit = editing != null,
            onDismiss = {
                showCreateDialog = false
                playlistToEdit = null
            },
            onConfirm = { title, description ->
                if (editing == null) {
                    viewModel.createPlaylist(title, description)
                    tabIndex = LibraryTab.Playlists.ordinal
                } else {
                    viewModel.renamePlaylist(editing.id, title, description)
                }
                showCreateDialog = false
                playlistToEdit = null
            }
        )
    }

    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.library_delete_playlist_title)) },
            text = { Text(stringResource(R.string.library_delete_playlist_message, playlist.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlist.id)
                    playlistToDelete = null
                }) {
                    Text(
                        text = stringResource(R.string.library_delete),
                        color = LumiGlassColors.Error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun LibraryHeader(onOpenHistory: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = LibraryTitleStyle,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onOpenHistory),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = stringResource(R.string.library_tab_history),
                tint = LumiGlassColors.Text,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LibrarySubHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = LumiGlassColors.Text,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text = title, style = LibraryTitleStyle)
    }
}

@Composable
private fun LibraryChips(selected: LibraryTab, onSelect: (LibraryTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LibraryTab.entries.forEach { tab ->
            val active = tab == selected
            val shape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .height(26.dp)
                    // Семантику чипа тримаємо на спільному вузлі: підпис і натискання
                    // озвучуються разом, хоча фон і текст лежать у різних шарах.
                    .semantics(mergeDescendants = true) {},
                contentAlignment = Alignment.Center
            ) {
                // Фон і ripple обрізаємо по скругленню окремим шаром,
                // щоб не обрізати текст: інакше нижні виносні елементи
                // літер («y», «p») зникають за краєм чипа.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .background(
                            if (active) lumiSecondaryGradient(alpha = 0.65f)
                            else lumiAccentGradient(alpha = 0.36f),
                            shape
                        )
                        .clickable { onSelect(tab) }
                )
                Text(
                    text = stringResource(tab.labelRes),
                    style = LibraryChipStyle,
                    modifier = Modifier.padding(horizontal = 17.dp)
                )
            }
        }
    }
}


@Composable
private fun AllTab(
    state: LibraryUiState,
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtists: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onEditPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val liked = state.likedTracks
    val playlists = state.playlists
    val albums = state.albums

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LibraryQuickRow(
                    coverUrl = liked.firstOrNull()?.coverUrl ?: liked.firstOrNull()?.albumCoverUrl,
                    title = stringResource(R.string.library_liked_tracks),
                    subtitle = pluralStringResource(R.plurals.tracks_count, liked.size, liked.size),
                    onClick = onOpenTracks
                )
                LibraryQuickRow(
                    coverUrl = playlists.firstOrNull()?.coverUrl
                        ?: playlists.firstOrNull()?.tracks?.firstOrNull()?.coverUrl,
                    title = stringResource(R.string.library_liked_playlists),
                    subtitle = pluralStringResource(
                        R.plurals.playlists_count,
                        playlists.size,
                        playlists.size
                    ),
                    onClick = onOpenPlaylists
                )
                LibraryQuickRow(
                    coverUrl = state.followedArtists.firstOrNull()?.imageUrl,
                    title = stringResource(R.string.library_liked_artists),
                    subtitle = pluralStringResource(
                        R.plurals.artists_count,
                        state.followedArtists.size,
                        state.followedArtists.size
                    ),
                    onClick = onOpenArtists
                )
            }
        }

        item {
            Spacer(Modifier.height(21.dp))
            SectionHeader(
                title = stringResource(R.string.library_my_playlists),
                onAction = onCreatePlaylist,
                actionDescription = stringResource(R.string.library_add_playlist)
            )
            Spacer(Modifier.height(16.dp))
        }

        if (playlists.isEmpty()) {
            item { EmptySection(stringResource(R.string.library_empty_playlists)) }
        } else {
            itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onOpen = { onOpenPlaylist(playlist.id) },
                    onEdit = { onEditPlaylist(playlist) },
                    onDelete = { onDeletePlaylist(playlist) },
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (index == playlists.lastIndex) 0.dp else 12.dp
                    )
                )
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            SectionHeader(title = stringResource(R.string.library_tab_albums))
            Spacer(Modifier.height(16.dp))
        }

        if (albums.isEmpty()) {
            item { EmptySection(stringResource(R.string.library_albums_empty)) }
        } else {
            itemsIndexed(albums, key = { _, album -> album.id }) { index, album ->
                AlbumRow(
                    album = album,
                    onOpen = { onOpenAlbum(album.id) },
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (index == albums.lastIndex) 0.dp else 12.dp
                    )
                )
            }
        }
    }
}


@Composable
private fun SectionHeader(
    title: String,
    onAction: (() -> Unit)? = null,
    actionDescription: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = LibrarySectionStyle,
            modifier = Modifier.weight(1f)
        )
        if (onAction != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onAction),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = actionDescription,
                    tint = LumiGlassColors.Text,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LibraryQuickRow(
    coverUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(57.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryCover(
            model = coverUrl,
            contentDescription = title,
            modifier = Modifier.size(57.dp),
            radius = 5.dp
        )
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = LibraryRowTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = LibraryRowSubtitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = LumiGlassColors.Text,
            modifier = Modifier.size(27.dp)
        )
    }
}



@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.library_my_playlists),
                onAction = onCreate,
                actionDescription = stringResource(R.string.library_add_playlist)
            )
            Spacer(Modifier.height(16.dp))
        }

        if (playlists.isEmpty()) {
            item { EmptySection(stringResource(R.string.library_empty_playlists)) }
        } else {
            itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onOpen = { onOpen(playlist.id) },
                    onEdit = { onEdit(playlist) },
                    onDelete = { onDelete(playlist) },
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (index == playlists.lastIndex) 0.dp else 12.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: List<Album>,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            SectionHeader(title = stringResource(R.string.library_tab_albums))
            Spacer(Modifier.height(16.dp))
        }

        if (albums.isEmpty()) {
            item { EmptySection(stringResource(R.string.library_albums_empty)) }
        } else {
            itemsIndexed(albums, key = { _, album -> album.id }) { index, album ->
                AlbumRow(
                    album = album,
                    onOpen = { onOpenAlbum(album.id) },
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (index == albums.lastIndex) 0.dp else 12.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun TracksTab(
    tracks: List<Track>,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onToggleLike: (Track) -> Unit,
    onOpenMenu: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_tab_tracks),
                    style = LibrarySectionStyle,
                    modifier = Modifier.weight(1f)
                )
                if (tracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { PlayerController.playQueueOrResume(tracks, 0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.play_all),
                            tint = LumiGlassColors.Text,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (tracks.isEmpty()) {
            item { EmptySection(stringResource(R.string.library_liked_empty)) }
        } else {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                LibraryTrackRow(
                    track = track,
                    onClick = {
                        val position = tracks.indexOfFirst { it.id == track.id }
                        PlayerController.playQueueOrResume(tracks, position)
                    },
                    onToggleLike = { onToggleLike(track) },
                    onOpenMenu = { onOpenMenu(track) },
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (index == tracks.lastIndex) 0.dp else 13.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun LibraryTrackRow(
    track: Track,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val likedIds by com.lumi.app.state.LibraryStateHolder.likedTrackIds.collectAsState()
    val isLiked = track.id in likedIds

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(57.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryCover(
            model = track.coverUrl ?: track.albumCoverUrl,
            contentDescription = track.title,
            modifier = Modifier.size(57.dp),
            radius = 5.dp
        )
        Spacer(Modifier.width(18.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = track.title,
                style = LibraryRowTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist?.name ?: track.artistName.orEmpty(),
                style = LibraryRowSubtitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onToggleLike),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(
                    if (isLiked) R.string.player_like_remove else R.string.player_like_add
                ),
                tint = LumiGlassColors.Text,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onOpenMenu),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.track_more_actions),
                tint = LumiGlassColors.Text,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(85.dp)
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryCover(
            model = playlist.coverUrl ?: playlist.tracks?.firstOrNull()?.coverUrl,
            contentDescription = playlist.title,
            modifier = Modifier.width(90.dp).height(85.dp),
            radius = 3.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = playlist.title,
                style = LibraryRowTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pluralStringResource(
                    R.plurals.tracks_count,
                    playlist.tracksCount,
                    playlist.tracksCount
                ),
                style = LibraryRowSubtitleStyle,
                maxLines = 1
            )
        }
        Box {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable { menuExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.library_more_actions),
                    tint = LumiGlassColors.Text,
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_rename)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun AlbumRow(
    album: Album,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val year = album.releaseDate?.trim()?.take(4)?.takeIf { value ->
        value.length == 4 && value.all(Char::isDigit)
    }
    val subtitle = if (year != null) {
        stringResource(R.string.library_album_label, year)
    } else {
        stringResource(R.string.library_album_type)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(85.dp)
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryCover(
            model = album.coverUrl,
            contentDescription = album.title,
            modifier = Modifier.width(89.dp).height(85.dp),
            radius = 0.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Text(
                text = album.title,
                style = LibraryRowTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = LibraryRowSubtitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryCover(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp
) {
    val shape = RoundedCornerShape(radius)
    if (!model.isNullOrBlank()) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(lumiSecondaryGradient(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = contentDescription,
                tint = LumiGlassColors.Text.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptySection(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = LibraryEmptyStyle)
    }
}

@Composable
private fun ArtistsTab(
    artists: List<Artist>,
    onOpenArtist: (String) -> Unit,
    onUnfollow: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        EmptySection(stringResource(R.string.library_artists_empty))
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        items(artists, key = { it.id }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(57.dp)
                    .clickable { onOpenArtist(artist.id) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(
                    model = artist.imageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.size(54.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        style = LibraryRowTitleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { onUnfollow(artist) }) {
                    Text(
                        text = stringResource(R.string.library_unfollow),
                        style = LibraryRowSubtitleStyle.copy(fontSize = 12.sp, lineHeight = 14.sp)
                    )
                }
            }
        }
    }
}

