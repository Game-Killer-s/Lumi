package com.lumi.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumi.app.data.repository.AuthRepository
import com.lumi.app.network.ApiResult
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onRegister: () -> Unit) {
    AuthForm(title = "Увійти в Lumi", button = "Увійти", showUsername = false, onSubmit = { username, email, password ->
        val scopeResult = AuthRepository().login(email, password)
        scopeResult
    }, onSuccess = onLoggedIn, secondaryLabel = "Створити акаунт", onSecondary = onRegister)
}

@Composable
fun RegisterScreen(onRegistered: () -> Unit, onLogin: () -> Unit) {
    AuthForm(title = "Створити акаунт", button = "Зареєструватися", showUsername = true, onSubmit = { username, email, password ->
        AuthRepository().register(username, email, password)
    }, onSuccess = onRegistered, secondaryLabel = "Уже є акаунт", onSecondary = onLogin)
}

@Composable
private fun AuthForm(
    title: String,
    button: String,
    showUsername: Boolean,
    onSubmit: suspend (String, String, String) -> ApiResult<*>,
    onSuccess: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Lumi", style = MaterialTheme.typography.displayLarge)
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (showUsername) OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Ім'я") }, singleLine = true)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Пароль") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    scope.launch {
                        val result = onSubmit(username, email, password)
                        if (result is ApiResult.Success<*>) {
                            val auth = result.data
                            if (auth is com.lumi.app.data.model.AuthResponse) AuthRepository().saveSession(auth)
                            onSuccess()
                        } else if (result is ApiResult.Error) error = result.error.message
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(button) }
            TextButton(onClick = onSecondary, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(secondaryLabel) }
        }
    }
}
