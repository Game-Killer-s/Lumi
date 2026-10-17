package com.lumi.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lumi.app.data.model.StatsPeriod
import com.lumi.app.ui.screen.StatisticsScreen
import com.lumi.app.ui.theme.LumiTheme
import com.lumi.app.ui.viewmodel.StatisticsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Перевіряє back stack статистики: ViewModel живе в записі NavBackStackEntry,
 * тож після переходу на деталі (виконавець / жанр / плеєр) і повернення
 * збережений обраний період і дані.
 */
class StatisticsNavigationTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController
    private var createdViewModels = 0

    private val source = TestData.InstantSource(
        mapOf(
            StatsPeriod.WEEK to TestData.summary(StatsPeriod.WEEK, 10),
            StatsPeriod.MONTH to TestData.summary(StatsPeriod.MONTH, 40),
            StatsPeriod.ALL_TIME to TestData.summary(StatsPeriod.ALL_TIME, 400)
        )
    )

    @Before
    fun setUp() {
        rule.setContent {
            LumiTheme {
                nav = rememberNavController()
                NavHost(navController = nav, startDestination = "statistics") {
                    composable("statistics") {
                        val vm: StatisticsViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer {
                                    createdViewModels++
                                    StatisticsViewModel(source, scopeOverride = CoroutineScope(Dispatchers.Main.immediate))
                                }
                            }
                        )
                        StatisticsScreen(
                            onOpenTrack = { tracks, index -> nav.navigate("player/${tracks[index].id}") },
                            onOpenArtist = { id -> nav.navigate("artist/$id") },
                            onOpenGenre = { genre -> nav.navigate("genre/$genre") },
                            viewModel = vm
                        )
                    }
                    composable("player/{trackId}") { Text("PLAYER_SCREEN") }
                    composable("artist/{artistId}") { Text("ARTIST_SCREEN") }
                    composable("genre/{genreName}") { Text("GENRE_SCREEN") }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun route() = nav.currentBackStackEntry?.destination?.route

    @Test
    fun topArtist_opensArtistProfile_andBackRestoresStatistics() {
        rule.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_top_artist_art1"))
        rule.onNodeWithTag("stats_top_artist_art1").performClick()

        rule.onNodeWithText("ARTIST_SCREEN").assertIsDisplayed()
        assertEquals("artist/{artistId}", route())

        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()

        assertEquals("statistics", route())
        rule.onNodeWithTag("stats_list").assertIsDisplayed()
    }

    @Test
    fun selectedPeriod_isRestoredAfterReturningFromArtist() {
        rule.onNodeWithTag("stats_period_tab_month").performClick()
        rule.onNodeWithTag("stats_period_tab_month").assertIsSelected()

        rule.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_top_artist_art1"))
        rule.onNodeWithTag("stats_top_artist_art1").performClick()
        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()

        rule.onNodeWithTag("stats_period_tab_month").assertIsSelected()
    }

    @Test
    fun viewModelIsNotRecreatedByNavigatingToDetailAndBack() {
        rule.onNodeWithTag("stats_period_tab_all").performClick()

        rule.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_top_genre_Pop"))
        rule.onNodeWithTag("stats_top_genre_Pop").performClick()
        rule.onNodeWithText("GENRE_SCREEN").assertIsDisplayed()
        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()

        assertEquals(1, createdViewModels)
        rule.onNodeWithTag("stats_period_tab_all").assertIsSelected()
    }

    @Test
    fun topGenre_navigatesToGenreResults() {
        rule.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_top_genre_Rock"))
        rule.onNodeWithTag("stats_top_genre_Rock").performClick()

        rule.onNodeWithText("GENRE_SCREEN").assertIsDisplayed()
        assertEquals("genre/{genreName}", route())
    }

    @Test
    fun topTrack_navigatesToPlayer_andBackKeepsStatistics() {
        rule.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_top_track_1"))
        rule.onNodeWithTag("stats_top_track_1").performClick()

        rule.onNodeWithText("PLAYER_SCREEN").assertIsDisplayed()
        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()

        assertEquals("statistics", route())
        rule.onNodeWithTag("stats_period_tab_week").assertIsSelected()
    }
}
