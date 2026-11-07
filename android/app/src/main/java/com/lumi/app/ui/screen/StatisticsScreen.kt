package com.lumi.app.ui.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumi.app.R
import com.lumi.app.data.model.ActivityPoint
import com.lumi.app.data.model.StatsPeriod
import com.lumi.app.data.model.StatsSummary
import com.lumi.app.data.model.TopArtist
import com.lumi.app.data.model.TopGenre
import com.lumi.app.data.model.TopTrack
import com.lumi.app.data.model.Track
import com.lumi.app.ui.i18n.LumiFormatters
import com.lumi.app.ui.i18n.currentAppLocale
import com.lumi.app.ui.viewmodel.StatisticsStatus
import com.lumi.app.ui.viewmodel.StatisticsUiState
import com.lumi.app.ui.viewmodel.StatisticsViewModel
import com.lumi.app.util.StatsFormatter
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Екран персональної статистики.
 *
 * ViewModel береться з поточного запису NavBackStackEntry, тому обраний
 * період і дані зберігаються, поки екран є в back stack (перехід на трек /
 * виконавця / жанр і назад). Позиція прокрутки відновлюється через
 * rememberLazyListState (rememberSaveable всередині).
 */
@Composable
fun StatisticsScreen(
    onOpenTrack: (tracks: List<Track>, index: Int) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: StatisticsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    StatisticsContent(
        state = state,
        onSelectPeriod = viewModel::selectPeriod,
        onRetry = viewModel::retry,
        onOpenTrack = onOpenTrack,
        onOpenArtist = onOpenArtist,
        onOpenGenre = onOpenGenre,
        onBack = onBack
    )
}

/** Stateless-частина: відображає [StatisticsUiState], тому легко тестується UI-тестами. */
@Composable
fun StatisticsContent(
    state: StatisticsUiState,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onRetry: () -> Unit,
    onOpenTrack: (tracks: List<Track>, index: Int) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("stats_list"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (onBack != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.stats_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.stats_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                )
            }
        }
        item { PeriodTabs(selected = state.selectedPeriod, onSelect = onSelectPeriod) }
        item {
            if (state.isRefreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("stats_refreshing"))
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }

        when (state.status) {
            StatisticsStatus.LOADING -> item { StatsLoading() }
            StatisticsStatus.ERROR -> item { StatsError(state.errorMessage, onRetry) }
            StatisticsStatus.EMPTY -> item { StatsEmpty() }
            StatisticsStatus.CONTENT -> {
                state.errorMessage?.let { message ->
                    item { StatsRefreshFailedBanner(message, onRetry) }
                }
                state.summary?.let { summary ->
                    summaryItems(summary, onOpenTrack, onOpenArtist, onOpenGenre)
                }
            }
        }
    }
}

@Composable
private fun PeriodTabs(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        StatsPeriod.entries.forEach { period ->
            Tab(
                selected = period == selected,
                onClick = { onSelect(period) },
                text = { Text(statsPeriodLabel(period)) },
                modifier = Modifier.testTag("stats_period_tab_${period.apiValue}")
            )
        }
    }
}

@Composable
private fun statsPeriodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.WEEK -> R.string.stats_period_week
        StatsPeriod.MONTH -> R.string.stats_period_month
        StatsPeriod.ALL_TIME -> R.string.stats_period_all
    }
)

// ---------------------------------------------------------------------
// Стани
// ---------------------------------------------------------------------

@Composable
private fun StatsLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp).testTag("stats_loading"),
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator() }
}

@Composable
private fun StatsError(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("stats_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message ?: stringResource(R.string.stats_error),
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry, modifier = Modifier.testTag("stats_retry")) {
            Text(stringResource(R.string.stats_retry))
        }
    }
}

@Composable
private fun StatsEmpty() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp).testTag("stats_empty"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.stats_empty),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/** Оновлення закешованих даних не вдалося: контент лишається, показуємо банер. */
@Composable
private fun StatsRefreshFailedBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("stats_refresh_error"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.stats_refresh_failed, message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.stats_refresh)) }
    }
}

// ---------------------------------------------------------------------
// Контент
// ---------------------------------------------------------------------

