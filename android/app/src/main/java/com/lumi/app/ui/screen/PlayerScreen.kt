package com.lumi.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumi.app.R
import com.lumi.app.data.model.Comment
import com.lumi.app.data.model.Track
import com.lumi.app.data.repository.TrackRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.network.TokenManager
import com.lumi.app.player.PlayerController
import com.lumi.app.player.PlayerRepeatMode
import com.lumi.app.player.PlayerUiState
import com.lumi.app.state.LibraryStateHolder
import com.lumi.app.state.TrackLikeController
import com.lumi.app.ui.auth.AuthBackgroundColor
import com.lumi.app.ui.auth.AuthErrorColor
import com.lumi.app.ui.auth.AuthGradient
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.component.PlayerSeekBar
import com.lumi.app.ui.component.TrackContextMenu
import com.lumi.app.ui.i18n.LumiFormatters
import com.lumi.app.ui.i18n.currentAppLocale
import com.lumi.app.ui.i18n.localized
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.i18n.uiText
import com.lumi.app.ui.player.CommentsEvent
import com.lumi.app.ui.player.CommentsRules
import com.lumi.app.ui.player.CommentsUiState
import com.lumi.app.ui.player.CommentsViewModel
import com.lumi.app.ui.player.DeleteCommentDialog
import com.lumi.app.ui.player.LyricsUiState
import com.lumi.app.ui.player.LyricsViewModel
import com.lumi.app.ui.player.PlayerMessage
import com.lumi.app.ui.player.ReportDialog
import com.lumi.app.ui.player.ReportEvent
import com.lumi.app.ui.player.ReportViewModel
import com.lumi.app.ui.player.playbackModeSummary
import com.lumi.app.ui.player.playbackStatusLabel

/** Поле вводу коментаря: трохи світліше за фон плеєра. */
private val PlayerInputColor = Color(0xFF231549)

/** Незаповнена частина шкали прогресу — rgba(121, 121, 121, 0.2). */
private val PlayerProgressTrackColor = Color(0xFF797979).copy(alpha = 0.2f)

/** Приглушений текст: підписи стану, час і текст пісні. */
private val PlayerMutedTextColor = Color.White.copy(alpha = 0.55f)

/** Горизонтальні відступи макета: 16px від краю екрана. */
private val PlayerHorizontalPadding = 16.dp

/** Обкладинка займає 307 із 380px робочої ширини макета. */
private const val PlayerCoverWidthFraction = 307f / 380f

/** Співвідношення сторін обкладинки з макета: 307 × 321. */
private const val PlayerCoverAspectRatio = 307f / 321f

/** Час під шкалою прогресу: дрібний приглушений підпис. */
private val PlayerTimeTextStyle = TextStyle(
    fontFamily = Montserrat,
    fontSize = 9.sp,
    lineHeight = 11.sp,
    color = PlayerMutedTextColor
)


