package com.lumi.app.network

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.lumi.app.LumiApplication
import com.lumi.app.data.model.AuthResponse
import com.lumi.app.data.model.Entitlement
import com.lumi.app.data.model.UserProfile
import kotlinx.coroutines.flow.first

/**
 * DataStore, де зберігається сесія користувача.
 * Саме завдяки цьому працює «Запам'ятати мене»:
 * токени лишаються навіть після перезапуску застосунку.
 */
private val Context.sessionDataStore by preferencesDataStore(name = "lumi_session")

/**
 * TokenManager зберігає access/refresh токени в пам'яті (для запитів)
 * і дублює їх у DataStore (для постійної сесії).
 */
object TokenManager {
    private val gson = Gson()

    private val KEY_ACCESS = stringPreferencesKey("access_token")
    private val KEY_REFRESH = stringPreferencesKey("refresh_token")
    private val KEY_USER = stringPreferencesKey("user_json")
    private val KEY_REMEMBER_ME = booleanPreferencesKey("remember_me")
    private val KEY_ENTITLEMENT = stringPreferencesKey("entitlement_json")

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var currentUser: UserProfile? = null
    private var currentEntitlement: Entitlement? = null

    private val appContext: Context
        get() = LumiApplication.instance

    /**
     * Завантажує збережену сесію з DataStore.
     * Викликається на Splash-екрані перед перевіркою логіну.
     */
    suspend fun init() {
        runCatching {
            val prefs = appContext.sessionDataStore.data.first()
            accessToken = prefs[KEY_ACCESS]
            refreshToken = prefs[KEY_REFRESH]

            val userJson = prefs[KEY_USER]
            currentUser = userJson?.let {
                runCatching { gson.fromJson(it, UserProfile::class.java) }.getOrNull()
            }

            // Можливості тарифу читаємо лише разом зі збереженою сесією.
            currentEntitlement = if (accessToken != null) {
                prefs[KEY_ENTITLEMENT]?.let {
                    runCatching { gson.fromJson(it, Entitlement::class.java) }.getOrNull()
                }
            } else {
                null
            }
        }
    }

    fun getToken(): String? = accessToken

    fun getRefreshToken(): String? = refreshToken

    fun getCurrentUser(): UserProfile? = currentUser

    fun isLoggedIn(): Boolean = accessToken != null

    fun getEntitlement(): Entitlement? = currentEntitlement

    /**
     * Зберігає можливості тарифу в сесії (у пам'яті та в DataStore),
     * щоб не питати сервер перед кожним треком.
     */
    suspend fun saveEntitlement(entitlement: Entitlement) {
        currentEntitlement = entitlement
        appContext.sessionDataStore.edit { prefs ->
            prefs[KEY_ENTITLEMENT] = gson.toJson(entitlement)
        }
    }

    /**
     * Зберігає сесію. Якщо [remember] == true — сесія
     * пишеться в DataStore і переживає перезапуск застосунку.
     */
    suspend fun saveSession(response: AuthResponse, remember: Boolean = true) {
        accessToken = response.accessToken
        refreshToken = response.refreshToken
        if (response.user != null) {
            currentUser = response.user
        }

        if (remember) {
            appContext.sessionDataStore.edit { prefs ->
                prefs[KEY_ACCESS] = response.accessToken
                prefs[KEY_REFRESH] = response.refreshToken
                currentUser?.let { prefs[KEY_USER] = gson.toJson(it) }
                prefs[KEY_REMEMBER_ME] = true
            }
        } else {
            // «Не запам'ятовувати»: стираємо стару сесію,
            // токени живуть лише в пам'яті.
            appContext.sessionDataStore.edit { it.clear() }
        }
    }

    suspend fun clearSession() {
        accessToken = null
        refreshToken = null
        currentUser = null
        currentEntitlement = null
        appContext.sessionDataStore.edit { it.clear() }
    }
}

