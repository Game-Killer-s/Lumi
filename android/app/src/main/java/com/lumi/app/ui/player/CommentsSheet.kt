package com.lumi.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.app.R
import com.lumi.app.data.model.Comment
import com.lumi.app.ui.auth.AuthBackgroundColor
import com.lumi.app.ui.auth.AuthErrorColor
import com.lumi.app.ui.auth.AuthGradient
import com.lumi.app.ui.auth.AuthPlaceholderColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.LumiTextField
import com.lumi.app.ui.auth.MinTouchTarget
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.auth.PrimaryAuthButton
import com.lumi.app.ui.i18n.LumiFormatters
import com.lumi.app.ui.i18n.currentAppLocale
import com.lumi.app.ui.i18n.localized
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.i18n.uiText
import com.lumi.app.ui.subscription.LumiSecondaryButton

/** Максимальна висота списку коментарів: далі список прокручується. */
private val CommentsMaxHeight = 340.dp

/** Скільки рядків показує поле нового коментаря. */
private const val CommentsInputLines = 3

/** Висота кнопки «Надіслати» (як у кнопок реєстрації). */
private val SendButtonHeight = 44.dp

/**
 * Шторка коментарів до треку (ТЗ 6.3.11, ТЗ 6.7.2).
 *
 * Усередині — список із пагінацією, поле нового коментаря та підтвердження
 * видалення. Скарга на коментар відкривається в спільному діалозі: шторка
 * лише повідомляє батьківський екран, на який коментар скаржимось.
 *
 * @param onCommentsChanged список змінився — плеєр перечитує лічильник.
 * @param onReportComment скарга на коментар: показує її екран плеєра, щоб
 * діалог був один на весь екран, а не окремий усередині шторки.
 */