/**
 * Повноекранний плеєр у стилі макета: обкладинка, інформація про трек,
 * прогрес, транспорт, текст пісні та коментарі на одній прокручуваній сторінці.
 *
 * Екран працює з глобальним [PlayerController], а текст і коментарі тримає
 * у ViewModel-ях, тому перехід між треками не скидає стан екрана.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {}
) {
    val state by PlayerController.state.collectAsState()
    val lyricsViewModel: LyricsViewModel = viewModel()
    val commentsViewModel: CommentsViewModel = viewModel()
    val reportViewModel: ReportViewModel = viewModel()

    val snackbarHostState = remember { SnackbarHostState() }
    val likeController = remember { TrackLikeController() }
    val trackRepository = remember { TrackRepository() }
    val likedIds by LibraryStateHolder.likedTrackIds.collectAsState()

    val track = state.currentTrack
    val trackId = track?.id?.takeIf { it.isNotBlank() }
    val currentUser = remember { TokenManager.getCurrentUser() }

    // Серце орієнтується лише на спільний стан улюблених: дані черги можуть
    // бути застарілими (наприклад, трек зі списку «Улюблені», який уже
    // прибрали), тому track.isLiked тут не використовуємо.
    val isLiked = trackId != null && trackId in likedIds

    LaunchedEffect(trackId) {
        if (trackId == null) {
            return@LaunchedEffect
        }

        lyricsViewModel.load(trackId)
        commentsViewModel.open(trackId)
    }

    // Підтягуємо улюблені з сервера, щоб серце було заповненим одразу після
    // холодного старту (публічні списки треків не містять персональний isLiked).
    // Відповідь авторитетна: якщо трек уже прибрали з улюблених, серце не
    // залишиться заповненим через застарілі дані черги.
    LaunchedEffect(Unit) {
        val revision = LibraryStateHolder.likedRevision()
        when (val result = trackRepository.getLikedTracks(limit = 100)) {
            is ApiResult.Success -> {
                val ids = result.data.tracks.map { it.id }
                if (result.data.total <= ids.size) {
                    LibraryStateHolder.applyLikedSnapshot(ids, revision)
                } else {
                    // Список обрізаний лімітом — не можемо вважати його повним.
                    LibraryStateHolder.seedLiked(ids)
                }
            }

            is ApiResult.Error -> Unit
        }
    }

    // Підсвічуємо рядок тексту разом із позицією відтворення.
    LaunchedEffect(state.positionMs, state.durationMs) {
        lyricsViewModel.onProgress(state.progressFraction)
    }

    LaunchedEffect(Unit) {
        commentsViewModel.events.collect { event ->
            when (event) {
                is CommentsEvent.Message ->
                    snackbarHostState.showSnackbar(event.text.resolveCurrent())

                CommentsEvent.Changed -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        reportViewModel.events.collect { event ->
            when (event) {
                is ReportEvent.Message ->
                    snackbarHostState.showSnackbar(event.text.resolveCurrent())
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerContent(
            state = state,
            onBack = onBack,
            onTogglePlayPause = PlayerController::togglePlayPause,
            onNext = PlayerController::skipToNext,
            onPrevious = PlayerController::skipToPrevious,
            onSeek = PlayerController::seekTo,
            onToggleShuffle = PlayerController::toggleShuffle,
            onCycleRepeat = PlayerController::cycleRepeatMode,
            isLiked = isLiked,
            onToggleLike = { track?.let { likeController.toggle(it) } },
            lyricsState = lyricsViewModel.uiState,
            onRetryLyrics = lyricsViewModel::retry,
            commentsState = commentsViewModel.uiState,
            onCommentDraftChange = commentsViewModel::onDraftChange,
            onSendComment = commentsViewModel::send,
            onDeleteComment = commentsViewModel::requestDelete,
            onReportComment = { comment ->
                trackId?.let { reportViewModel.openCommentReport(it, comment) }
            },
            onLoadMoreComments = commentsViewModel::loadMore,
            onRetryComments = commentsViewModel::retry,
            currentUserId = currentUser?.id,
            currentNickname = currentUser?.nickname.orEmpty(),
            onReportTrack = { trackId?.let(reportViewModel::openTrackReport) },
            onGoToArtist = onOpenArtist,
            onGoToAlbum = onOpenAlbum
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }

    // Підтвердження видалення власного коментаря показуємо поверх сторінки.
    commentsViewModel.uiState.pendingDelete?.let { comment ->
        DeleteCommentDialog(
            comment = comment,
            onConfirm = commentsViewModel::confirmDelete,
            onDismiss = commentsViewModel::cancelDelete
        )
    }

    ReportDialog(
        state = reportViewModel.uiState,
        onReason = reportViewModel::selectReason,
        onDetailsChange = reportViewModel::onDetailsChange,
        onSubmit = reportViewModel::submit,
        onDismiss = reportViewModel::dismiss
    )
}


/**
 * Вміст плеєра без прив'язки до ViewModel-ей: його легко перевіряти в тестах
 * і рендерити в прев'ю.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerContent(
    state: PlayerUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {},
    lyricsState: LyricsUiState = LyricsUiState(),
    onRetryLyrics: () -> Unit = {},
    commentsState: CommentsUiState = CommentsUiState(),
    onCommentDraftChange: (String) -> Unit = {},
    onSendComment: () -> Unit = {},
    onDeleteComment: (Comment) -> Unit = {},
    onReportComment: (Comment) -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onRetryComments: () -> Unit = {},
    currentUserId: String? = null,
    currentNickname: String = "",
    onReportTrack: () -> Unit = {},
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuthBackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PlayerHorizontalPadding)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        PlayerTopBar(
            onBack = onBack,
            onOpenMenu = { track?.let { menuTrack = it } }
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (track == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.player_empty_state),
                    style = TextStyle(
                        fontFamily = Montserrat,
                        fontSize = 15.sp,
                        color = PlayerMutedTextColor
                    ),
                    modifier = Modifier.testTag("player_empty")
                )
            }
            return@Column
        }

        AlbumCover(
            coverUrl = track.coverUrl,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(PlayerCoverWidthFraction)
                .aspectRatio(PlayerCoverAspectRatio)
                .testTag("player_cover")
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 5.dp, end = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = TextStyle(
                        fontFamily = Montserrat,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("player_title")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist?.name ?: track.artistName.orEmpty(),
                    style = TextStyle(
                        fontFamily = Montserrat,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("player_artist")
                )
            }

            IconButton(
                onClick = onToggleLike,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("player_like")
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isLiked) R.string.player_like_remove else R.string.player_like_add
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(23.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        PlayerSeekBar(
            positionMs = state.positionMs,
            bufferedPositionMs = state.bufferedPositionMs,
            durationMs = state.durationMs,
            onSeek = onSeek,
            enabled = state.durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
            activeColor = Color.White,
            bufferedColor = Color.White.copy(alpha = 0.32f),
            inactiveColor = PlayerProgressTrackColor,
            trackHeight = 4.dp,
            thumbRadius = 6.dp,
            dragThumbRadius = 8.dp,
            horizontalPadding = 0.dp,
            timeTextStyle = PlayerTimeTextStyle
        )


        Spacer(modifier = Modifier.height(20.dp))

        TransportControls(
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            shuffleEnabled = state.shuffleEnabled,
            repeatMode = state.repeatMode,
            canSkipNext = state.hasNext,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat
        )

        Spacer(modifier = Modifier.height(24.dp))

        PlaybackStatusRow(state = state)

        Spacer(modifier = Modifier.height(18.dp))

        SpeakerHintRow()

        Spacer(modifier = Modifier.height(32.dp))

        LyricsSection(
            state = lyricsState,
            isPlaying = state.isPlaying,
            onRetry = onRetryLyrics
        )

        Spacer(modifier = Modifier.height(32.dp))

        CommentsSection(
            state = commentsState,
            currentUserId = currentUserId,
            currentNickname = currentNickname,
            onDraftChange = onCommentDraftChange,
            onSend = onSendComment,
            onDelete = onDeleteComment,
            onReport = onReportComment,
            onLoadMore = onLoadMoreComments,
            onRetry = onRetryComments
        )

        Spacer(
            modifier = Modifier
                .height(24.dp)
                .navigationBarsPadding()
        )
    }

    menuTrack?.let { menuTrackItem ->
        TrackContextMenu(
            track = menuTrackItem,
            sheetState = menuSheetState,
            onDismiss = { menuTrack = null },
            onGoToArtist = onGoToArtist,
            onGoToAlbum = onGoToAlbum,
            onReportTrack = {
                menuTrack = null
                onReportTrack()
            }
        )
    }
}


/** Верхня панель: згортання плеєра та меню дій з треком. */
@Composable
private fun PlayerTopBar(
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("player_back")
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.player_collapse),
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier
                .size(48.dp)
                .testTag("player_menu")
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.player_menu),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Квадратна обкладинка із заокругленням 22px, як у макеті. */
@Composable
private fun AlbumCover(coverUrl: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .background(Color(0xFF2A1B4D), shape)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(64.dp)
            )
        }
    }
}


