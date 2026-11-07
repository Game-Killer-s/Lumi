package com.lumi.app.ui.player

import androidx.annotation.StringRes
import com.lumi.app.R
import com.lumi.app.data.model.CreateReportRequest

/** На що скаржимось: трек (ТЗ 6.3.11) або коментар під ним (ТЗ 6.7.2). */
enum class ReportTarget(val wireValue: String) {
    TRACK("track"),
    COMMENT("comment")
}

/**
 * Причини скарги. Значення [wireValue] летять на бекенд (таблиця Report.reason),
 * а [labelRes] показується користувачу — мова підставляється під час показу.
 */
enum class ReportReason(val wireValue: String, @StringRes val labelRes: Int) {
    COPYRIGHT("copyright", R.string.report_reason_copyright),
    BAD_QUALITY("bad_quality", R.string.report_reason_quality),
    SPAM("spam", R.string.report_reason_spam),
    OFFENSIVE("offensive", R.string.report_reason_offensive),
    OTHER("other", R.string.report_reason_other)
}

/**
 * Формування скарги: що саме відправимо модератору.
 *
 * Скарга завжди привʼязана до треку (у БД Report.trackId обовʼязковий),
 * а target/targetId уточнюють, на що саме скаржимось — на сам трек
 * чи на конкретний коментар під ним.
 */
object ReportRules {

    /** Деталі скарги — необов'язкові, але теж обмежені за довжиною. */
    const val COMMENT_MAX_LENGTH = 300

    /** Порожні деталі перетворюємо на null, щоб не слав зайвий рядок. */
    fun optionalComment(raw: String): String? {
        val text = raw.trim()

        return when {
            text.isEmpty() -> null
            text.length > COMMENT_MAX_LENGTH -> text.take(COMMENT_MAX_LENGTH)
            else -> text
        }
    }

    /**
     * Надсилати можна, коли обрано причину й відомо, на що скаржимось.
     * Поки запит у дорозі ([isSending]) — повторний відправ блокуємо,
     * щоб не зробити дві скарги з одного натискання.
     */
    fun canSubmit(reason: ReportReason?, targetId: String, isSending: Boolean): Boolean {
        return !isSending && reason != null && targetId.isNotBlank()
    }

    fun build(
        target: ReportTarget,
        trackId: String,
        targetId: String,
        reason: ReportReason,
        details: String
    ): CreateReportRequest {
        return CreateReportRequest(
            target = target.wireValue,
            targetId = targetId,
            trackId = trackId,
            reason = reason.wireValue,
            comment = optionalComment(details)
        )
    }
}
