package com.lumi.app.ui.i18n

import java.util.Locale

/**
 * Мови інтерфейсу Lumi (ТЗ 6.3.1 / 6.3.7): українська та англійська.
 *
 * Українська — базова локаль застосунку (res/values/strings.xml),
 * англійська лежить у res/values-en/strings.xml. Мова перемикається
 * під час роботи застосунку, тому:
 *  - усі підписи беруться через stringResource / UiText;
 *  - дати й числа форматуються через LumiFormatters із явним Locale.
 */
enum class AppLanguage(val tag: String) {
    UKRAINIAN("uk"),
    ENGLISH("en");

    /** Locale для форматів дат, чисел і валют. */
    fun locale(): Locale = Locale.forLanguageTag(tag)

    companion object {
        /** Мова за замовчуванням — українська (ТЗ: базова локаль UA). */
        val DEFAULT: AppLanguage = UKRAINIAN

        /**
         * Розпізнає збережене значення: "uk", "uk_UA", "uk-UA", "en"…
         * Невідоме значення → [DEFAULT].
         */
        fun fromTag(tag: String?): AppLanguage {
            val normalized = tag?.trim()?.lowercase(Locale.ROOT) ?: return DEFAULT

            if (normalized.isEmpty()) {
                return DEFAULT
            }

            return values().firstOrNull { language ->
                normalized == language.tag ||
                    normalized.startsWith("${language.tag}_") ||
                    normalized.startsWith("${language.tag}-") ||
                    normalized.startsWith("${language.tag}+")
            } ?: DEFAULT
        }

        /** Впорядкований список для перемикача мови на екрані налаштувань. */
        val selectable: List<AppLanguage> = listOf(UKRAINIAN, ENGLISH)
    }
}