/** Шафл, перемикання треків, велика кнопка відтворення та повтор. */
@Composable
private fun TransportControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: PlayerRepeatMode,
    canSkipNext: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleShuffle,
            modifier = Modifier
                .size(42.dp)
                .testTag("player_shuffle")
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = stringResource(R.string.player_shuffle),
                tint = if (shuffleEnabled) AuthTextColor else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("player_previous")
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.player_previous),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(57.dp)
                    .clip(CircleShape)
                    .background(AuthGradient)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        color = AuthTextColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("player_buffering")
                    )
                } else {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.testTag("player_play_pause")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.player_pause else R.string.player_play
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onNext,
                enabled = canSkipNext,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("player_next")
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.player_next),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        IconButton(
            onClick = onCycleRepeat,
            modifier = Modifier
                .size(42.dp)
                .testTag("player_repeat")
        ) {
            Icon(
                imageVector = if (repeatMode == PlayerRepeatMode.ONE) {
                    Icons.Filled.RepeatOne
                } else {
                    Icons.Filled.Repeat
                },
                contentDescription = stringResource(
                    when (repeatMode) {
                        PlayerRepeatMode.OFF -> R.string.player_repeat_off
                        PlayerRepeatMode.ALL -> R.string.player_repeat_all
                        PlayerRepeatMode.ONE -> R.string.player_repeat_one
                    }
                ),
                tint = if (repeatMode == PlayerRepeatMode.OFF) Color.White else AuthTextColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


/** Рядок стану під транспортом: крапка та «Відтворюється» / «Пауза». */
@Composable
private fun PlaybackStatusRow(state: PlayerUiState, modifier: Modifier = Modifier) {
    val statusText = playbackStatusLabel(state.status)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color.White.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = statusText,
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = PlayerMutedTextColor
                ),
                modifier = Modifier.testTag("player_status")
            )
        }

        // Режими відтворення лишаємо окремим рядком: він потрібен і для
        // озвучування стану, і як підказка, чому кнопки шафлу/повтору підсвічені.
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = playbackModeSummary(state.shuffleEnabled, state.repeatMode),
            style = TextStyle(
                fontFamily = Montserrat,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("player_mode_label")
        )
    }
}

