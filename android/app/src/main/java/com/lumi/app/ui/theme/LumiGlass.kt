package com.lumi.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Кольори та градієнти з макета бібліотеки/плеєра (Figma, dark mode).
 *
 * Значення навмисно винесені окремо від MaterialTheme: макет використовує
 * власну «скляну» палітру, яка не збігається зі стандартною colorScheme.
 */
object LumiGlassColors {
    val Background = Color(0xFF190E38)
    val Text = Color(0xFFFFFFFF)
    val SecondaryText = Color(0xFFDDE6FF)
    val NavigationHandle = Color(0xFF645A93)
    val Border = Color(0x1AFFFFFF)
    val Error = Color(0xFFD73A31)
}

/** rgba(75, 31, 111, 0.40) → rgba(109, 84, 217, 0.40). */
fun lumiSecondaryGradient(alpha: Float = 0.4f): Brush = Brush.horizontalGradient(
    listOf(
        Color(0xFF4B1F6F).copy(alpha = alpha),
        Color(0xFF6D54D9).copy(alpha = alpha)
    )
)

/** rgba(75, 31, 111, 0.36) → rgba(255, 162, 107, 0.36). */
fun lumiAccentGradient(alpha: Float = 0.36f): Brush = Brush.horizontalGradient(
    listOf(
        Color(0xFF4B1F6F).copy(alpha = alpha),
        Color(0xFFFFA26B).copy(alpha = alpha)
    )
)

/** Скляна поверхня з макета: градієнт + тонка біла рамка + radius 10. */
fun Modifier.lumiGlass(
    brush: Brush = lumiSecondaryGradient(),
    radius: Dp = 10.dp,
    border: Boolean = true
): Modifier {
    val shape = RoundedCornerShape(radius)
    var modifier = clip(shape).background(brush, shape)
    if (border) {
        modifier = modifier.border(1.dp, LumiGlassColors.Border, shape)
    }
    return modifier
}
