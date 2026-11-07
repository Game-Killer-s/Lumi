package com.lumi.app.ui.profile

import com.lumi.app.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Редагування профілю: що летить на бекенд і як читається помилка PATCH.
 *
 * Головний кейс — незмінене поле не має відправлятись: саме через нього
 * збереження падало 409 «імʼя вже зайняте», хоча користувач міняв лише email.
 */
class ProfileEditRulesTest {

    // ---------- Що змінилось ----------

    @Test
    fun `unchanged fields are not sent`() {
        val changes = ProfileEditRules.changes(
            currentNickname = "Vasyl",
            currentEmail = "vasyl@example.com",
            typedName = " Vasyl ",
            typedEmail = "VASYL@example.com"
        )

        assertNull(changes.nickname)
        assertNull(changes.email)
        assertTrue(changes.isEmpty)
    }

    @Test
    fun `changed fields are trimmed and sent`() {
        val changes = ProfileEditRules.changes(
            currentNickname = "Vasyl",
            currentEmail = "vasyl@example.com",
            typedName = "  Vasylko  ",
            typedEmail = "  new@example.com "
        )

        assertEquals("Vasylko", changes.nickname)
        assertEquals("new@example.com", changes.email)
        assertFalse(changes.isEmpty)
    }

    @Test
    fun `only the changed email is sent`() {
        val changes = ProfileEditRules.changes(
            currentNickname = "Vasyl",
            currentEmail = "vasyl@example.com",
            typedName = "Vasyl",
            typedEmail = "other@example.com"
        )

        // Імʼя не чіпали — воно не повинно потрапити в PATCH.
        assertNull(changes.nickname)
        assertEquals("other@example.com", changes.email)
    }

    @Test
    fun `nickname case change is treated as a change`() {
        val changes = ProfileEditRules.changes(
            currentNickname = "vasyl",
            currentEmail = "vasyl@example.com",
            typedName = "Vasyl",
            typedEmail = "vasyl@example.com"
        )

        assertEquals("Vasyl", changes.nickname)
        assertNull(changes.email)
    }

    // ---------- Помилки збереження ----------

    @Test
    fun `taken nickname is shown under the name field`() {
        val failure = ProfileEditRules.failure(
            ApiError(statusCode = 409, message = "Nickname is already taken")
        )

        assertEquals(ProfileSaveFailure.NICKNAME_TAKEN, failure)
        assertEquals(ProfileField.NICKNAME, failure.field)
    }

    @Test
    fun `taken email is shown under the email field`() {
        val failure = ProfileEditRules.failure(
            ApiError(statusCode = 409, message = "Email is already in use")
        )

        assertEquals(ProfileSaveFailure.EMAIL_TAKEN, failure)
        assertEquals(ProfileField.EMAIL, failure.field)
    }

    @Test
    fun `ambiguous conflict is reported without a field`() {
        val failure = ProfileEditRules.failure(
            ApiError(statusCode = 409, message = "Email or nickname is already in use")
        )

        // Обидва слова в тексті — не підсвічуємо жодне поле.
        assertEquals(ProfileSaveFailure.CONFLICT, failure)
        assertNull(failure.field)
    }

    @Test
    fun `conflict without details is reported without a field`() {
        val failure = ProfileEditRules.failure(
            ApiError(statusCode = 409, message = "Conflict")
        )

        assertEquals(ProfileSaveFailure.CONFLICT, failure)
        assertNull(failure.field)
    }

    @Test
    fun `expired session is reported as a session problem`() {
        assertEquals(
            ProfileSaveFailure.SESSION_EXPIRED,
            ProfileEditRules.failure(ApiError.UNAUTHORIZED)
        )
        assertEquals(
            ProfileSaveFailure.SESSION_EXPIRED,
            ProfileEditRules.failure(ApiError(statusCode = 403, message = "Access denied."))
        )
    }

    @Test
    fun `network and server errors are generic failures`() {
        assertEquals(
            ProfileSaveFailure.FAILED,
            ProfileEditRules.failure(ApiError.NETWORK_ERROR)
        )
        assertEquals(
            ProfileSaveFailure.FAILED,
            ProfileEditRules.failure(ApiError(statusCode = 500, message = "Internal server error"))
        )
    }

    @Test
    fun `every failure has a message to show`() {
        // messageRes вказує на реальний рядок у strings.xml — інакше тут був би 0.
        assertTrue(ProfileSaveFailure.values().all { it.messageRes != 0 })
    }

    @Test
    fun `name length limit matches the backend`() {
        assertEquals(50, ProfileEditRules.NAME_MAX_LENGTH)
    }
}