private fun LazyListScope.summaryItems(
    summary: StatsSummary,
    onOpenTrack: (List<Track>, Int) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit
) {
    item {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = stringResource(R.string.stats_tracks_played),
                    value = statsCount(summary.tracksPlayed.toLong()),
                    tag = "stat_tracks",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.stats_listening_time),
                    value = statsListeningTime(summary.listeningSeconds),
                    tag = "stat_time",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = stringResource(R.string.stats_playlists),
                    value = statsCount(summary.playlistsCount.toLong()),
                    tag = "stat_playlists",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.stats_top_genre),
                    value = summary.topGenre?.name ?: LumiFormatters.EMPTY_VALUE,
                    tag = "stat_top_genre",
                    onClick = summary.topGenre?.let { genre -> { onOpenGenre(genre.name) } },
                    modifier = Modifier.weight(1f)
                )
            }
            StatCard(
                title = stringResource(R.string.stats_top_artist),
                value = summary.topArtist?.artist?.name ?: LumiFormatters.EMPTY_VALUE,
                tag = "stat_top_artist",
                onClick = summary.topArtist?.let { top -> { onOpenArtist(top.artist.id) } },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (summary.activity.isNotEmpty()) {
        item {
            SectionTitle(stringResource(R.string.stats_activity))
            ActivityChart(
                points = summary.activity,
                period = summary.period,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    // ---- Топ треки ----
    item { SectionTitle(stringResource(R.string.stats_top_tracks)) }
    if (summary.topTracks.isEmpty()) {
        item {
            SectionEmpty(
                stringResource(R.string.stats_top_tracks_empty),
                "stats_top_tracks_empty"
            )
        }
    } else {
        val tracks = summary.topTracks.map { it.track }
        items(summary.topTracks.size) { index ->
            TopTrackRow(
                rank = index + 1,
                item = summary.topTracks[index],
                onClick = { onOpenTrack(tracks, index) }
            )
        }
    }

    // ---- Топ виконавці ----
    item { SectionTitle(stringResource(R.string.stats_top_artists)) }
    if (summary.topArtists.isEmpty()) {
        item {
            SectionEmpty(
                stringResource(R.string.stats_top_artists_empty),
                "stats_top_artists_empty"
            )
        }
    } else {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(summary.topArtists) { top ->
                    TopArtistItem(top, onClick = { onOpenArtist(top.artist.id) })
                }
            }
        }
    }

    // ---- Топ жанри ----
    item { SectionTitle(stringResource(R.string.stats_top_genres)) }
    if (summary.topGenres.isEmpty()) {
        item {
            SectionEmpty(
                stringResource(R.string.stats_top_genres_empty),
                "stats_top_genres_empty"
            )
        }
    } else {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(summary.topGenres) { genre ->
                    TopGenreChip(genre, onClick = { onOpenGenre(genre.name) })
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
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SectionEmpty(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag(tag)
    )
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = modifier.testTag(tag)
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("${tag}_value")
            )
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = cardModifier) { content() }
    } else {
        Card(modifier = cardModifier) { content() }
    }
}

// ---------------------------------------------------------------------
// Графік активності
// ---------------------------------------------------------------------

/**
 * Стовпчиковий графік прослуховувань: тиждень і місяць — по днях,
 * весь час — по місяцях. Підписи дат форматує [StatsFormatter.formatDateLabel],
 * а щоб вони не злипались, показується обмежена кількість підписів.
 */
@Composable
fun ActivityChart(
    points: List<ActivityPoint>,
    period: StatsPeriod,
    modifier: Modifier = Modifier
) {
    val maxPlays = points.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1
    val labelIndices = StatsFormatter
        .chartLabelIndices(points.size, if (period == StatsPeriod.WEEK) 7 else 5)
        .toSet()
    val barColor = MaterialTheme.colorScheme.primary
    val chartDescription = stringResource(
        R.string.stats_chart_description,
        points.size,
        statsPlays(maxPlays)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stats_chart")
            .semantics {
                contentDescription = chartDescription
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            points.forEach { point ->
                val fraction = (point.plays.toFloat() / maxPlays).coerceIn(0.02f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (point.plays > 0) barColor else barColor.copy(alpha = 0.2f))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (index in labelIndices) {
                        Text(
                            text = statsDateLabel(point.date, period),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
                                .testTag("stats_chart_label_$index")
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Топ-сутності
// ---------------------------------------------------------------------

@Composable
private fun TopTrackRow(rank: Int, item: TopTrack, onClick: () -> Unit) {
    val track = item.track
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("stats_top_track_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(28.dp)
        )
        AsyncImage(
            model = track.coverUrl ?: track.albumCoverUrl,
            contentDescription = track.title,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = track.artist?.name ?: track.artistName ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = statsPlays(item.plays),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun TopArtistItem(item: TopArtist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick)
            .testTag("stats_top_artist_${item.artist.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = item.artist.imageUrl,
            contentDescription = item.artist.name,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.artist.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = statsPlays(item.plays),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopGenreChip(item: TopGenre, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text("${item.name} · ${statsCount(item.plays.toLong())}") },
        modifier = Modifier.testTag("stats_top_genre_${item.name}")
    )
}

// ---------------------------------------------------------------------
// Локалізовані форматтери
// ---------------------------------------------------------------------

@Composable
private fun statsCount(value: Long): String {
    val locale = currentAppLocale()
    val v = value.coerceAtLeast(0L)
    if (v >= 1_000_000L) {
        val compact = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(v / 1_000_000.0)
        return stringResource(R.string.stats_count_millions, compact)
    }
    return LumiFormatters.formatNumber(v.toInt(), locale)
}

@Composable
private fun statsNumber(value: Long): String =
    LumiFormatters.formatNumber(value.coerceAtLeast(0L).toInt(), currentAppLocale())

@Composable
private fun statsListeningTime(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    if (seconds == 0L) return stringResource(R.string.stats_time_minutes, statsNumber(0))
    if (seconds < 60L) return stringResource(R.string.stats_less_than_minute)

    val totalMinutes = seconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    val minutesText = stringResource(R.string.stats_time_minutes, statsNumber(minutes))
    val hoursText = stringResource(R.string.stats_time_hours, statsNumber(hours))

    return when {
        hours == 0L -> minutesText
        minutes == 0L -> hoursText
        else -> stringResource(R.string.stats_time_hours_minutes, hoursText, minutesText)
    }
}

@Composable
private fun statsPlays(plays: Int): String {
    val n = plays.coerceAtLeast(0)
    return pluralStringResource(R.plurals.stats_plays, n, statsCount(n.toLong()))
}

@Composable
private fun statsDateLabel(date: LocalDate, period: StatsPeriod): String {
    val locale = currentAppLocale()
    val label = when (period) {
        StatsPeriod.WEEK -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        StatsPeriod.MONTH -> date.format(DateTimeFormatter.ofPattern("dd.MM", locale))
        StatsPeriod.ALL_TIME -> date.format(DateTimeFormatter.ofPattern("LLL yyyy", locale))
    }
    return label.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(locale) else char.toString()
    }
}