/** Декоративний рядок гучності та черги з нижньої частини макета. */
@Composable
private fun SpeakerHintRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(19.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(19.dp)
        )
    }
}


/** Блок тексту пісні: заголовок, згортання, картка з рядками та підказка. */
@Composable
private fun LyricsSection(
    state: LyricsUiState,
    isPlaying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_lyrics")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.player_lyrics),
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("player_lyrics_toggle")
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.player_lyrics_collapse else R.string.player_lyrics_expand
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!expanded) {
            return@Column
        }

        val error = state.error

        when {
            state.isLoading -> PlayerMessage(
                text = uiText(R.string.lyrics_loading),
                isLoading = true
            )

            error != null -> PlayerMessage(text = error, onRetry = onRetry)

            state.lines.isEmpty() -> LyricsEmptyState()

            else -> LyricsCard(state = state, isPlaying = isPlaying)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.lyrics_hint),
            style = TextStyle(
                fontFamily = Montserrat,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = PlayerMutedTextColor
            ),
            modifier = Modifier.testTag("player_lyrics_hint")
        )
    }
}

/** Напівпрозора картка з текстом пісні та підсвіченим поточним рядком. */
@Composable
private fun LyricsCard(state: LyricsUiState, isPlaying: Boolean) {
    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .clip(cardShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), cardShape)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            state.lines.forEachIndexed { index, line ->
                val isHighlighted = index == state.highlightedLine
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = Montserrat,
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            state.highlightedLine < 0 ->
                                if (isPlaying) Color.White.copy(alpha = 0.8f) else PlayerMutedTextColor
                            isHighlighted -> Color.White
                            else -> PlayerMutedTextColor
                        },
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .testTag("player_lyric_line_$index")
                )
            }
        }
    }
}


/**
 * Запасний варіант, коли тексту немає: іконка ноти та пояснення.
 * Слухач бачить, що функція доступна, навіть якщо пісня без слів.
 */
@Composable
private fun LyricsEmptyState() {
    val cardShape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .clip(cardShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), cardShape)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.lyrics_empty),
            style = TextStyle(
                fontFamily = Montserrat,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = PlayerMutedTextColor
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("player_lyrics_empty")
        )
    }
}


/** Блок коментарів під текстом пісні: заголовок, поле вводу та список. */
@Composable
private fun CommentsSection(
    state: CommentsUiState,
    currentUserId: String?,
    currentNickname: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDelete: (Comment) -> Unit,
    onReport: (Comment) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_comments")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.player_comments),
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            if (state.total > 0) {
                Text(
                    text = pluralStringResource(R.plurals.comments_count, state.total, state.total),
                    style = TextStyle(
                        fontFamily = Montserrat,
                        fontSize = 12.sp,
                        color = PlayerMutedTextColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        CommentInput(
            draft = state.draft,
            isSending = state.isSending,
            isTooLong = state.isDraftTooLong,
            nickname = currentNickname,
            onDraftChange = onDraftChange,
            onSend = onSend
        )

        Spacer(modifier = Modifier.height(20.dp))

        val error = state.error
        val comments = state.comments

        when {
            state.isLoading -> PlayerMessage(
                text = uiText(R.string.comments_loading),
                isLoading = true
            )

            error != null && comments.isEmpty() ->
                PlayerMessage(text = error, onRetry = onRetry)

            comments.isEmpty() ->
                PlayerMessage(text = uiText(R.string.comments_empty))

            else -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    comments.forEachIndexed { index, comment ->
                        CommentRow(
                            comment = comment,
                            isOwn = CommentsRules.isOwn(comment, currentUserId),
                            isDeleting = state.deletingId == comment.id,
                            onDelete = { onDelete(comment) },
                            onReport = { onReport(comment) }
                        )
                        if (index < comments.lastIndex) {
                            // Тонкий розділювач між коментарями, як у макеті.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.06f))
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error.localized(),
                        style = TextStyle(
                            fontFamily = Montserrat,
                            fontSize = 12.sp,
                            color = AuthErrorColor
                        )
                    )
                }

                if (state.isLoadingMore) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(
                        color = AuthTextColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(20.dp)
                            .testTag("player_comments_loading_more")
                    )
                } else if (state.canLoadMore) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onLoadMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_comments_load_more")
                    ) {
                        Text(
                            text = stringResource(R.string.comments_load_more),
                            fontFamily = Montserrat,
                            fontSize = 13.sp,
                            color = AuthTextColor
                        )
                    }
                }
            }
        }
    }
}


