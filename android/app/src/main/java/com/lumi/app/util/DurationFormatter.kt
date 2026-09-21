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
}
