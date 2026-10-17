package com.lumi.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Налаштування") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Основна інформація", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            ListItem(headlineContent = { Text("Акаунт") }, supportingContent = { Text("anastasia@gmail.com") })
            ListItem(headlineContent = { Text("Сповіщення") }, supportingContent = { Text("Увімкнено") })
            ListItem(headlineContent = { Text("Тема") }, supportingContent = { Text("Темна") })
            ListItem(headlineContent = { Text("Мова") }, supportingContent = { Text("Українська") })
            ListItem(headlineContent = { Text("Дані й конфіденційність") })
            ListItem(headlineContent = { Text("Параметри звуку") }, supportingContent = { Text("HQ") })
            ListItem(headlineContent = { Text("Зоощадження трафіку") })
            ListItem(headlineContent = { Text("Очищення кешу") }, supportingContent = { Text("120 МБ") })
        }
    }
}