/** Поле нового коментаря: аватар, підказка та кнопка надсилання. */
@Composable
private fun CommentInput(
    draft: String,
    isSending: Boolean,
    isTooLong: Boolean,
    nickname: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val canSend = CommentsRules.canSend(draft, isSending) && !isTooLong

    val submit = {
        if (canSend) {
            // Ховаємо клавіатуру, щоб після надсилання було видно результат.
            focusManager.clearFocus()
            onSend()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PlayerInputColor)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialAvatar(name = nickname, size = 52.dp)

        Spacer(modifier = Modifier.width(12.dp))

        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            textStyle = TextStyle(
                fontFamily = Montserrat,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = Color.White
            ),
            cursorBrush = SolidColor(AuthTextColor),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier
                .weight(1f)
                .testTag("player_comment_field"),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = stringResource(R.string.player_comment_hint),
                            style = TextStyle(
                                fontFamily = Montserrat,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.45f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )

        IconButton(
            onClick = submit,
            enabled = canSend,
            modifier = Modifier
                .size(44.dp)
                .testTag("player_comment_send")
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    color = AuthTextColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.comments_send),
                    tint = if (canSend) AuthTextColor else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    if (isTooLong) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.comments_error_too_long, CommentsRules.MAX_LENGTH),
            style = TextStyle(
                fontFamily = Montserrat,
                fontSize = 11.sp,
                color = AuthErrorColor
            )
        )
    }
}


/** Один коментар: аватар, ім'я, дата, текст і дія (видалити або поскаржитись). */
@Composable
private fun CommentRow(
    comment: Comment,
    isOwn: Boolean,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    val author = comment.user?.nickname?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.comments_author_unknown)
    val locale = currentAppLocale()
    val date = remember(comment.createdAt, locale) {
        LumiFormatters.formatDate(comment.createdAt, locale)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        InitialAvatar(
            name = author,
            size = 40.dp,
            avatarUrl = comment.user?.avatarUrl
        )

        Spacer(modifier = Modifier.width(11.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = author,
                    style = TextStyle(
                        fontFamily = Montserrat,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (date != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = date,
                        style = TextStyle(
                            fontFamily = Montserrat,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = PlayerMutedTextColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = comment.text,
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            )

            if (comment.isHidden) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.comments_hidden_label),
                    style = TextStyle(
                        fontFamily = Montserrat,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = AuthErrorColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (isDeleting) {
                // Поки видалення в дорозі, дію не показуємо, щоб не
                // відправити той самий запит удруге.
                Text(
                    text = stringResource(R.string.common_loading),
                    style = TextStyle(
                        fontFamily = Montserrat,
                        fontSize = 11.sp,
                        color = PlayerMutedTextColor
                    ),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .sizeIn(minHeight = 40.dp)
                        .clickable(
                            role = Role.Button,
                            onClick = if (isOwn) onDelete else onReport
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(
                            if (isOwn) R.string.comments_delete else R.string.comments_report
                        ),
                        style = TextStyle(
                            fontFamily = Montserrat,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOwn) AuthErrorColor else AuthTextColor
                        ),
                        modifier = Modifier.testTag(
                            if (isOwn) "player_comment_delete_${comment.id}"
                            else "player_comment_report_${comment.id}"
                        )
                    )
                }
            }
        }
    }
}

/** Круглий аватар: фото користувача, а без нього — перша літера імені. */
@Composable
private fun InitialAvatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♪"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6D54D9), Color(0xFF4B1F6F))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Text(
                text = initial,
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontSize = (size.value * 0.34f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )
        }
    }
}

