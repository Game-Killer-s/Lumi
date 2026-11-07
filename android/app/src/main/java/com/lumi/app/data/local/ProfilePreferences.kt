package com.lumi.app.data.local

import android.content.Context
import com.lumi.app.LumiApplication

/**
 * Локальні дані профілю, яких поки немає в контракті backend.
 *
 * Номер телефону є в макеті екрана редагування, але ProfileDto
 * бекенду містить лише nickname/email/avatarUrl. Щоб поле не було
 * декоративним, зберігаємо його на пристрої та показуємо при
 * наступному відкритті екрана.
 */
object ProfilePreferences {

    private const val PREFS_NAME = "lumi_profile"
    private const val KEY_PHONE_PREFIX = "profile_phone_"

    private fun preferences(): android.content.SharedPreferences =
        LumiApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Телефон зберігається окремо для кожного користувача, щоб після
     * зміни акаунта на пристрої не показувався чужий номер.
     */
    fun getPhone(userId: String): String =
        preferences().getString(KEY_PHONE_PREFIX + userId, "").orEmpty()

    fun setPhone(userId: String, value: String) {
        preferences().edit().putString(KEY_PHONE_PREFIX + userId, value.trim()).apply()
    }
}
