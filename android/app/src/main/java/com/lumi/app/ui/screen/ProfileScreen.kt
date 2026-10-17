package com.lumi.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumi.app.data.model.UserProfile
import com.lumi.app.data.repository.AuthRepository
import com.lumi.app.data.repository.UserRepository
import com.lumi.app.network.ApiResult
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.height

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenSupportChat: () -> Unit
) {
    val repository = remember { UserRepository() }
    val authRepository = remember { AuthRepository() }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var edit by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val result = repository.getProfile()
        if (result is ApiResult.Success) { profile = result.data; username = result.data.username }
        else if (result is ApiResult.Error) message = result.error.message
    }

    Box(Modifier.fillMaxSize()) {
        profile?.let { user ->
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Профіль", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(user.username, style = MaterialTheme.typography.displayLarge)
                Text(user.email, style = MaterialTheme.typography.bodyLarge)
                Text("Роль: ${user.role}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (edit) {
                    androidx.compose.material3.OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ім'я") }, singleLine = true)
                    Button(onClick = {
                        scope.launch {
                            when (val result = repository.updateProfile(username = username)) {
                                is ApiResult.Success -> {
                                    profile = result.data
                                    username = result.data.username
                                    edit = false
                                    message = "Профіль оновлено"
                                }
                                is ApiResult.Error -> message = result.error.message
                            }
                        }
                    }) { Text("Зберегти") }
                } else {
                    OutlinedButton(onClick = { edit = true }) { Text("Редагувати профіль") }
                }
                Button(onClick = onOpenStatistics, modifier = Modifier.fillMaxWidth()) {
                    Text("Статистика прослуховування")
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Налаштування")
                }
                OutlinedButton(onClick = onOpenSubscription, modifier = Modifier.fillMaxWidth()) {
                    Text("Управління підпискою")
                }
                OutlinedButton(onClick = onOpenSupportChat, modifier = Modifier.fillMaxWidth()) {
                    Text("Онлайн-чат підтримки")
                }
                TextButton(onClick = onBack) { Text("Назад") }
                TextButton(onClick = { authRepository.logout(); onLoggedOut() }) { Text("Вийти") }
                message?.let { Text(it) }
            }
        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}
