package com.lumi.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.R
import com.lumi.app.data.model.Entitlement
import com.lumi.app.data.repository.AuthRepository
import com.lumi.app.network.ApiError
import com.lumi.app.network.ApiResult
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.rawText
import com.lumi.app.ui.i18n.uiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Стан екранів авторизації.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nickname: String = "",
    val rememberMe: Boolean = true,
    val agreeTerms: Boolean = true,
    val showPassword: Boolean = false,
    val showConfirmPassword: Boolean = false,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    val confirmPasswordError: UiText? = null,
    val nicknameError: UiText? = null,
    val emailHasError: Boolean = false,
    val passwordHasError: Boolean = false,
    val confirmPasswordHasError: Boolean = false,
    val nicknameHasError: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * Події для екрана (одноразові): повідомлення, навігація.
 */
sealed class AuthEvent {
    /**
     * Текст ще не перекладений: UI вирішує мову в момент показу,
     * тому перемикання UA/EN діє без перезапуску застосунку.
     */
    data class Message(val text: UiText) : AuthEvent()
    data object NavigateHome : AuthEvent()
    data object ForgotPasswordSent : AuthEvent()
}

/**
 * ViewModel авторизації: логін, реєстрація, Google Sign-In,
 * відновлення пароля. Містить усі валідації з ТЗ.
 */
