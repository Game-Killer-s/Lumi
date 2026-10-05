package com.lumi.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
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
import com.lumi.app.ui.auth.AuthEvent
import com.lumi.app.ui.auth.AuthLogo
import com.lumi.app.ui.auth.AuthMutedLinkColor
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
 * Екран входу за CSS (412x917):
 * - логотип Lumi зверху,
 * - поля Email / Пароль з підписами,
 * - «Забули пароль?» справа під полем пароля,
 * - кнопка «Увiйти» (акцентний градієнт),
 * - «Ще немає акаунту? Зареєструватися»,
 * - розділювач «або»,
 * - кнопка Google (фон #DDE6FF),
 * - еквалайзер унизу.
 */
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val viewModel: AuthViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state = viewModel.uiState

    // Повідомлення Google Sign-In перекладаються в момент показу,
    // тому перемикання мови діє навіть для вже сформованої помилки.
    val launchGoogle = rememberGoogleSignInLauncher(
        onIdToken = { viewModel.googleLogin(it) },
        onError = { scope.launch { snackbarHostState.showSnackbar(it.resolveCurrent(context)) } }
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.Message -> snackbarHostState.showSnackbar(event.text.resolveCurrent(context))
                is AuthEvent.NavigateHome -> onLoginSuccess()
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
                // Відступ зверху = 141px (з CSS)
                Spacer(Modifier.height(141.dp))

                AuthLogo()

                // 284 - (141 + 119) = 24
                Spacer(Modifier.height(24.dp))

                LumiTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = uiText(R.string.field_email),
                    placeholder = uiText(R.string.field_email_placeholder),
                    isError = state.emailHasError,
                    errorText = state.emailError,
                    keyboardType = KeyboardType.Email
                )

                // 383 - (305 + 50 + 6) = 22
                Spacer(Modifier.height(22.dp))

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

                // «Забули пароль?» — праворуч, під полем пароля
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(R.string.login_forgot_password),
                        fontFamily = Montserrat,
                        color = AuthMutedLinkColor,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        modifier = Modifier
                            .clickable(role = Role.Button) { onNavigateToForgotPassword() }
                            // Зона дотику 48dp, текст лишається центрованим (ТЗ 6.3.7).
                            .heightIn(min = MinTouchTarget)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 8.dp)
                    )
                }

                // 502 - (404 + 50 + 4 + 11 + 8) = 25
                Spacer(Modifier.height(17.dp))

                PrimaryAuthButton(
                    text = uiText(R.string.login_submit),
                    onClick = { viewModel.login() },
                    enabled = !state.isLoading,
                    loading = state.isLoading,
                    height = 50.dp
                )

                // «Ще немає акаунту? Зареєструватися» (top 568)
                // 8dp замість 16dp — компенсація збільшеної зони дотику посилання
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.login_no_account),
                        fontFamily = Montserrat,
                        color = AuthTextColor,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.login_register_link),
                        fontFamily = Montserrat,
                        color = AuthTextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(role = Role.Button) { onNavigateToRegister() }
                            // Зона дотику 48dp, текст лишається центрованим (ТЗ 6.3.7).
                            .heightIn(min = MinTouchTarget)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 4.dp)
                    )
                }

                // розділювач «або» (top 607)
                Spacer(Modifier.height(18.dp))
                OrDivider()

                // кнопка Google (top 646)
                Spacer(Modifier.height(25.dp))
                GoogleAuthButton(
                    text = uiText(R.string.login_google),
                    onClick = { launchGoogle() }
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

