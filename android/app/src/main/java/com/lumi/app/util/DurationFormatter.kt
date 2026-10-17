package com.lumi.app.util

/** Pure formatting helper, split out so it's unit-testable without Android. */
object DurationFormatter {
    /** Formats a duration given in whole seconds as "m:ss" (e.g. 65 -> "1:05"). */
    fun formatSeconds(totalSeconds: Int): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val minutes = safeSeconds / 60
        val seconds = safeSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /**
     * Форматує тривалість у мілісекундах: "m:ss", а від години — "h:mm:ss".
     * Від'ємні значення стають "0:00".
     */
    fun formatMillis(totalMillis: Long): String {
        val totalSeconds = (totalMillis.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, seconds)
        else "%d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
    }
}
