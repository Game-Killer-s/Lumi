package com.lumi.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumi.app.R
import com.lumi.app.ui.auth.AuthAccentGradient
import com.lumi.app.ui.auth.AuthBackgroundColor
import com.lumi.app.ui.auth.AuthErrorColor
import com.lumi.app.ui.auth.AuthGradient
import com.lumi.app.ui.auth.AuthPlaceholderColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.LumiTextField
import com.lumi.app.ui.auth.MinTouchTarget
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.auth.PrimaryAuthButton
import com.lumi.app.ui.i18n.localized
import com.lumi.app.ui.i18n.uiText

/** Скільки рядків показує поле деталей скарги. */
private const val DetailsLines = 3

/** Максимальна ширина вікна: форма читається, але не розтягується на весь екран. */
private val DialogMaxWidth = 340.dp

/**
 * Вікно скарги на трек або коментар (ТЗ 6.3.11, ТЗ 6.7.2).
 *
 * Причина обов'язкова, деталі — ні. Вікно закривається лише коли скарга не
 * в дорозі ([ReportViewModel.dismiss] це теж перевіряє), тому користувач
 * завжди бачить результат відправлення.
 *
 * Сам діалог нічого не знає про репозиторій: він малює [ReportUiState]
 * і віддає натискання далі, тож екран плеєра лишається єдиним місцем, де
 * збирається вся логіка скарги.
 */
@Composable
fun ReportDialog(
    state: ReportUiState,
    onReason: (ReportReason) -> Unit,
    onDetailsChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isOpen) {
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = DialogMaxWidth),
            color = AuthBackgroundColor,
            shape = RoundedCornerShape(16.dp)
        ) {
            // Вікно діалогу обмежене висотою екрана: довгий список причин
            // плюс поле деталей мусять прокручуватись, а не обрізатись.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(
                        if (state.target == ReportTarget.COMMENT) {
                            R.string.report_title_comment
                        } else {
                            R.string.report_title_track
                        }
                    ),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.report_reason_label),
                    fontFamily = Montserrat,
                    color = AuthPlaceholderColor,
                    fontSize = 11.sp
                )

                Spacer(Modifier.height(8.dp))

                ReportReason.entries.forEach { reason ->
                    ReasonRow(
                        reason = reason,
                        isSelected = reason == state.reason,
                        enabled = !state.isSending,
                        onSelect = { onReason(reason) }
                    )

                    Spacer(Modifier.height(8.dp))
                }

                LumiTextField(
                    value = state.details,
                    onValueChange = onDetailsChange,
                    label = uiText(R.string.report_details_label),
                    placeholder = uiText(R.string.report_details_hint),
                    isError = state.isDetailsTooLong,
                    errorText = if (state.isDetailsTooLong) {
                        uiText(R.string.report_details_too_long, ReportRules.COMMENT_MAX_LENGTH)
                    } else {
                        null
                    },
                    maxLines = DetailsLines
                )

                state.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error.localized(),
                        fontFamily = Montserrat,
                        color = AuthErrorColor,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                PrimaryAuthButton(
                    text = uiText(R.string.report_submit),
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    loading = state.isSending
                )

                Spacer(Modifier.height(8.dp))

                // «Скасувати» — текстова дія: головна кнопка у вікна одна.
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.isSending,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        fontFamily = Montserrat,
                        color = AuthTextColor,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Причина скарги: рядок зі станом вибору.
 *
 * Роль [Role.RadioButton] означає, що TalkBack сам скаже «обрано / не обрано»,
 * тому галочку малюємо лише візуально.
 */
@Composable
private fun ReasonRow(
    reason: ReportReason,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) AuthAccentGradient else AuthGradient)
            .selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(horizontal = 12.dp)
            .heightIn(min = MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(reason.labelRes),
            fontFamily = Montserrat,
            color = if (isSelected) Color.White else AuthTextColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Text(
                text = "✓",
                fontFamily = Montserrat,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}
