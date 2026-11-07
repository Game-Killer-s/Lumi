package com.lumi.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.app.R
import com.lumi.app.ui.auth.AuthGradient
import com.lumi.app.ui.auth.AuthPlaceholderColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.uiText

/** Максимальна висота списку рядків: далі текст прокручується. */
private val LyricsMaxHeight = 420.dp

/**
 * Шторка з текстом пісні (ТЗ 6.3.11).
 *
 * Рядок, який «звучить», підсвічується градієнтом і до нього автоматично
 * прокручується список — так текст читається разом із музикою. Сам текст
 * ми лише показуємо: жодного впливу на відтворення шторка не має, тож
 * музика не спиняється, поки користувач читає.
 */
@Composable
fun LyricsSheet(
    title: String,
    state: LyricsUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    LumiBottomSheet(title = title, onDismiss = onDismiss) {
        val error = state.error

        when {
            state.isLoading -> PlayerMessage(
                text = uiText(R.string.lyrics_loading),
                isLoading = true
            )

            error != null -> PlayerMessage(text = error, onRetry = onRetry)

            // Текст завантажився, але порожній: у треку його просто немає.
            state.lines.isEmpty() -> PlayerMessage(text = uiText(R.string.lyrics_empty))

            else -> LyricsLines(state = state)
        }
    }
}

/** Рядки тексту з підсвічуванням поточного. */
@Composable
private fun LyricsLines(state: LyricsUiState) {
    val listState = rememberLazyListState()

    // Підсвічений рядок завжди має бути видно: інакше підсвічування
    // «звучить» десь за межами екрана.
    LaunchedEffect(state.highlightedLine) {
        if (state.highlightedLine in state.lines.indices) {
            listState.animateScrollToItem(state.highlightedLine)
        }
    }

    Text(
        text = stringResource(R.string.lyrics_hint),
        fontFamily = Montserrat,
        color = AuthPlaceholderColor,
        fontSize = 11.sp
    )

    Spacer(Modifier.height(10.dp))

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = LyricsMaxHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(state.lines) { index, line ->
            val isHighlighted = index == state.highlightedLine

            Text(
                text = line,
                fontFamily = Montserrat,
                color = if (isHighlighted) Color.White else AuthTextColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isHighlighted) {
                            Modifier
                                .background(AuthGradient, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        } else {
                            Modifier.padding(vertical = 6.dp)
                        }
                    )
            )
        }
    }
}