class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    var uiState by mutableStateOf(AuthUiState())
        private set

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Можливості тарифу поточної сесії: оновлюються після входу.
     */
    val entitlement: Entitlement
        get() = repository.currentEntitlement()

    fun onEmailChange(value: String) {
        uiState = uiState.copy(
            email = value,
            emailError = null,
            emailHasError = false
        )
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(
            password = value,
            passwordError = null,
            passwordHasError = false
        )
    }

    fun onConfirmPasswordChange(value: String) {
        uiState = uiState.copy(
            confirmPassword = value,
            confirmPasswordError = null,
            confirmPasswordHasError = false
        )
    }

    fun onNicknameChange(value: String) {
        uiState = uiState.copy(
            nickname = value,
            nicknameError = null,
            nicknameHasError = false
        )
    }

    fun onRememberMeChange(value: Boolean) {
        uiState = uiState.copy(rememberMe = value)
    }

    fun onAgreeTermsChange(value: Boolean) {
        uiState = uiState.copy(agreeTerms = value)
    }

    fun togglePasswordVisibility() {
        uiState = uiState.copy(showPassword = !uiState.showPassword)
    }

    fun toggleConfirmPasswordVisibility() {
        uiState = uiState.copy(showConfirmPassword = !uiState.showConfirmPassword)
    }

    // ==================== ЛОГІН ====================

    /** Повідомлення валідації порожнього поля (спільне для всіх форм). */
    private val requiredFieldError: UiText
        get() = uiText(R.string.error_required_field)

    fun login() {
        val state = uiState

        // Перевірка на пусті поля — підсвічуємо кожне порожнє поле
        val emailEmpty = state.email.isBlank()
        val passwordEmpty = state.password.isBlank()

        if (emailEmpty || passwordEmpty) {
            val message = requiredFieldError
            _events.trySend(AuthEvent.Message(message))
            uiState = uiState.copy(
                emailError = if (emailEmpty) message else null,
                passwordError = if (passwordEmpty) message else null,
                emailHasError = emailEmpty,
                passwordHasError = passwordEmpty
            )
            return
        }

        // Валідація email (@ та коректний домен)
        if (!Validators.isValidEmail(state.email)) {
            uiState = uiState.copy(
                emailError = uiText(R.string.error_invalid_email),
                emailHasError = true
            )
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            when (val result = repository.login(state.email.trim(), state.password)) {
                is ApiResult.Success -> {
                    repository.saveSession(result.data, rememberMe = state.rememberMe)
                    _events.trySend(AuthEvent.NavigateHome)
                }
                is ApiResult.Error -> handleLoginError(result.error)
            }
            uiState = uiState.copy(isLoading = false)
        }
    }

    /**
     * Сценарії «Неправильний пароль» і «Користувача не існує»:
     * підсвічуємо відповідне поле червоним.
     *
     * Текст від сервера лишається як [rawText]: підставляти ресурс
     * не можна, бо бекенд повертає власне формулювання.
     */
    private fun handleLoginError(error: ApiError) {
        val message = error.message
        when {
            message.contains("Неправильний пароль") -> {
                uiState = uiState.copy(
                    passwordError = uiText(R.string.error_wrong_password),
                    passwordHasError = true
                )
            }
            message.contains("не знайдено") -> {
                uiState = uiState.copy(
                    emailError = uiText(R.string.error_email_not_found),
                    emailHasError = true
                )
            }
            else -> _events.trySend(AuthEvent.Message(rawText(message)))
        }
    }

    // ==================== РЕЄСТРАЦІЯ ====================

    fun register() {
        val state = uiState

        // Перевірка на пусті поля — підсвічуємо кожне порожнє поле
        val nicknameEmpty = state.nickname.isBlank()
        val emailEmpty = state.email.isBlank()
        val passwordEmpty = state.password.isBlank()
        val confirmEmpty = state.confirmPassword.isBlank()

        if (nicknameEmpty || emailEmpty || passwordEmpty || confirmEmpty) {
            val message = requiredFieldError
            _events.trySend(AuthEvent.Message(message))
            uiState = uiState.copy(
                nicknameError = if (nicknameEmpty) message else null,
                emailError = if (emailEmpty) message else null,
                passwordError = if (passwordEmpty) message else null,
                confirmPasswordError = if (confirmEmpty) message else null,
                nicknameHasError = nicknameEmpty,
                emailHasError = emailEmpty,
                passwordHasError = passwordEmpty,
                confirmPasswordHasError = confirmEmpty
            )
            return
        }

        // Згода з умовами
        if (!state.agreeTerms) {
            _events.trySend(AuthEvent.Message(uiText(R.string.error_terms_not_accepted)))
            return
        }

        // Валідація email
        if (!Validators.isValidEmail(state.email)) {
            uiState = uiState.copy(
                emailError = uiText(R.string.error_invalid_email),
                emailHasError = true
            )
            return
        }

        // Перевірка довжини пароля
        if (!Validators.isPasswordLongEnough(state.password)) {
            uiState = uiState.copy(
                passwordError = uiText(R.string.error_password_too_short),
                passwordHasError = true
            )
            return
        }

        // Перевірка складності пароля
        if (!Validators.isPasswordStrong(state.password)) {
            uiState = uiState.copy(
                passwordError = uiText(R.string.error_password_too_weak),
                passwordHasError = true
            )
            return
        }

        // Паролі мають збігатися
        if (state.confirmPassword != state.password) {
            uiState = uiState.copy(
                confirmPasswordError = uiText(R.string.error_passwords_mismatch),
                confirmPasswordHasError = true
            )
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            when (val result = repository.register(
                nickname = state.nickname.trim(),
                email = state.email.trim(),
                password = state.password
            )) {
                is ApiResult.Success -> {
                    repository.saveSession(result.data, rememberMe = true)
                    _events.trySend(AuthEvent.NavigateHome)
                }
                // Сценарій дубліката: помилка від Node.js/PostgreSQL
                is ApiResult.Error -> {
                    _events.trySend(AuthEvent.Message(rawText(result.error.message)))
                }
            }
            uiState = uiState.copy(isLoading = false)
        }
    }

    // ==================== GOOGLE SIGN-IN ====================

    fun googleLogin(idToken: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            when (val result = repository.googleLogin(idToken)) {
                is ApiResult.Success -> {
                    repository.saveSession(result.data, rememberMe = true)
                    _events.trySend(AuthEvent.NavigateHome)
                }
                is ApiResult.Error -> _events.trySend(AuthEvent.Message(rawText(result.error.message)))
            }
            uiState = uiState.copy(isLoading = false)
        }
    }

    // ==================== ВІДНОВЛЕННЯ ПАРОЛЯ ====================

    fun forgotPassword() {
        val state = uiState

        if (state.email.isBlank()) {
            _events.trySend(AuthEvent.Message(requiredFieldError))
            return
        }

        if (!Validators.isValidEmail(state.email)) {
            uiState = uiState.copy(
                emailError = uiText(R.string.error_invalid_email),
                emailHasError = true
            )
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            when (val result = repository.forgotPassword(state.email.trim())) {
                is ApiResult.Success -> {
                    _events.trySend(AuthEvent.Message(uiText(R.string.forgot_password_sent)))
                    _events.trySend(AuthEvent.ForgotPasswordSent)
                }
                is ApiResult.Error -> _events.trySend(AuthEvent.Message(rawText(result.error.message)))
            }
            uiState = uiState.copy(isLoading = false)
        }
    }
}
