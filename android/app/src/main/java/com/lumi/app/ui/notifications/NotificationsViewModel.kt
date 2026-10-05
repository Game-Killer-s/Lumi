package com.lumi.app.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.data.model.NotificationItem
import com.lumi.app.data.model.NotificationSettings
import com.lumi.app.data.model.UpdateNotificationSettingsRequest
import com.lumi.app.data.repository.NotificationRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.rawText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Стан екранів сповіщень: свитчі налаштувань + список шторки.
 */
data class NotificationsUiState(
    val settings: NotificationSettings = NotificationSettings(),
    val items: List<NotificationItem> = emptyList(),
    val unread: Int = 0,
    val isLoading: Boolean = false
)

sealed class NotificationsEvent {
    // Повідомлення зберігається як UiText, тому Snackbar показується
    // обраною мовою (мова може змінитися після формування події).
    data class Message(val text: UiText) : NotificationsEvent()
}

/**
 * ViewModel сповіщень: зберігає налаштування і читає шторку.
 */
class NotificationsViewModel(
    private val repository: NotificationRepository = NotificationRepository()
) : ViewModel() {

    var uiState by mutableStateOf(NotificationsUiState())
        private set

    private val _events = Channel<NotificationsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun loadSettings() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)

            when (val result = repository.getSettings()) {
                is ApiResult.Success -> uiState = uiState.copy(settings = result.data)
                is ApiResult.Error -> _events.trySend(NotificationsEvent.Message(rawText(result.error.message)))
            }

            uiState = uiState.copy(isLoading = false)
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)

            when (val result = repository.getNotifications()) {
                is ApiResult.Success -> uiState = uiState.copy(
                    items = result.data.items,
                    unread = result.data.unread
                )
                is ApiResult.Error -> _events.trySend(NotificationsEvent.Message(rawText(result.error.message)))
            }

            uiState = uiState.copy(isLoading = false)
        }
    }

    fun toggleNewReleases(value: Boolean) = save(uiState.settings.copy(newReleases = value))

    fun toggleArtistUpdates(value: Boolean) = save(uiState.settings.copy(artistUpdates = value))

    fun togglePlatformUpdates(value: Boolean) = save(uiState.settings.copy(platformUpdates = value))

    fun togglePush(value: Boolean) = save(uiState.settings.copy(pushEnabled = value))

    fun toggleEmail(value: Boolean) = save(uiState.settings.copy(emailEnabled = value))

    // Свитч перемикаємо одразу, а якщо сервер відмовив — повертаємо як було.
    private fun save(updated: NotificationSettings) {
        val previous = uiState.settings
        uiState = uiState.copy(settings = updated)

        viewModelScope.launch {
            val result = repository.updateSettings(
                UpdateNotificationSettingsRequest(
                    newReleases = updated.newReleases,
                    artistUpdates = updated.artistUpdates,
                    platformUpdates = updated.platformUpdates,
                    pushEnabled = updated.pushEnabled,
                    emailEnabled = updated.emailEnabled
                )
            )

            if (result is ApiResult.Error) {
                uiState = uiState.copy(settings = previous)
                _events.trySend(NotificationsEvent.Message(rawText(result.error.message)))
            }
        }
    }

    fun markAsRead(item: NotificationItem) {
        if (item.isRead) {
            return
        }

        viewModelScope.launch {
            when (val result = repository.markAsRead(item.id)) {
                is ApiResult.Success -> loadNotifications()
                is ApiResult.Error -> _events.trySend(NotificationsEvent.Message(rawText(result.error.message)))
            }
        }
    }

    fun markAllAsRead() {
        if (uiState.unread == 0) {
            return
        }

        viewModelScope.launch {
            when (val result = repository.markAllAsRead()) {
                is ApiResult.Success -> loadNotifications()
                is ApiResult.Error -> _events.trySend(NotificationsEvent.Message(rawText(result.error.message)))
            }
        }
    }
}
