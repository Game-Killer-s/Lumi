package com.lumi.app.util

import com.lumi.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackFiltersTest {
    private fun t(id: String, genre: String?) = Track(id = id, title = id, artist = null, genre = genre)

    @Test
    fun `keeps only tracks of the genre ignoring case and spaces`() {
        val tracks = listOf(t("1", "Rock"), t("2", "rock "), t("3", "Pop"), t("4", null))
        assertEquals(listOf("1", "2"), filterByGenre(tracks, " ROCK").map { it.id })
    }

    @Test
    fun `no matches gives empty list`() {
        assertEquals(emptyList<Track>(), filterByGenre(listOf(t("1", "Pop")), "Jazz"))
    }
}
