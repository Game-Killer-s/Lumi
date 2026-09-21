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
}
