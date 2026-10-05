package com.lumi.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumi.app.R
import com.lumi.app.ui.auth.AuthBackground
import com.lumi.app.ui.auth.AuthCheckBox
import com.lumi.app.ui.auth.AuthEvent
import com.lumi.app.ui.auth.AuthLogo
import com.lumi.app.ui.auth.AuthRegisterGoogleColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.AuthViewModel
import com.lumi.app.ui.auth.BottomEqualizer
import com.lumi.app.ui.auth.GoogleAuthButton
import com.lumi.app.ui.auth.LumiTextField
import com.lumi.app.ui.auth.MinTouchTarget
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.auth.OrDivider
import com.lumi.app.ui.auth.PrimaryAuthButton
import com.lumi.app.ui.auth.rememberGoogleSignInLauncher
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.i18n.uiText
import kotlinx.coroutines.launch

/**
 * Екран реєстрації за CSS (412x917):
 * - стрілка назад,
 * - логотип Lumi,
 * - чотири поля: Ім'я, Email, Пароль, Підтвердити пароль,
 * - чекбокс згоди з умовами,
 * - кнопка «Зареєструватися» (акцентний градієнт),
 * - «Уже маєте акаунт? Увійти»,
 * - розділювач «або»,
 * - кнопка Google (фон #D4D4D4).
 */
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    val viewModel: AuthViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state = viewModel.uiState

    val launchGoogle = rememberGoogleSignInLauncher(
        onIdToken = { viewModel.googleLogin(it) },
        onError = { scope.launch { snackbarHostState.showSnackbar(it.resolveCurrent(context)) } }
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.Message -> snackbarHostState.showSnackbar(event.text.resolveCurrent(context))
                is AuthEvent.NavigateHome -> onRegisterSuccess()
                is AuthEvent.ForgotPasswordSent -> Unit
            }
        }
    }

    AuthBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Стрілка назад (left 44, top 15)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, top = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }

                // Логотип (heading / title, top 76)
                Spacer(Modifier.height(18.dp))
                AuthLogo()

                // Frame 319 (поля) починається на top 218
                Spacer(Modifier.height(28.dp))

                LumiTextField(
                    value = state.nickname,
                    onValueChange = viewModel::onNicknameChange,
                    label = uiText(R.string.register_name_label),
                    placeholder = uiText(R.string.register_name_placeholder),
                    isError = state.nicknameHasError,
                    errorText = state.nicknameError
                )

                Spacer(Modifier.height(28.dp))

                LumiTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = uiText(R.string.field_email),
                    placeholder = uiText(R.string.field_email_placeholder),
                    isError = state.emailHasError,
                    errorText = state.emailError,
                    keyboardType = KeyboardType.Email
                )

                Spacer(Modifier.height(28.dp))

                LumiTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = uiText(R.string.field_password),
                    placeholder = uiText(R.string.field_password_placeholder),
                    isError = state.passwordHasError,
                    errorText = state.passwordError,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (state.showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = viewModel::togglePasswordVisibility) {
                            Icon(
                                imageVector = if (state.showPassword) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = stringResource(
                                    if (state.showPassword) {
                                        R.string.field_hide_password
                                    } else {
                                        R.string.field_show_password
                                    }
                                ),
                                tint = AuthTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                )

                Spacer(Modifier.height(28.dp))

                LumiTextField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = uiText(R.string.register_confirm_password_label),
                    placeholder = uiText(R.string.register_confirm_password_placeholder),
                    isError = state.confirmPasswordHasError,
                    errorText = state.confirmPasswordError,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (state.showConfirmPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = viewModel::toggleConfirmPasswordVisibility) {
                            Icon(
                                imageVector = if (state.showConfirmPassword) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = stringResource(
                                    if (state.showConfirmPassword) {
                                        R.string.field_hide_password
                                    } else {
                                        R.string.field_show_password
                                    }
                                ),
                                tint = AuthTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                )

                // Group 36 — згода з умовами (top 606)
                // Рядок цілком є чекбоксом: зона дотику ≥48dp і один
                // семантичний вузол для TalkBack («checkbox», стан + текст).
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget)
                        .toggleable(
                            value = state.agreeTerms,
                            role = Role.Checkbox,
                            onValueChange = viewModel::onAgreeTermsChange
                        ),
                    verticalAlignment = Alignment.Top
                ) {
                    AuthCheckBox(
                        checked = state.agreeTerms,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.register_terms),
                        fontFamily = Montserrat,
                        color = AuthTextColor,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                }

                // Кнопка «Зареєструватися» (top 658)
                // 25dp замість 32dp — компенсація приросту рядка згоди до 48dp
                Spacer(Modifier.height(25.dp))
                PrimaryAuthButton(
                    text = uiText(R.string.register_submit),
                    onClick = { viewModel.register() },
                    enabled = !state.isLoading,
                    loading = state.isLoading,
                    height = 44.dp
                )

                // «Уже маєте акаунт? Увійти» (top 718)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.register_have_account),
                        fontFamily = Montserrat,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.register_login_link),
                        fontFamily = Montserrat,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(role = Role.Button) { onNavigateToLogin() }
                            // Зона дотику 48dp, текст лишається центрованим (ТЗ 6.3.7).
                            .heightIn(min = MinTouchTarget)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 4.dp)
                    )
                }

                // розділювач «або» (top 757)
                Spacer(Modifier.height(18.dp))
                OrDivider()

                // кнопка Google (top 796)
                Spacer(Modifier.height(25.dp))
                GoogleAuthButton(
                    text = uiText(R.string.register_google),
                    onClick = { launchGoogle() },
                    backgroundColor = AuthRegisterGoogleColor
                )

                Spacer(Modifier.height(24.dp))
            }

            BottomEqualizer(Modifier.align(Alignment.BottomCenter))

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

