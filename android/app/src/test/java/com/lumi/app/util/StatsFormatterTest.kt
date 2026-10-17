package com.lumi.app.util

import com.lumi.app.data.model.StatsPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsFormatterTest {

    private val nbsp = "\u00A0"

    // ---------- кількості ----------

    @Test
    fun `count below one thousand is plain`() {
        assertEquals("0", StatsFormatter.formatCount(0))
        assertEquals("7", StatsFormatter.formatCount(7))
        assertEquals("999", StatsFormatter.formatCount(999))
    }

    @Test
    fun `count is grouped with non breaking spaces`() {
        assertEquals("1${nbsp}000", StatsFormatter.formatCount(1_000))
        assertEquals("12${nbsp}345", StatsFormatter.formatCount(12_345))
        assertEquals("999${nbsp}999", StatsFormatter.formatCount(999_999))
    }

    @Test
    fun `count from a million is compact`() {
        assertEquals("1${nbsp}млн", StatsFormatter.formatCount(1_000_000))
        assertEquals("1,2${nbsp}млн", StatsFormatter.formatCount(1_250_000))
        assertEquals("12,5${nbsp}млн", StatsFormatter.formatCount(12_580_000))
    }

    @Test
    fun `negative count becomes zero`() {
        assertEquals("0", StatsFormatter.formatCount(-3))
    }

    // ---------- час прослуховування ----------

    @Test
    fun `listening time under a minute`() {
        assertEquals("0${nbsp}хв", StatsFormatter.formatListeningTime(0))
        assertEquals("менше 1${nbsp}хв", StatsFormatter.formatListeningTime(59))
    }

    @Test
    fun `listening time in minutes rounds down`() {
        assertEquals("1${nbsp}хв", StatsFormatter.formatListeningTime(60))
        assertEquals("1${nbsp}хв", StatsFormatter.formatListeningTime(119))
        assertEquals("59${nbsp}хв", StatsFormatter.formatListeningTime(59 * 60 + 59))
    }

    @Test
    fun `listening time switches to hours`() {
        assertEquals("1${nbsp}год", StatsFormatter.formatListeningTime(3_600))
        assertEquals("1${nbsp}год 5${nbsp}хв", StatsFormatter.formatListeningTime(3_900))
        assertEquals("2${nbsp}год 30${nbsp}хв", StatsFormatter.formatListeningTime(9_000))
    }

    @Test
    fun `large listening time groups hours`() {
        assertEquals("1${nbsp}234${nbsp}год", StatsFormatter.formatListeningTime(1_234L * 3_600))
    }

    @Test
    fun `negative listening time is zero`() {
        assertEquals("0${nbsp}хв", StatsFormatter.formatListeningTime(-10))
    }

    // ---------- множина ----------

    @Test
    fun `plays plural forms`() {
        assertEquals("1${nbsp}прослуховування", StatsFormatter.formatPlays(1))
        assertEquals("2${nbsp}прослуховування", StatsFormatter.formatPlays(2))
        assertEquals("4${nbsp}прослуховування", StatsFormatter.formatPlays(4))
        assertEquals("5${nbsp}прослуховувань", StatsFormatter.formatPlays(5))
        assertEquals("11${nbsp}прослуховувань", StatsFormatter.formatPlays(11))
        assertEquals("12${nbsp}прослуховувань", StatsFormatter.formatPlays(12))
        assertEquals("21${nbsp}прослуховування", StatsFormatter.formatPlays(21))
        assertEquals("0${nbsp}прослуховувань", StatsFormatter.formatPlays(0))
    }

    // ---------- підписи дат ----------

    @Test
    fun `week labels are weekday names starting on monday`() {
        // 2026-09-28 — понеділок
        val monday = LocalDate.of(2026, 9, 28)
        val labels = (0..6).map { StatsFormatter.formatDateLabel(monday.plusDays(it.toLong()), StatsPeriod.WEEK) }
        assertEquals(listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд"), labels)
    }

    @Test
    fun `month labels are day dot month with zero padding`() {
        assertEquals("05.10", StatsFormatter.formatDateLabel(LocalDate.of(2026, 10, 5), StatsPeriod.MONTH))
        assertEquals("31.12", StatsFormatter.formatDateLabel(LocalDate.of(2026, 12, 31), StatsPeriod.MONTH))
        assertEquals("01.01", StatsFormatter.formatDateLabel(LocalDate.of(2027, 1, 1), StatsPeriod.MONTH))
    }

    @Test
    fun `all time labels are month name and year`() {
        assertEquals("січ 2026", StatsFormatter.formatDateLabel(LocalDate.of(2026, 1, 1), StatsPeriod.ALL_TIME))
        assertEquals("вер 2025", StatsFormatter.formatDateLabel(LocalDate.of(2025, 9, 15), StatsPeriod.ALL_TIME))
        assertEquals("гру 2026", StatsFormatter.formatDateLabel(LocalDate.of(2026, 12, 1), StatsPeriod.ALL_TIME))
    }

    @Test
    fun `every month of the year has a label`() {
        (1..12).forEach { m ->
            val label = StatsFormatter.formatDateLabel(LocalDate.of(2026, m, 1), StatsPeriod.ALL_TIME)
            assertTrue(label.endsWith("2026") && label.length > 5)
        }
    }

    // ---------- вибір підписів ----------

    @Test
    fun `all labels shown when they fit`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), StatsFormatter.chartLabelIndices(7, 7))
    }

    @Test
    fun `labels are spread evenly including first and last`() {
        assertEquals(listOf(0, 7, 15, 22, 29), StatsFormatter.chartLabelIndices(30, 5))
    }

    @Test
    fun `degenerate label requests`() {
        assertEquals(emptyList<Int>(), StatsFormatter.chartLabelIndices(0, 5))
        assertEquals(emptyList<Int>(), StatsFormatter.chartLabelIndices(10, 0))
        assertEquals(listOf(0), StatsFormatter.chartLabelIndices(10, 1))
    }
}
