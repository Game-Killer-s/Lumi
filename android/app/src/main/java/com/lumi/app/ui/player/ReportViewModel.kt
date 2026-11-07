package com.lumi.app.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.R
import com.lumi.app.data.model.Comment
import com.lumi.app.data.repository.ReportRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.uiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Стан діалогу скарги: на що скаржимось, причина, деталі. */
data class ReportUiState(
    val isOpen: Boolean = false,
    val target: ReportTarget = ReportTarget.TRACK,
    val trackId: String = "",
    val targetId: String = "",
    val reason: ReportReason? = null,
    val details: String = "",
    val isSending: Boolean = false,
    val error: UiText? = null
) {
    val canSubmit: Boolean get() = ReportRules.canSubmit(reason, targetId, isSending)

    val isDetailsTooLong: Boolean get() = details.trim().length > ReportRules.COMMENT_MAX_LENGTH
}

sealed class ReportEvent {
    /** Результат скарги показуємо Snackbar-ом обраною мовою. */
    data class Message(val text: UiText) : ReportEvent()
}

class ReportViewModel(
    private val repository: ReportRepository = ReportRepository()
) : ViewModel() {

    var uiState by mutableStateOf(ReportUiState())
        private set

    private val _events = Channel<ReportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Скарга на сам трек (меню «⋯» у плеєрі). */
    fun openTrackReport(trackId: String) {
        if (trackId.isBlank()) {
            return
        }

        uiState = ReportUiState(
            isOpen = true,
            target = ReportTarget.TRACK,
            trackId = trackId,
            targetId = trackId
        )
    }

    /** Скарга на коментар під треком (ТЗ 6.7.2). */
    fun openCommentReport(trackId: String, comment: Comment) {
        uiState = ReportUiState(
            isOpen = true,
            target = ReportTarget.COMMENT,
            trackId = trackId,
            targetId = comment.id
        )
    }

    fun selectReason(reason: ReportReason) {
        uiState = uiState.copy(reason = reason, error = null)
    }

    fun onDetailsChange(text: String) {
        uiState = uiState.copy(details = text)
    }

    fun dismiss() {
        // Поки скарга в дорозі, діалог не закриваємо: інакше користувач
        // не побачить результат і може надіслати скаргу ще раз.
        if (uiState.isSending) {
            return
        }

        uiState = ReportUiState()
    }

    fun submit() {
        val state = uiState
        val reason = state.reason

        if (reason == null) {
            uiState = state.copy(error = uiText(R.string.report_error_reason_required))
            return
        }

        if (!state.canSubmit) {
            // Повторний натиск, поки запит у дорозі, нічого не робить.
            return
        }

        uiState = state.copy(isSending = true, error = null)

        viewModelScope.launch {
            val request = ReportRules.build(
                target = state.target,
                trackId = state.trackId,
                targetId = state.targetId,
                reason = reason,
                details = state.details
            )

            when (val result = repository.sendReport(request)) {
                is ApiResult.Success -> {
                    uiState = ReportUiState()
                    _events.trySend(ReportEvent.Message(uiText(R.string.report_sent)))
                }

                is ApiResult.Error -> uiState = uiState.copy(
                    isSending = false,
                    error = uiText(R.string.report_error_send)
                )
            }
        }
    }
}
