package com.lumi.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.lumi.app.data.model.StatsPeriod
import com.lumi.app.data.model.Track
import com.lumi.app.ui.screen.StatisticsContent
import com.lumi.app.ui.theme.LumiTheme
import com.lumi.app.ui.viewmodel.StatisticsStatus
import com.lumi.app.ui.viewmodel.StatisticsUiState
import com.lumi.app.util.StatsFormatter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI-тести екрана статистики: стани, форматування, вкладки періодів і клік по топ-сутностях. */
class StatisticsContentTest {

    @get:Rule
    val rule = createComposeRule()

    private var selectedPeriod: StatsPeriod? = null
    private var retried = false
    private var openedTrack: Pair<List<Track>, Int>? = null
    private var openedArtist: String? = null
    private var openedGenre: String? = null

    private fun show(state: StatisticsUiState) {
        rule.setContent {
            LumiTheme {
                StatisticsContent(
                    state = state,
                    onSelectPeriod = { selectedPeriod = it },
                    onRetry = { retried = true },
                    onOpenTrack = { tracks, index -> openedTrack = tracks to index },
                    onOpenArtist = { openedArtist = it },
                    onOpenGenre = { openedGenre = it }
                )
            }
        }
    }

    private fun content(summary: com.lumi.app.data.model.StatsSummary = TestData.summary()) =
        StatisticsUiState(selectedPeriod = summary.period, status = StatisticsStatus.CONTENT, summary = summary)

    // ---------- стани ----------

    @Test
    fun loading_showsLocalLoaderAndKeepsTabsVisible() {
        show(StatisticsUiState(selectedPeriod = StatsPeriod.MONTH, status = StatisticsStatus.LOADING))

        rule.onNodeWithTag("stats_loading").assertIsDisplayed()
        StatsPeriod.entries.forEach { rule.onNodeWithTag("stats_period_tab_${it.apiValue}").assertIsDisplayed() }
        rule.onNodeWithTag("stats_period_tab_month").assertIsSelected()
    }

    @Test
    fun error_showsMessageAndRetryInvokesCallback() {
        show(StatisticsUiState(status = StatisticsStatus.ERROR, errorMessage = "Немає мережі"))

        rule.onNodeWithTag("stats_error").assertIsDisplayed()
        rule.onNodeWithTag("stats_retry").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun empty_showsEmptyState() {
        show(StatisticsUiState(status = StatisticsStatus.EMPTY, summary = TestData.summary(tracksPlayed = 0)))
        rule.onNodeWithTag("stats_empty").assertIsDisplayed()
    }

    @Test
    fun refreshing_showsThinProgressOverCachedContent() {
        show(content().copy(isRefreshing = true))

        rule.onNodeWithTag("stats_refreshing").assertIsDisplayed()
        rule.onNodeWithTag("stat_tracks").assertIsDisplayed()
    }

    @Test
    fun failedRefresh_keepsContentAndShowsBanner() {
        show(content().copy(errorMessage = "Таймаут"))

        rule.onNodeWithTag("stats_refresh_error").assertIsDisplayed()
        rule.onNodeWithTag("stat_tracks").assertIsDisplayed()
    }

    // ---------- значення ----------

    @Test
    fun content_showsFormattedSummaryValues() {
        show(content())

        rule.onNodeWithTag("stat_tracks_value").assertTextEquals(StatsFormatter.formatCount(1234))
        rule.onNodeWithTag("stat_time_value").assertTextEquals(StatsFormatter.formatListeningTime(9_000))
        rule.onNodeWithTag("stat_playlists_value").assertTextEquals("3")
        rule.onNodeWithTag("stat_top_genre_value").assertTextEquals("Rock")
        rule.onNodeWithTag("stat_top_artist_value").assertTextEquals("Top Band")
    }

    // ---------- вкладки періодів ----------

    @Test
    fun tappingPeriodTabsReportsEachPeriod() {
        show(content())

        rule.onNodeWithTag("stats_period_tab_month").performClick()
        assertEquals(StatsPeriod.MONTH, selectedPeriod)
        rule.onNodeWithTag("stats_period_tab_all").performClick()
        assertEquals(StatsPeriod.ALL_TIME, selectedPeriod)
        rule.onNodeWithTag("stats_period_tab_week").performClick()
        assertEquals(StatsPeriod.WEEK, selectedPeriod)
    }

    @Test
    fun selectedTabFollowsUiState() {
        show(content(TestData.summary(StatsPeriod.ALL_TIME)))
        rule.onNodeWithTag("stats_period_tab_all").assertIsSelected()
    }

    // ---------- навігація по топ-сутностях ----------

    @Test
    fun clickingTopTrackReportsListAndIndex() {
        show(content())

        rule.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_top_track_2"))
        rule.onNodeWithTag("stats_top_track_2").performClick()

        assertEquals(1, openedTrack?.second)
        assertEquals(listOf("1", "2"), openedTrack?.first?.map { it.id })
    }

    @Test
    fun clickingTopArtistReportsArtistId() {
        show(content())

        rule.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_top_artist_art1"))
        rule.onNodeWithTag("stats_top_artist_art1").performClick()

        assertEquals("art1", openedArtist)
    }

    @Test
    fun clickingTopGenreChipReportsGenreName() {
        show(content())

        rule.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_top_genre_Pop"))
        rule.onNodeWithTag("stats_top_genre_Pop").performClick()

        assertEquals("Pop", openedGenre)
    }

    @Test
    fun summaryCardsForTopGenreAndArtistAreClickable() {
        show(content())

        rule.onNodeWithTag("stat_top_genre").performClick()
        assertEquals("Rock", openedGenre)
        rule.onNodeWithTag("stat_top_artist").performClick()
        assertEquals("art1", openedArtist)
    }

    // ---------- порожні секції ----------

    @Test
    fun eachTopSectionShowsItsOwnEmptyState() {
        val summary = TestData.summary().copy(topTracks = emptyList(), topArtists = emptyList(), topGenres = emptyList())
        show(content(summary))

        rule.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_top_genres_empty"))
        rule.onNodeWithTag("stats_top_tracks_empty").assertIsDisplayed()
        rule.onNodeWithTag("stats_top_artists_empty").assertIsDisplayed()
        rule.onNodeWithTag("stats_top_genres_empty").assertIsDisplayed()
    }
}
