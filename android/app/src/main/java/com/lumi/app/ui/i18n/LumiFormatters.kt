package com.lumi.app.ui.i18n

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Локалізовані дати, числа та тривалості (ТЗ: локалізація UA/EN).
 *
 * Формат залежить від мови інтерфейсу, а не від системної локалі:
 * саме тому всюди передається явний [Locale] (за замовчуванням —
 * той, що встановив [LumiLocalization.rememberLocale]).
 */
object LumiFormatters {

    /** Порожнє значення для дат/сум, коли даних немає. */
    const val EMPTY_VALUE = "—"

    /** Символ гривні у цінах Lumi. */
    const val HRYVNIA_SIGN = "₴"

    /**
     * ISO-дата з бекенду ("2026-03-05T10:00:00Z") → локальний формат
     * ("5 бер. 2026" або "Mar 5, 2026").
     */
    fun formatDate(value: String?, locale: Locale = Locale.getDefault()): String? {
        if (value.isNullOrBlank()) {
            return null
        }

        return runCatching {
            val date = LocalDate.parse(value.trim().take(10))
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }.getOrNull()
    }

    /** Ціле число з розділювачами груп ("1 234" / "1,234"). */
    fun formatNumber(value: Int, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getIntegerInstance(locale).format(value)

    /** Сума в копійках → "1 234 ₴". [cents] == null → "—". */
    fun formatMoney(cents: Int?, locale: Locale = Locale.getDefault()): String {
        if (cents == null) {
            return EMPTY_VALUE
        }

        return "${formatNumber(cents / 100, locale)} $HRYVNIA_SIGN"
    }

    /** Тривалість треку: 3:45, 1:02:07. */
    fun formatTrackDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }
}
