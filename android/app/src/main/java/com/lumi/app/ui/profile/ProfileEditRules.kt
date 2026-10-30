package com.lumi.app.ui.profile

import androidx.annotation.StringRes
import com.lumi.app.R
import com.lumi.app.network.ApiError

/** Поля профілю, які можна редагувати (backend: UpdateProfileDto). */
enum class ProfileField { NICKNAME, EMAIL }

/**
 * Що саме змінив користувач на екрані редагування.
 *
 * null означає «поле не змінилось» — таке значення не летить на бекенд.
 * Так PATCH не перезаписує нічого зайвого і не натрапляє на конфлікти
 * через поля, які користувач не чіпав.
 */
data class ProfileEditChanges(
    val nickname: String?,
    val email: String?
) {
    val isEmpty: Boolean get() = nickname == null && email == null
}

/**
 * Чому збереження не вдалося.
 *
 * [messageRes] показується користувачу (мова підставляється під час показу),
 * [field] — поле, під яким малюємо помилку; null — повідомлення в snackbar.
 */
enum class ProfileSaveFailure(
    @StringRes val messageRes: Int,
    val field: ProfileField?
) {
    NICKNAME_TAKEN(R.string.profile_edit_name_taken, ProfileField.NICKNAME),
    EMAIL_TAKEN(R.string.profile_edit_email_taken, ProfileField.EMAIL),
    CONFLICT(R.string.profile_edit_conflict, null),
    SESSION_EXPIRED(R.string.profile_error_session_message, null),
    FAILED(R.string.profile_update_error, null)
}

/**
 * Правила екрана редагування профілю: що надсилати і як показати помилку.
 *
 * Винесено окремо від Compose, щоб покрити юніт-тестами.
 */
object ProfileEditRules {

    /** Обмеження бекенду (UpdateProfileDto.MaxLength). */
    const val NAME_MAX_LENGTH = 50

    /**
     * Рахує реальні зміни. Пробіли по краях не вважаємо зміною,
     * а email порівнюємо без урахування регістру: бекенд його нормалізує.
     */
    fun changes(
        currentNickname: String,
        currentEmail: String,
        typedName: String,
        typedEmail: String
    ): ProfileEditChanges {
        val nickname = typedName.trim()
        val email = typedEmail.trim()

        return ProfileEditChanges(
            nickname = nickname.takeIf { it != currentNickname.trim() },
            email = email.takeIf { !it.equals(currentEmail.trim(), ignoreCase = true) }
        )
    }

    /**
     * Розбір помилки PATCH /profile.
     *
     * 409 — конфлікт унікальності; бекенд пише в message, що саме зайняте
     * («Nickname is already taken» / «Email is already in use»). Якщо в тексті
     * згадані обидва поля (fallback на Prisma P2002) — показуємо загальне
     * повідомлення, щоб не підсвітити не те поле.
     */
    fun failure(error: ApiError): ProfileSaveFailure {
        val message = error.message

        return when (error.statusCode) {
            401, 403 -> ProfileSaveFailure.SESSION_EXPIRED
            409 -> {
                val mentionsEmail = message.containsAny("email", "пошт")
                val mentionsNickname = message.containsAny("nickname", "name", "нік", "ім")

                when {
                    mentionsEmail && mentionsNickname -> ProfileSaveFailure.CONFLICT
                    mentionsEmail -> ProfileSaveFailure.EMAIL_TAKEN
                    mentionsNickname -> ProfileSaveFailure.NICKNAME_TAKEN
                    else -> ProfileSaveFailure.CONFLICT
                }
            }
            else -> ProfileSaveFailure.FAILED
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { contains(it, ignoreCase = true) }
}
