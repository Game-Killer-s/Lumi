package com.lumi.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(onBack: () -> Unit) {
    var message by remember { mutableStateOf("") }
    val messages = remember { mutableStateOf(listOf("Привіт! Чим можемо допомогти?", "Дякуємо! Ваше питання передано фахівцю.")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Онлайн-чат підтримки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages.value.size) { index ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(messages.value[index], modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Напишіть повідомлення...") }
                )
                Button(onClick = {
                    if (message.isNotBlank()) {
                        messages.value = messages.value + message
                        message = ""
                    }
                }) {
                    Text("Надіслати")
                }
            }
        }
    }
}
