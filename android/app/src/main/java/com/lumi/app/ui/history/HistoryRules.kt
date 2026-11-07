package com.lumi.app.ui.history

import com.lumi.app.data.local.HistoryEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class HistoryDayBucket { TODAY, YESTERDAY, EARLIER }

/**
 * Чисті правила списку історії: що показувати, як групувати та як
 * підписувати час. Виокремлено для JUnit-тестів без Android.
 */
object HistoryRules {

    /** «За останній місяць» — поточний день і 29 попередніх. */
    const val WINDOW_DAYS = 30L

    fun isWithinLastMonth(
        playedAt: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val date = Instant.ofEpochMilli(playedAt).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return !date.isBefore(today.minusDays(WINDOW_DAYS - 1)) && !date.isAfter(today)
    }

    fun bucket(
        playedAt: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): HistoryDayBucket {
        val date = Instant.ofEpochMilli(playedAt).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (date) {
            today -> HistoryDayBucket.TODAY
            today.minusDays(1) -> HistoryDayBucket.YESTERDAY
            else -> HistoryDayBucket.EARLIER
        }
    }

    fun visible(
        entries: List<HistoryEntry>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<HistoryEntry> = entries
        .filter { isWithinLastMonth(it.playedAt, now, zone) }
        .sortedByDescending { it.playedAt }

    fun timeLabel(
        playedAt: Long,
        locale: Locale,
        zone: ZoneId = ZoneId.systemDefault()
    ): String = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .format(Instant.ofEpochMilli(playedAt).atZone(zone))

    fun dateLabel(
        playedAt: Long,
        locale: Locale,
        zone: ZoneId = ZoneId.systemDefault()
    ): String = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(Instant.ofEpochMilli(playedAt).atZone(zone))
}
