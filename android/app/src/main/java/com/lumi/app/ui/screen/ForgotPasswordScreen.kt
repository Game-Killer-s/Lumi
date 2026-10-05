package com.lumi.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumi.app.R
import com.lumi.app.ui.auth.AuthBackground
import com.lumi.app.ui.auth.AuthEvent
import com.lumi.app.ui.auth.AuthViewModel
import com.lumi.app.ui.auth.BottomEqualizer
import com.lumi.app.ui.auth.LockIcon
import com.lumi.app.ui.auth.LumiTextField
import com.lumi.app.ui.auth.MinTouchTarget
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.auth.PrimaryAuthButton
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.i18n.uiText

/**
 * Екран відновлення доступу за CSS:
 * - заголовок «Забули пароль?»,
 * - іконка замка в колі,
 * - «Відновлення паролю» + опис,
 * - поле Email,
 * - кнопка «Надіслати код доступу»,
 * - «Повернутися до входу».
 */
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    val viewModel: AuthViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    // Context потрібен лише для перекладу повідомлень у Snackbar.
    val context = LocalContext.current
    val state = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.Message -> snackbarHostState.showSnackbar(event.text.resolveCurrent(context))
                is AuthEvent.ForgotPasswordSent -> onBack()
                else -> Unit
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
                // Заголовок (top 48)
                Spacer(Modifier.height(48.dp))
                Text(
                    text = stringResource(R.string.forgot_title),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Замок (Ellipse 1, top 127)
                Spacer(Modifier.height(63.dp))
                LockIcon()

                // «Відновлення паролю» (top 304)
                Spacer(Modifier.height(61.dp))
                Text(
                    text = stringResource(R.string.forgot_heading),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 28.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Опис (top 343)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.forgot_description),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Поле Email (top 404)
                Spacer(Modifier.height(48.dp))
                LumiTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = uiText(R.string.field_email),
                    placeholder = uiText(R.string.field_email_placeholder),
                    isError = state.emailHasError,
                    errorText = state.emailError,
                    keyboardType = KeyboardType.Email
                )

                // Кнопка «Надіслати код доступу»
                Spacer(Modifier.height(16.dp))
                PrimaryAuthButton(
                    text = uiText(R.string.forgot_submit),
                    onClick = { viewModel.forgotPassword() },
                    enabled = !state.isLoading,
                    loading = state.isLoading,
                    height = 44.dp
                )

                // «Повернутися до входу» (top 587)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.forgot_back_to_login),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onBack() }
                        // Зона дотику 48dp: текст лишається по центру (ТЗ 6.3.7).
                        .heightIn(min = MinTouchTarget)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(8.dp)
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

