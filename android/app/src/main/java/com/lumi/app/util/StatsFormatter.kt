package com.lumi.app.util

import com.lumi.app.data.model.StatsPeriod
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Чисте форматування значень статистики (без Android), щоб його можна
 * було покрити звичайними JUnit-тестами. Локаль-незалежне: назви днів і
 * місяців зашиті, тому результат однаковий на будь-якому пристрої.
 */
object StatsFormatter {

    private const val NBSP = '\u00A0'

    private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")
    private val MONTHS = listOf(
        "січ", "лют", "бер", "кві", "тра", "чер",
        "лип", "сер", "вер", "жов", "лис", "гру"
    )

    fun formatCount(value: Int): String = formatCount(value.toLong())

    /**
     * 0..999 → "742"; 1 000..999 999 → "12 345" (нерозривний пробіл);
     * від мільйона → "1,2 млн" (один знак після коми, "1 млн" без ",0").
     */
    fun formatCount(value: Long): String {
        val v = value.coerceAtLeast(0L)
        if (v >= 1_000_000L) {
            val tenths = v / 100_000L
            val whole = tenths / 10
            val fraction = tenths % 10
            return if (fraction == 0L) "$whole${NBSP}млн" else "$whole,$fraction${NBSP}млн"
        }
        return groupThousands(v)
    }

    private fun groupThousands(v: Long): String {
        val digits = v.toString()
        val sb = StringBuilder()
        digits.forEachIndexed { i, ch ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(NBSP)
            sb.append(ch)
        }
        return sb.toString()
    }

    /**
     * Час прослуховування: "0 хв", "менше 1 хв", "45 хв", "2 год", "1 год 5 хв".
     * Хвилини округлюються вниз.
     */
    fun formatListeningTime(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0L)
        if (seconds == 0L) return "0${NBSP}хв"
        if (seconds < 60L) return "менше 1${NBSP}хв"
        val totalMinutes = seconds / 60L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours == 0L -> "$minutes${NBSP}хв"
            minutes == 0L -> "${groupThousands(hours)}${NBSP}год"
            else -> "${groupThousands(hours)}${NBSP}год $minutes${NBSP}хв"
        }
    }

    /** "1 прослуховування", "5 прослуховувань" (українська множина). */
    fun formatPlays(plays: Int): String {
        val n = plays.coerceAtLeast(0)
        val mod10 = n % 10
        val mod100 = n % 100
        val word = when {
            mod10 == 1 && mod100 != 11 -> "прослуховування"
            mod10 in 2..4 && mod100 !in 12..14 -> "прослуховування"
            else -> "прослуховувань"
        }
        return "${formatCount(n)}$NBSP$word"
    }

    /**
     * Підпис дати на осі графіка:
     *  - тиждень: "Пн", "Вт", …
     *  - місяць: "05.10"
     *  - весь час: "жов 2026"
     */
    fun formatDateLabel(date: LocalDate, period: StatsPeriod): String = when (period) {
        StatsPeriod.WEEK -> WEEKDAYS[date.dayOfWeek.value - 1]
        StatsPeriod.MONTH -> "%02d.%02d".format(Locale.ROOT, date.dayOfMonth, date.monthValue)
        StatsPeriod.ALL_TIME -> "${MONTHS[date.monthValue - 1]} ${date.year}"
    }

    /**
     * Індекси стовпців, під якими показується підпис: рівномірно, завжди
     * з першим і останнім, не більше [maxLabels].
     */
    fun chartLabelIndices(count: Int, maxLabels: Int): List<Int> {
        if (count <= 0 || maxLabels <= 0) return emptyList()
        if (count <= maxLabels) return (0 until count).toList()
        if (maxLabels == 1) return listOf(0)
        return (0 until maxLabels)
            .map { (it * (count - 1).toDouble() / (maxLabels - 1)).roundToInt() }
            .distinct()
    }
}
