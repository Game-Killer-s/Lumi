package com.lumi.app.ui.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Локалізація під час роботи застосунку (ТЗ 6.3.1 / 6.3.7).
 *
 * Мова застосовується у двох місцях:
 *  1. `attachBaseContext` (Activity/Application) — щоб правильно
 *     відмалювати перший кадр після холодного старту;
 *  2. [ProvideLumiLocalization] — щоб перемикач мови в налаштуваннях
 *     діяв негайно, без перезапуску застосунку.
 */
object LumiLocalization {

    /** Обгортка базового Context із мовою, збереженою в налаштуваннях. */
    fun applyToBaseContext(base: Context): Context =
        withLanguage(base, AppSettingsStore.readLanguage(base))

    /** Повертає Context, у якому ресурси резолвляться у вказаній мові. */
    fun withLanguage(base: Context, language: AppLanguage): Context {
        val locale = language.locale()
        rememberLocale(language)

        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return base.createConfigurationContext(configuration)
    }

    /** Фіксує мову як типову для java.text / java.time форматерів. */
    fun rememberLocale(language: AppLanguage) {
        val locale = language.locale()
        if (Locale.getDefault() != locale) {
            Locale.setDefault(locale)
        }
    }

    /** Чи вже застосована потрібна мова до цього Context. */
    fun matches(context: Context, language: AppLanguage): Boolean =
        context.resources.configuration.locales.get(0)?.language == language.tag
}

/** Поточна мова інтерфейсу всередині Composition. */
val LocalAppLanguage = compositionLocalOf { AppLanguage.DEFAULT }

/**
 * Робить `stringResource()` / `LocalContext.current.getString()` реактивними
 * до перемикача мови: достатньо викликати AppSettingsStore.setLanguage(),
 * і весь UI перемальовується обраною мовою.
 *
 * `stringResource()` читає `LocalResources`, а той має обчислюване значення
 * (`LocalConfiguration` + `LocalContext`), тому перевизначення саме цих
 * CompositionLocal-ів одразу змінює всі рядки без перезапуску Activity.
 */
@Composable
fun ProvideLumiLocalization(content: @Composable () -> Unit) {
    val language by AppSettingsStore.language.collectAsState()
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current

    val localizedContext = remember(language, baseContext, baseConfiguration) {
        if (LumiLocalization.matches(baseContext, language)) {
            baseContext
        } else {
            LumiLocalization.withLanguage(baseContext, language)
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalAppLanguage provides language,
        content = content
    )
}

/** Локаль поточної мови інтерфейсу (для форматерів дат, чисел і сум). */
@Composable
@ReadOnlyComposable
fun currentAppLocale(): Locale = LocalAppLanguage.current.locale()

/** Розгортає ContextWrapper-и (потрібно, коли LocalContext локалізований). */
fun Context.findActivity(): Activity? {
    var current: Context? = this

    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }

    return null
}
