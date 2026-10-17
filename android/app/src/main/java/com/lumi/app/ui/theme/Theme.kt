package com.lumi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lumi.app.ui.i18n.AppSettingsStore
import com.lumi.app.ui.i18n.findActivity

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface
)

/**
 * Чи активна темна тема. Доступно нащадкам (і тестам) без повторного
 * обчислення вибору користувача.
 */
val LocalLumiDarkTheme = compositionLocalOf { false }

/**
 * Тема Lumi (ТЗ 6.3.1 / 6.3.7).
 *
 * Режим береться з налаштувань застосунку («Системна» / «Світла» / «Темна»),
 * тому перемикач у налаштуваннях діє негайно — без перезапуску Activity.
 * Аргумент [darkTheme] явно перекриває режим (preview та UI-тести).
 */
@Composable
fun LumiTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val themeMode by AppSettingsStore.themeMode.collectAsState()
    val isDark = darkTheme ?: ThemeMode.isDark(themeMode, isSystemInDarkTheme())
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalLumiDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
