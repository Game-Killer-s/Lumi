package com.lumi.app.ui.player

import com.lumi.app.data.model.Track
import com.lumi.app.ui.i18n.LumiFormatters

/**
 * Правила екрана плеєра, які не залежать від Android.
 *
 * Виносимо їх окремо, щоб покрити юніт-тестами: тут лишається вся арифметика
 * (який трек відкрити, скільки триває, яка частка прогресу), а Compose
 * займається лише показом.
 */
object PlayerRules {

    private const val MILLIS_PER_SECOND = 1000L

    /**
     * Id треку, який відкриваємо: явний аргумент маршруту має приоритет,
     * інакше беремо перший популярний (поки плеєр не підключений до сервісу
     * відтворення, це єдиний спосіб відкрити екран). Обидва порожні → null.
     */
    fun resolveTrackId(requestedTrackId: String?, fallbackTrackId: String?): String? {
        return requestedTrackId?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackTrackId?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Чи є що програвати: без audioUrl плеєр показує лише метадані й текст. */
    fun hasAudio(track: Track?): Boolean = !track?.audioUrl.isNullOrBlank()

    /**
     * Тривалість треку з БД (секунди) → «3:34».
     * У моделі duration — секунди (у seed: 214), а форматер працює з мс.
     */
    fun durationLabel(durationSeconds: Int): String {
        return LumiFormatters.formatTrackDuration(durationSeconds.toLong() * MILLIS_PER_SECOND)
    }

    /**
     * Частка прослуханого (0f..1f) — для прогрес-бару та підсвічування рядків.
     * Невідома тривалість → 0f, щоб індикатор не стрибав.
     */
    fun progressFraction(positionSeconds: Int, durationSeconds: Int): Float {
        if (durationSeconds <= 0) {
            return 0f
        }

        return (positionSeconds.toFloat() / durationSeconds).coerceIn(0f, 1f)
    }
}
