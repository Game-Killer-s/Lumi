package com.lumi.app.ui.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

/**
 * Текст, який ще не перекладено в конкретну мову.
 *
 * ViewModel-и не мають доступу до Context, тому всі їхні повідомлення
 * зберігаються як ресурс ([Res]); текст із сервера передається як [Raw].
 * Переклад відбувається в UI-шарі — саме тому мова змінюється без
 * перезапуску застосунку.
 */
sealed interface UiText {

    /** Посилання на рядок у strings.xml (+ позиційні аргументи формату). */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Готовий текст (наприклад, повідомлення з бекенду). */
    data class Raw(val text: String) : UiText

    /** Переклад поза Composable (Snackbar, Toast, логування). */
    fun resolve(context: Context): String = when (this) {
        is Res -> if (args.isEmpty()) {
            context.getString(id)
        } else {
            context.getString(id, *args.toTypedArray())
        }

        is Raw -> text
    }
}

/** Переклад усередині Composable. */
@Composable
@ReadOnlyComposable
fun UiText.localized(): String = when (this) {
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *args.toTypedArray())
    }

    is UiText.Raw -> text
}

/** Зручні фабрики для ViewModel-ів. */
fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun rawText(text: String): UiText = UiText.Raw(text)

/**
 * Переклад поза Composable (Snackbar/Toast у корутині).
 *
 * Мова береться з поточних налаштувань застосунку, тому навіть
 * захоплений у корутині [UiText] озвучується обраною мовою.
 * [fallbackContext] використовується лише якщо AppSettingsStore не ініціалізовано.
 */
fun UiText.resolveCurrent(fallbackContext: Context? = null): String {
    val context = AppSettingsStore.localizedContext() ?: fallbackContext ?: return ""

    return resolve(context)
}
