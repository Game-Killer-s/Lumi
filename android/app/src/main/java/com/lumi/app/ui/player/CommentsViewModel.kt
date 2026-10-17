package com.lumi.app.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.app.R
import com.lumi.app.data.model.Comment
import com.lumi.app.data.repository.CommentsRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.network.TokenManager
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.uiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Стан шторки коментарів: список, чернетка нового коментаря, запити в дорозі. */
data class CommentsUiState(
    val trackId: String = "",
    val comments: List<Comment> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val draft: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    val deletingId: String? = null,
    /** Коментар, для якого показуємо підтвердження видалення. */
    val pendingDelete: Comment? = null,
    val error: UiText? = null
) {
    val canSend: Boolean get() = CommentsRules.canSend(draft, isSending)

    val canLoadMore: Boolean get() = CommentsRules.canLoadMore(comments.size, total, isLoadingMore)

    val isDraftTooLong: Boolean get() = CommentsRules.isTooLong(draft)
}

sealed class CommentsEvent {
    /** Snackbar: «Коментар додано», «Коментар видалено», помилка. */
    data class Message(val text: UiText) : CommentsEvent()

    /**
     * Список змінився (додали чи видалили) — плеєр має перечитати
     * лічильник коментарів у своїй шапці.
     */
    data object Changed : CommentsEvent()
}

/**
 * @param currentUserId id поточного користувача: за ним вирішуємо, чи можна
 * видаляти коментар (чужі видаляє лише модератор, ТЗ 6.7.2).
 */
class CommentsViewModel(
    private val repository: CommentsRepository = CommentsRepository(),
    private val currentUserId: String? = TokenManager.getCurrentUser()?.id
) : ViewModel() {

    var uiState by mutableStateOf(CommentsUiState())
        private set

    private val _events = Channel<CommentsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Готує шторку до показу.
     *
     * Повторне відкриття того самого треку нічого не перечитує: чернетка
     * й прокрутка лишаються на місці, а зайвих запитів до бекенду немає.
     */
    fun open(trackId: String) {
        if (trackId.isBlank()) {
            return
        }

        if (uiState.trackId == trackId && uiState.comments.isNotEmpty()) {
            return
        }

        uiState = CommentsUiState(trackId = trackId)
        loadPage(page = CommentsRepository.FIRST_PAGE)
    }

    /** Повторити завантаження після помилки. */
    fun retry() {
        loadPage(page = CommentsRepository.FIRST_PAGE)
    }

    fun loadMore() {
        if (!uiState.canLoadMore || uiState.isLoading) {
            return
        }

        loadPage(page = uiState.page + 1)
    }

    fun onDraftChange(text: String) {
        uiState = uiState.copy(draft = text)
    }

    fun send() {
        if (!uiState.canSend) {
            // Порожній текст або подвійний натиск — нічого не надсилаємо.
            return
        }

        val trackId = uiState.trackId
        val text = uiState.draft.trim()

        uiState = uiState.copy(isSending = true, error = null)

        viewModelScope.launch {
            when (val result = repository.addComment(trackId, text)) {
                is ApiResult.Success -> {
                    uiState = uiState.copy(
                        comments = CommentsRules.merge(listOf(result.data), uiState.comments),
                        total = uiState.total + 1,
                        draft = "",
                        isSending = false,
                        error = null
                    )
                    _events.trySend(CommentsEvent.Message(uiText(R.string.comments_added)))
                    _events.trySend(CommentsEvent.Changed)
                }

                is ApiResult.Error -> {
                    uiState = uiState.copy(
                        isSending = false,
                        error = uiText(R.string.comments_error_send)
                    )
                    _events.trySend(CommentsEvent.Message(uiText(R.string.comments_error_send)))
                }
            }
        }
    }

    /** Кнопка «Видалити»: спершу питаємо підтвердження. */
    fun requestDelete(comment: Comment) {
        if (!CommentsRules.isOwn(comment, currentUserId)) {
            return
        }

        uiState = uiState.copy(pendingDelete = comment)
    }

    fun cancelDelete() {
        uiState = uiState.copy(pendingDelete = null)
    }

    fun confirmDelete() {
        val comment = uiState.pendingDelete ?: return

        if (uiState.deletingId != null) {
            // Попереднє видалення ще в дорозі — другу операцію не запускаємо.
            return
        }

        uiState = uiState.copy(pendingDelete = null, deletingId = comment.id, error = null)

        viewModelScope.launch {
            when (val result = repository.deleteComment(comment.id)) {
                is ApiResult.Success -> {
                    uiState = uiState.copy(
                        comments = uiState.comments.filterNot { it.id == comment.id },
                        total = (uiState.total - 1).coerceAtLeast(0),
                        deletingId = null,
                        error = null
                    )
                    _events.trySend(CommentsEvent.Message(uiText(R.string.comments_deleted)))
                    _events.trySend(CommentsEvent.Changed)
                }

                is ApiResult.Error -> {
                    uiState = uiState.copy(
                        deletingId = null,
                        error = uiText(R.string.comments_error_delete)
                    )
                    _events.trySend(CommentsEvent.Message(uiText(R.string.comments_error_delete)))
                }
            }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    /**
     * Чи коментар належить поточному користувачу.
     *
     * За цим UI вирішує, яку дію показати: «Видалити» для свого коментаря
     * і «Поскаржитися» для чужого (ТЗ 6.7.2).
     */
    fun isOwn(comment: Comment): Boolean = CommentsRules.isOwn(comment, currentUserId)

    private fun loadPage(page: Int) {
        val trackId = uiState.trackId
        if (trackId.isBlank()) {
            return
        }

        val isFirstPage = page <= CommentsRepository.FIRST_PAGE

        uiState = if (isFirstPage) {
            uiState.copy(isLoading = true, error = null)
        } else {
            uiState.copy(isLoadingMore = true, error = null)
        }

        viewModelScope.launch {
            val result = repository.getComments(trackId, page, CommentsRepository.PAGE_SIZE)

            when (result) {
                is ApiResult.Success -> uiState = uiState.copy(
                    // Перша сторінка замінює список, наступні — дописуються.
                    comments = CommentsRules.merge(
                        existing = if (isFirstPage) emptyList() else uiState.comments,
                        page = result.data.comments
                    ),
                    total = result.data.total,
                    page = page,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null
                )

                is ApiResult.Error -> uiState = uiState.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = uiText(R.string.comments_error)
                )
            }
        }
    }
}
