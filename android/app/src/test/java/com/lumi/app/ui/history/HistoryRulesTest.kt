package com.lumi.app.ui.history

import com.lumi.app.data.local.HistoryEntry
import com.lumi.app.data.model.Track
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRulesTest {

    private val zone = ZoneId.of("UTC")
    private val now = millisAt("2026-10-28", 12, 0)

    private fun millisAt(date: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(date)
            .atTime(LocalTime.of(hour, minute))
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

    private fun entry(id: String, date: String, hour: Int) = HistoryEntry(
        track = Track(id = id, title = "Track $id", artist = null),
        playedAt = millisAt(date, hour)
    )

    @Test
    fun `current window keeps today and the 29 previous days`() {
        assertTrue(HistoryRules.isWithinLastMonth(millisAt("2026-10-28", 0), now, zone))
        assertTrue(HistoryRules.isWithinLastMonth(millisAt("2026-09-29", 0), now, zone))
        assertFalse(HistoryRules.isWithinLastMonth(millisAt("2026-09-28", 23), now, zone))
        assertFalse(HistoryRules.isWithinLastMonth(millisAt("2026-10-29", 0), now, zone))
    }

    @Test
    fun `entries are bucketed by calendar day in the given zone`() {
        assertEquals(
            HistoryDayBucket.TODAY,
            HistoryRules.bucket(millisAt("2026-10-28", 0), now, zone)
        )
        assertEquals(
            HistoryDayBucket.YESTERDAY,
            HistoryRules.bucket(millisAt("2026-10-27", 23), now, zone)
        )
        assertEquals(
            HistoryDayBucket.EARLIER,
            HistoryRules.bucket(millisAt("2026-10-26", 23), now, zone)
        )
    }

    @Test
    fun `visible drops old entries and sorts newest first`() {
        val entries = listOf(
            entry("older", "2026-10-20", 8),
            entry("expired", "2026-09-01", 8),
            entry("newest", "2026-10-28", 9)
        )

        val visible = HistoryRules.visible(entries, now, zone)

        assertEquals(listOf("newest", "older"), visible.map { it.track.id })
    }

    @Test
    fun `labels are non-empty for a fixed instant`() {
        val timestamp = millisAt("2026-10-18", 9, 30)

        assertTrue(HistoryRules.timeLabel(timestamp, java.util.Locale.US, zone).isNotBlank())
        assertTrue(HistoryRules.dateLabel(timestamp, java.util.Locale.US, zone).isNotBlank())
    }
}
