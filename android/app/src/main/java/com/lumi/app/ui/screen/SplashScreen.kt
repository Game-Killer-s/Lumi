package com.lumi.app.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.lumi.app.R
import com.lumi.app.data.repository.AuthRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.network.TokenManager
import com.lumi.app.ui.auth.AuthBackground
import com.lumi.app.ui.auth.Montserrat
import kotlinx.coroutines.delay

/**
 * Splash-екран: анімований логотип Lumi.
 * Після завантаження перевіряє збережену сесію:
 * якщо є refresh token — відновлює її і веде на Home,
 * інакше — на екран входу.
 */
@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    // Анімація появи логотипа: прозорість 0 -> 1, масштаб 0.7 -> 1
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.7f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing))
        alpha.animateTo(1f, animationSpec = tween(durationMillis = 700))
        delay(1200)

        // Відновлення сесії через Refresh Token («Запам'ятати мене»)
        TokenManager.init()
        val refreshToken = TokenManager.getRefreshToken()

        if (refreshToken != null) {
            when (val result = AuthRepository().refreshToken(refreshToken)) {
                is ApiResult.Success -> {
                    TokenManager.saveSession(result.data, remember = true)
                    onNavigateToHome()
                }
                is ApiResult.Error -> {
                    TokenManager.clearSession()
                    onNavigateToLogin()
                }
            }
        } else {
            onNavigateToLogin()
        }
    }

    AuthBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(alpha.value)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 80.sp,
                    lineHeight = 92.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    // Слоган Lumi — брендова фраза, не перекладається.
                    text = "Steam Your Sound",
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