@Composable
fun CommentsSheet(
    trackId: String,
    viewModel: CommentsViewModel,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onCommentsChanged: () -> Unit,
    onReportComment: (Comment) -> Unit
) {
    val state = viewModel.uiState
    val context = LocalContext.current

    // Шторку відкрили — готуємо список. Повторне відкриття того самого
    // треку нічого не перечитує, це вже вирішує ViewModel.
    LaunchedEffect(trackId) { viewModel.open(trackId) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // Результат операції показуємо Snackbar-ом обраною мовою.
                is CommentsEvent.Message ->
                    snackbarHostState.showSnackbar(event.text.resolveCurrent(context))

                is CommentsEvent.Changed -> onCommentsChanged()
            }
        }
    }

    LumiBottomSheet(
        // Заголовок — це і є лічильник: «7 коментарів» для поточного треку.
        title = pluralStringResource(R.plurals.comments_count, state.total, state.total),
        onDismiss = onDismiss
    ) {
        val error = state.error

        when {
            state.isLoading -> PlayerMessage(
                text = uiText(R.string.comments_loading),
                isLoading = true
            )

            // Помилку без списку показуємо повідомленням із кнопкою повтору.
            error != null && state.comments.isEmpty() ->
                PlayerMessage(text = error, onRetry = viewModel::retry)

            state.comments.isEmpty() -> PlayerMessage(text = uiText(R.string.comments_empty))

            else -> {
                CommentList(
                    state = state,
                    viewModel = viewModel,
                    onReportComment = onReportComment
                )

                if (error != null) {
                    // Помилку при вже завантаженому списку показуємо рядком,
                    // щоб не закривати коментарі повідомленням на всю шторку.
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error.localized(),
                        fontFamily = Montserrat,
                        color = AuthErrorColor,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        LumiTextField(
            value = state.draft,
            onValueChange = viewModel::onDraftChange,
            label = uiText(R.string.comments_input_label),
            placeholder = uiText(R.string.comments_input_hint),
            isError = state.isDraftTooLong,
            errorText = if (state.isDraftTooLong) {
                uiText(R.string.comments_error_too_long, CommentsRules.MAX_LENGTH)
            } else {
                null
            },
            maxLines = CommentsInputLines
        )

        Spacer(Modifier.height(12.dp))

        PrimaryAuthButton(
            text = uiText(R.string.comments_send),
            onClick = viewModel::send,
            height = SendButtonHeight,
            enabled = state.canSend,
            loading = state.isSending
        )
    }

    // Підтвердження видалення — окреме вікно поверх шторки.
    state.pendingDelete?.let { pending ->
        DeleteCommentDialog(
            comment = pending,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }
}

/** Список коментарів і кнопка «Показати ще» під ним. */
@Composable
private fun CommentList(
    state: CommentsUiState,
    viewModel: CommentsViewModel,
    onReportComment: (Comment) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = CommentsMaxHeight),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // key = id: після додавання чи видалення Compose перемальовує лише
        // змінені рядки, а не весь список.
        items(state.comments, key = { it.id }) { comment ->
            CommentRow(
                comment = comment,
                isOwn = viewModel.isOwn(comment),
                isDeleting = state.deletingId == comment.id,
                onDelete = { viewModel.requestDelete(comment) },
                onReport = { onReportComment(comment) }
            )
        }
    }

    if (state.canLoadMore || state.isLoadingMore) {
        Spacer(Modifier.height(12.dp))

        if (state.isLoadingMore) {
            // Наступна сторінка в дорозі: показуємо індикатор, а не кнопку,
            // щоб користувач не натиснув «Показати ще» вдруге.
            PlayerMessage(text = uiText(R.string.comments_loading), isLoading = true)
        } else {
            LumiSecondaryButton(
                text = stringResource(R.string.comments_load_more),
                onClick = viewModel::loadMore
            )
        }
    }
}

/**
 * Текстова дія під коментарем («Видалити» або «Поскаржитися»).
 *
 * Маленький текст, але зона дотику 48dp і роль Button, щоб TalkBack бачив
 * саме дію, а не просто слово (ТЗ 6.3.7).
 */
@Composable
private fun CommentAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    Box(
        modifier = Modifier
            .sizeIn(minHeight = MinTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontFamily = Montserrat,
            // Видалення — червоне, решта — звичайний колір тексту.
            color = when {
                !enabled -> AuthPlaceholderColor
                isDestructive -> AuthErrorColor
                else -> AuthTextColor
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Один коментар: автор, дата, текст і доступні дії.
 *
 * Дії залежать від авторства (ТЗ 6.7.2): свій коментар можна видалити,
 * чужий — поскаржити модератору.
 */
@Composable
private fun CommentRow(
    comment: Comment,
    isOwn: Boolean,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    val authorName = comment.user?.nickname
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.comments_author_unknown)
    val createdAt = LumiFormatters.formatDate(comment.createdAt, currentAppLocale())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuthGradient, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = authorName,
                fontFamily = Montserrat,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (isOwn) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.comments_own_label),
                    fontFamily = Montserrat,
                    color = AuthPlaceholderColor,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.weight(1f))

            if (createdAt != null) {
                Text(
                    text = createdAt,
                    fontFamily = Montserrat,
                    color = AuthPlaceholderColor,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = comment.text,
            fontFamily = Montserrat,
            color = AuthTextColor,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        if (comment.isHidden) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.comments_hidden_label),
                fontFamily = Montserrat,
                color = AuthErrorColor,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(4.dp))

        if (isDeleting) {
            // Поки видалення в дорозі, дії не показуємо: подвійний натиск
            // створив би два запити на видалення того самого коментаря.
            Text(
                text = stringResource(R.string.common_loading),
                fontFamily = Montserrat,
                color = AuthPlaceholderColor,
                fontSize = 12.sp
            )
        } else if (isOwn) {
            CommentAction(
                text = stringResource(R.string.comments_delete),
                onClick = onDelete,
                isDestructive = true
            )
        } else {
            CommentAction(
                text = stringResource(R.string.comments_report),
                onClick = onReport
            )
        }
    }
}

/**
 * Підтвердження видалення: показуємо текст коментаря, щоб користувач
 * не видалив випадково не той рядок.
 */
@Composable
private fun DeleteCommentDialog(
    comment: Comment,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuthBackgroundColor,
        title = {
            Text(
                text = stringResource(R.string.comments_delete_confirm_title),
                fontFamily = Montserrat,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.comments_delete_confirm_text),
                    fontFamily = Montserrat,
                    color = AuthTextColor,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = comment.text,
                    fontFamily = Montserrat,
                    color = AuthPlaceholderColor,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    fontFamily = Montserrat,
                    color = AuthTextColor,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.comments_delete_confirm_action),
                    fontFamily = Montserrat,
                    color = AuthErrorColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
