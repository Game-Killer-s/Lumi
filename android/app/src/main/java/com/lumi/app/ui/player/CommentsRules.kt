package com.lumi.app.ui.player

import com.lumi.app.data.model.Comment

/**
 * Правила списку коментарів (ТЗ 6.3.11, ТЗ 6.7.2).
 *
 * Тут уся «бухгалтерія»: ліміт довжини, пагінація, дублікати після
 * повторного завантаження та право видаляти лише свій коментар.
 */
object CommentsRules {

    /** Максимальна довжина коментаря — більше сервер однаково відхилить. */
    const val MAX_LENGTH = 500

    /** Надсилати можна непорожній текст, поки попередній запит не в дорозі. */
    fun canSend(draft: String, isSending: Boolean): Boolean {
        if (isSending) {
            return false
        }

        val text = draft.trim()

        return text.isNotEmpty() && text.length <= MAX_LENGTH
    }

    /** Чи задовгий чернетка: показуємо підказку під полем вводу. */
    fun isTooLong(draft: String): Boolean = draft.trim().length > MAX_LENGTH

    /** Чи є ще сторінки: скільки завантажено проти total із бекенду. */
    fun canLoadMore(loaded: Int, total: Int, isLoadingMore: Boolean): Boolean {
        return !isLoadingMore && loaded < total
    }

    /** Свій коментар: тоді доступні видалення та скарга «від імені автора». */
    fun isOwn(comment: Comment, currentUserId: String?): Boolean {
        if (currentUserId.isNullOrBlank()) {
            return false
        }

        return comment.userId == currentUserId
    }

    /**
     * Додає сторінку до вже завантажених коментарів.
     *
     * Захист від дублікатів потрібен із двох причин: після додавання свого
     * коментаря він уже є в списку, а повторне завантаження тієї самої
     * сторінки не повинно подвоювати рядки. Приховані модератором
     * коментарі у списку не показуємо взагалі.
     */
    fun merge(existing: List<Comment>, page: List<Comment>): List<Comment> {
        val visiblePage = page.filterNot { it.isHidden }

        if (visiblePage.isEmpty()) {
            return existing
        }

        val known = existing.mapTo(HashSet()) { it.id }

        return existing + visiblePage.filter { known.add(it.id) }
    }

    /** Скільки коментарів реально видно у списку. */
    fun visibleCount(comments: List<Comment>): Int = comments.count { !it.isHidden }
}
