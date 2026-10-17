package com.lumi.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {

    @Test
    fun `formats whole minutes`() {
        assertEquals("2:00", DurationFormatter.formatSeconds(120))
    }

    @Test
    fun `pads seconds under ten`() {
        assertEquals("1:05", DurationFormatter.formatSeconds(65))
    }

    @Test
    fun `formats zero`() {
        assertEquals("0:00", DurationFormatter.formatSeconds(0))
    }

    @Test
    fun `clamps negative durations to zero`() {
        assertEquals("0:00", DurationFormatter.formatSeconds(-5))
    }

    @Test
    fun `formats durations over an hour as minutes`() {
        assertEquals("61:01", DurationFormatter.formatSeconds(3661))
    }

    @Test
    fun `formatMillis shows minutes and seconds`() {
        assertEquals("0:00", DurationFormatter.formatMillis(0))
        assertEquals("0:59", DurationFormatter.formatMillis(59_999))
        assertEquals("1:05", DurationFormatter.formatMillis(65_000))
        assertEquals("3:30", DurationFormatter.formatMillis(210_400))
    }

    @Test
    fun `formatMillis switches to hours`() {
        assertEquals("1:00:00", DurationFormatter.formatMillis(3_600_000))
        assertEquals("1:01:01", DurationFormatter.formatMillis(3_661_000))
    }

    @Test
    fun `formatMillis clamps negatives`() {
        assertEquals("0:00", DurationFormatter.formatMillis(-1_000))
    }
}
