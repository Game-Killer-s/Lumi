package com.lumi.app.ui.auth

/**
 * Валідації форм авторизації.
 * Тексти помилок відповідають вимогам спринту.
 */
object Validators {

    // Перевірка наявності символу @ та коректного домену
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun isValidEmail(email: String): Boolean =
        emailRegex.matches(email.trim())

    fun isPasswordLongEnough(password: String): Boolean =
        password.length >= 8

    // Пароль має містити хоча б одну літеру та одну цифру,
    // інакше він «занадто простий»
    fun isPasswordStrong(password: String): Boolean =
        password.any { it.isLetter() } && password.any { it.isDigit() }
}
