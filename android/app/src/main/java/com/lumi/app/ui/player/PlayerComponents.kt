package com.lumi.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.app.R
import com.lumi.app.data.model.Track
import com.lumi.app.ui.auth.AuthAccentGradient
import com.lumi.app.ui.auth.AuthBackgroundColor
import com.lumi.app.ui.auth.AuthGradient
import com.lumi.app.ui.auth.AuthPlaceholderColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.MinTouchTarget
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.localized
import com.lumi.app.ui.subscription.LumiSecondaryButton

/**
 * Спільні деталі екрана плеєра: шторка, обкладинка, прогрес, меню «⋯».
 *
 * Компоненти не знають нічого про відтворення — вони лише показують те, що
 * приходить зі стану ViewModel-ей (текст пісні, коментарі, скарга).
 */

/** Розмір обкладинки за макетом плеєра. */
private val CoverSize = 240.dp

/** Висота смуги прогресу. */
private val ProgressHeight = 4.dp

/**
 * Нижня шторка у стилі Lumi: темний фон, «ручка», заголовок і хрестик.
 *
 * Заголовок показуємо всередині шторки (а не в системному TopAppBar), щоб
 * користувач бачив, до якого саме треку належать текст чи коментарі, і міг
 * закрити шторку одним дотиком у зоні 48dp (ТЗ 6.3.7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumiBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val closeLabel = stringResource(R.string.common_close)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = AuthBackgroundColor,
        dragHandle = { SheetHandle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontFamily = Montserrat,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Хрестик: озвучуємо як «Закрити», сам символ лишаємо декоративним.
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics(mergeDescendants = true) { contentDescription = closeLabel },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    fontFamily = Montserrat,
                    color = AuthTextColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = content
        )

        Spacer(Modifier.height(24.dp))
    }
}

/** «Ручка» шторки: декоративна, тому з обходу TalkBack виключена. */
@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(ProgressHeight)
                .background(AuthPlaceholderColor, RoundedCornerShape(2.dp))
                .clearAndSetSemantics { }
        )
    }
}

/**
 * Обкладинка треку.
 *
 * Під нею завжди лежить брендовий градієнт із першою літерою назви, а сам
 * малюнок підтягується через Coil лише коли посилання є. Так місце
 * обкладинки ніколи не «порожніє»: без мережі чи без картинки залишається
 * градієнт, а не сірий прямокутник.
 */
@Composable
fun PlayerCoverArt(track: Track, modifier: Modifier = Modifier) {
    val coverUrl = track.coverUrl?.takeIf { it.isNotBlank() } ?: track.albumCoverUrl

    Box(
        modifier = modifier
            .size(CoverSize)
            .clip(RoundedCornerShape(16.dp))
            .background(AuthGradient),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = track.title.take(1).uppercase(),
            fontFamily = Montserrat,
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 72.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.clearAndSetSemantics { }
        )

        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                // Обкладинка декоративна: назву озвучує заголовок під нею.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Смуга прогресу з підписами «0:42 / 3:34».
 *
 * Позиція приходить із шару відтворення; тут ми лише малюємо її. Для TalkBack
 * смуга озвучується як «0:42 з 3:34», а не як безіменний індикатор (ТЗ 6.3.7).
 */
@Composable
fun PlayerProgress(
    positionSeconds: Int,
    durationSeconds: Int,
    modifier: Modifier = Modifier
) {
    val fraction = PlayerRules.progressFraction(positionSeconds, durationSeconds)
    val positionLabel = PlayerRules.durationLabel(positionSeconds)
    val durationLabel = PlayerRules.durationLabel(durationSeconds)
    val stateDescription = stringResource(
        R.string.player_progress_state,
        positionLabel,
        durationLabel
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ProgressHeight)
                .semantics {
                    contentDescription = stateDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                }
        ) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)

            drawRoundRect(
                color = AuthPlaceholderColor.copy(alpha = 0.35f),
                cornerRadius = radius
            )

            if (fraction > 0f) {
                drawRoundRect(
                    brush = AuthAccentGradient,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = radius
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ProgressTime(positionLabel)
            Spacer(Modifier.weight(1f))
            ProgressTime(durationLabel)
        }
    }
}

@Composable
private fun ProgressTime(label: String) {
    Text(
        text = label,
        fontFamily = Montserrat,
        color = AuthPlaceholderColor,
        fontSize = 11.sp
    )
}

/**
 * Стан «завантажуємо» / «помилка» / «порожньо» з кнопкою повтору.
 * Спільний для шторок і самого екрана плеєра, щоб повідомлення виглядали
 * однаково в усіх місцях.
 */
@Composable
fun PlayerMessage(
    text: UiText,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = AuthTextColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = text.localized(),
            fontFamily = Montserrat,
            color = AuthTextColor,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            LumiSecondaryButton(
                text = stringResource(R.string.common_retry),
                onClick = onRetry
            )
        }
    }
}

/**
 * Кнопка «⋯» з меню дій над треком. Поки дія одна — скарга (ТЗ 6.3.11),
 * але меню легко розширити, не чіпаючи верстку екрана.
 */
@Composable
fun PlayerMenuButton(
    onReportTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.player_menu)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
                .clickable(role = Role.Button) { isExpanded = true }
                .semantics(mergeDescendants = true) { contentDescription = menuLabel },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⋯",
                fontFamily = Montserrat,
                color = AuthTextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.player_report_track),
                        fontFamily = Montserrat,
                        color = AuthTextColor,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    isExpanded = false
                    onReportTrack()
                }
            )
        }
    }
}
