package com.lumi.app.ui.theme

import java.util.Locale

/**
 * Тема інтерфейсу (ТЗ 6.3.1 / 6.3.7): «Системна», «Світла», «Темна».
 *
 * Значення зберігається в AppSettingsStore і застосовується у LumiTheme
 * без перезапуску застосунку.
 */
enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        /** Типове значення — як у системі. */
        val DEFAULT: ThemeMode = SYSTEM

        /** Впорядкований список для перемикача теми на екрані налаштувань. */
        val selectable: List<ThemeMode> = listOf(SYSTEM, LIGHT, DARK)

        /** Читає збережене значення; невідоме → [DEFAULT]. */
        fun fromStorage(value: String?): ThemeMode {
            val normalized = value?.trim()?.lowercase(Locale.ROOT)
            return values().firstOrNull { it.storageValue == normalized } ?: DEFAULT
        }

        /**
         * Чи малювати темну тему. Для [SYSTEM] — стан системи,
         * для решти — явний вибір користувача (він перекриває систему).
         */
        fun isDark(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean = when (mode) {
            SYSTEM -> systemInDarkTheme
            LIGHT -> false
            DARK -> true
        }
    }
}
