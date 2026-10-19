package com.lumi.app.ui.i18n

import android.content.Context
import android.content.SharedPreferences
import com.lumi.app.LumiApplication
import com.lumi.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Локальні налаштування застосунку: мова інтерфейсу та тема (ТЗ 6.3.7).
 *
 * Читання виконується синхронно (SharedPreferences), бо мову треба знати
 * ще в Activity#attachBaseContext — до створення першого Activity.
 * Для реактивного UI віддаються [StateFlow]-и.
 */
object AppSettingsStore {

    const val PREFS_NAME = "lumi_app_settings"
    const val KEY_LANGUAGE = "app_language"
    const val KEY_THEME_MODE = "app_theme_mode"
    const val KEY_DATA_SAVER = "app_data_saver"
    const val KEY_NOTIFICATIONS = "app_notifications"
    const val KEY_AUDIO_QUALITY = "app_audio_quality"

    private var preferences: SharedPreferences? = null

    /** Application Context: потрібен для перекладу поза Composable (Snackbar). */
    private var appContext: Context? = null

    private var localizedContextCache: Context? = null
    private var localizedContextLanguage: AppLanguage? = null

    private val _language = MutableStateFlow(AppLanguage.DEFAULT)

    /** Обрана мова інтерфейсу (для CompositionLocal у LumiLocalization). */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.DEFAULT)

    /** Обрана тема інтерфейсу (для LumiTheme). */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dataSaver = MutableStateFlow(false)

    /** «Заощадження трафіку» з екрана налаштувань. */
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    private val _notifications = MutableStateFlow(true)

    /** Локальні сповіщення про новинки та підписку. */
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    private val _audioQuality = MutableStateFlow("STANDARD")

    /** Якість звуку: STANDARD / HQ / LOSSLESS. */
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    /**
     * Читає збережені налаштування в пам'ять.
     * Викликається з LumiApplication.onCreate().
     */
    fun init(context: Context = LumiApplication.instance) {
        val prefs = preferencesOf(context)
        preferences = prefs
        appContext = context.applicationContext
        invalidateLocalizedContext()

        val storedLanguage = AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null))
        val storedTheme = ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null))

        _language.value = storedLanguage
        _themeMode.value = storedTheme
        _dataSaver.value = prefs.getBoolean(KEY_DATA_SAVER, false)
        _notifications.value = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        _audioQuality.value = prefs.getString(KEY_AUDIO_QUALITY, "STANDARD") ?: "STANDARD"
        LumiLocalization.rememberLocale(storedLanguage)
    }

    /**
     * Context із ресурсами поточної мови — для перекладу поза Composable
     * (Snackbar у корутині, Toast). Повертає null лише якщо [init] не викликали.
     */
    fun localizedContext(language: AppLanguage = _language.value): Context? {
        val base = appContext ?: return null

        if (localizedContextCache == null || localizedContextLanguage != language) {
            localizedContextCache = LumiLocalization.withLanguage(base, language)
            localizedContextLanguage = language
        }

        return localizedContextCache
    }

    /** Очищає тимчасові файли застосунку (розділ «Налаштування»). */
    fun clearLocalCache(): Boolean =
        appContext?.cacheDir?.let { cache ->
            runCatching { cache.listFiles()?.forEach { it.deleteRecursively() } }.isSuccess
        } ?: false

    private fun invalidateLocalizedContext() {
        localizedContextCache = null
        localizedContextLanguage = null
    }

    /**
     * Мова з диска без ініціалізації (потрібна в attachBaseContext,
     * який виконується раніше за Activity#onCreate).
     */
    fun readLanguage(context: Context): AppLanguage =
        AppLanguage.fromTag(preferencesOf(context).getString(KEY_LANGUAGE, null))

    /** Перемикає мову інтерфейсу й одразу зберігає вибір. */
    fun setLanguage(language: AppLanguage) {
        _language.value = language
        LumiLocalization.rememberLocale(language)
        preferences?.edit()?.putString(KEY_LANGUAGE, language.tag)?.apply()
    }

    /** Перемикає тему інтерфейсу й одразу зберігає вибір. */
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        preferences?.edit()?.putString(KEY_THEME_MODE, mode.storageValue)?.apply()
    }

    /** Перемикає режим заощадження трафіку. */
    fun setDataSaver(enabled: Boolean) {
        _dataSaver.value = enabled
        preferences?.edit()?.putBoolean(KEY_DATA_SAVER, enabled)?.apply()
    }

    /** Перемикає локальні сповіщення. */
    fun setNotifications(enabled: Boolean) {
        _notifications.value = enabled
        preferences?.edit()?.putBoolean(KEY_NOTIFICATIONS, enabled)?.apply()
    }

    /** Зберігає обрану якість звуку. */
    fun setAudioQuality(quality: String) {
        _audioQuality.value = quality
        preferences?.edit()?.putString(KEY_AUDIO_QUALITY, quality)?.apply()
    }

    private fun preferencesOf(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
