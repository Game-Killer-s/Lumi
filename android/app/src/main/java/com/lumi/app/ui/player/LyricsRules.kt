package com.lumi.app.ui.player

/**
 * Розбір тексту пісні (ТЗ 6.3.11).
 *
 * Текст приходить одним рядком, тому в UI його треба спершу розбити
 * на окремі рядки, а під час відтворення — підсвічувати поточний.
 */
object LyricsRules {

    /** Індекс «жодного рядка»: тексту немає або він ще не завантажений. */
    const val NO_LINE = -1

    /**
     * Рядки тексту без порожніх: прибираємо зайві переноси та відступи,
     * щоб у шторці не було «дірок» між куплетами.
     */
    fun lines(lyrics: String?): List<String> {
        return lyrics
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    fun hasLyrics(lyrics: String?): Boolean = lines(lyrics).isNotEmpty()

    /**
     * Рядок, який підсвічуємо «в такт музиці».
     *
     * Таймкодів бекенд не віддає, тому орієнтуємось на частку прослуханого
     * [progressFraction] (0f..1f): 0f → перший рядок, 1f → останній.
     * Порожній текст → [NO_LINE].
     */
    fun highlightedLine(progressFraction: Float, lineCount: Int): Int {
        if (lineCount <= 0) {
            return NO_LINE
        }

        val fraction = progressFraction.coerceIn(0f, 1f)

        return (fraction * lineCount).toInt().coerceIn(0, lineCount - 1)
    }
}
