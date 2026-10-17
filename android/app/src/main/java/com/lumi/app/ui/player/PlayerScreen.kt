package com.lumi.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumi.app.R
import com.lumi.app.data.model.Track
import com.lumi.app.ui.auth.AuthBackground
import com.lumi.app.ui.auth.AuthErrorColor
import com.lumi.app.ui.auth.AuthPlaceholderColor
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.i18n.uiText
import com.lumi.app.ui.subscription.LumiSecondaryButton
import com.lumi.app.ui.subscription.LumiTopBar

/** Яку шторку відкрито поверх плеєра. */
private enum class PlayerSheet { LYRICS, COMMENTS }

/**
 * Екран плеєра: обкладинка, метадані, прогрес, текст пісні, коментарі
 * та скарга на трек (ТЗ 6.3.11, ТЗ 6.7.2).
 *
 * Кожна панель має свою ViewModel, тож стан не змішується: завантаження
 * тексту пісні не впливає на список коментарів, а скарга не залежить від
 * обох. Сам екран лише зшиває їх і показує результат.
 *
 * @param trackId id з маршруту; порожній рядок означає «відкрий перший-ліпший
 * трек» — так екран можна відкрити і без глибокого посилання.
 */
@Composable
fun PlayerScreen(
    trackId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
    lyricsViewModel: LyricsViewModel = viewModel(),
    commentsViewModel: CommentsViewModel = viewModel(),
    reportViewModel: ReportViewModel = viewModel()
) {
    val state = viewModel.uiState
    val lyricsState = lyricsViewModel.uiState
    val reportState = reportViewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }

    var openSheet by remember { mutableStateOf<PlayerSheet?>(null) }

    // id із маршруту може бути порожнім: тоді ViewModel відкриє перший
    // популярний трек (плеєр поки не підключено до сервісу відтворення).
    LaunchedEffect(trackId) { viewModel.load(trackId.ifBlank { null }) }

    // Трек відомий — одразу тягнемо й текст пісні: шторка відкривається без
    // очікування. Порожній id просто скидає стан тексту.
    val resolvedTrackId = state.track?.id
    LaunchedEffect(resolvedTrackId) { lyricsViewModel.load(resolvedTrackId.orEmpty()) }

    LaunchedEffect(Unit) {
        reportViewModel.events.collect { event ->
            when (event) {
                // Результат скарги показуємо Snackbar-ом обраною мовою.
                is ReportEvent.Message ->
                    snackbarHostState.showSnackbar(event.text.resolveCurrent())
            }
        }
    }

    AuthBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LumiTopBar(
                        title = stringResource(R.string.player_topbar),
                        onBack = onBack,
                        modifier = Modifier.weight(1f)
                    )

                    // Меню «⋯»: скарга на сам трек (ТЗ 6.3.11).
                    PlayerMenuButton(
                        onReportTrack = {
                            resolvedTrackId?.let(reportViewModel::openTrackReport)
                        }
                    )
                }

                PlayerBody(
                    state = state,
                    onOpenLyrics = { openSheet = PlayerSheet.LYRICS },
                    onOpenComments = { openSheet = PlayerSheet.COMMENTS },
                    onRetry = viewModel::retry
                )

                Spacer(Modifier.height(24.dp))
            }

            // Snackbar-и (результат скарги) показуємо поверх вмісту екрана.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    when (openSheet) {
        PlayerSheet.LYRICS -> LyricsSheet(
            title = stringResource(R.string.player_lyrics),
            state = lyricsState,
            onDismiss = { openSheet = null },
            onRetry = lyricsViewModel::retry
        )

        PlayerSheet.COMMENTS -> CommentsSheet(
            trackId = resolvedTrackId.orEmpty(),
            viewModel = commentsViewModel,
            snackbarHostState = snackbarHostState,
            onDismiss = { openSheet = null },
            // Коментар додали чи видалили — оновлюємо лічильник у плеєрі.
            onCommentsChanged = viewModel::refreshCommentsCount,
            onReportComment = { comment ->
                // Скарга на коментар відкривається поверх усього екрана:
                // шторку закриваємо, щоб не було двох модальних вікон.
                openSheet = null
                resolvedTrackId?.let { reportViewModel.openCommentReport(it, comment) }
            }
        )

        null -> Unit
    }

    // Скарга показується поверх усього екрана, тож шторку закриваємо:
    // два модальні вікна одночасно збили б користувача з пантелику.
    ReportDialog(
        state = reportState,
        onReason = reportViewModel::selectReason,
        onDetailsChange = reportViewModel::onDetailsChange,
        onSubmit = reportViewModel::submit,
        onDismiss = reportViewModel::dismiss
    )
}

/**
 * Вміст екрана залежно від стану треку.
 *
 * Помилку показуємо з кнопкою повтору, а коли трек не знайшли взагалі —
 * окремим текстом: користувач має розуміти, що саме сталося (ТЗ 6.3.7).
 */
@Composable
private fun PlayerBody(
    state: PlayerUiState,
    onOpenLyrics: () -> Unit,
    onOpenComments: () -> Unit,
    onRetry: () -> Unit
) {
    val track = state.track
    val error = state.error

    when {
        state.isLoading -> PlayerMessage(text = uiText(R.string.player_loading), isLoading = true)

        error != null && track == null -> PlayerMessage(text = error, onRetry = onRetry)

        track == null -> PlayerMessage(
            text = uiText(R.string.player_error_no_tracks),
            onRetry = onRetry
        )

        else -> TrackDetails(
            track = track,
            positionSeconds = state.positionSeconds,
            commentsCount = state.commentsCount,
            onOpenLyrics = onOpenLyrics,
            onOpenComments = onOpenComments
        )
    }
}

/** Обкладинка, назва, прогрес і точки входу в текст пісні та коментарі. */
@Composable
private fun TrackDetails(
    track: Track,
    positionSeconds: Int,
    commentsCount: Int,
    onOpenLyrics: () -> Unit,
    onOpenComments: () -> Unit
) {
    Spacer(Modifier.height(8.dp))

    PlayerCoverArt(track)

    Spacer(Modifier.height(20.dp))

    Text(
        text = track.title,
        fontFamily = Montserrat,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    val artist = track.artistName?.takeIf { it.isNotBlank() }

    if (artist != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist,
            fontFamily = Montserrat,
            color = AuthPlaceholderColor,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(20.dp))

    PlayerProgress(positionSeconds = positionSeconds, durationSeconds = track.duration)

    // Трек є, але звуку немає: чесніше попередити, ніж показувати 0:00.
    if (!PlayerRules.hasAudio(track)) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.player_no_audio),
            fontFamily = Montserrat,
            color = AuthErrorColor,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(24.dp))

    LumiSecondaryButton(
        text = stringResource(R.string.player_lyrics),
        onClick = onOpenLyrics
    )

    Spacer(Modifier.height(10.dp))

    CommentsEntry(commentsCount = commentsCount, onOpenComments = onOpenComments)
}

/**
 * Кнопка «Коментарі» з лічильником.
 *
 * Лічильник озвучуємо разом із назвою кнопки, щоб TalkBack читав
 * «Коментарі, 7 коментарів» одним елементом, а не двома (ТЗ 6.3.7).
 */
@Composable
private fun CommentsEntry(commentsCount: Int, onOpenComments: () -> Unit) {
    val label = stringResource(R.string.player_comments)
    val countLabel = pluralStringResource(R.plurals.comments_count, commentsCount, commentsCount)

    Box(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label, $countLabel"
        }
    ) {
        LumiSecondaryButton(text = label, onClick = onOpenComments)
    }
}
