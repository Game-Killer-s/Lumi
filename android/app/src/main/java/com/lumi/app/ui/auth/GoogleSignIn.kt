package com.lumi.app.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.lumi.app.BuildConfig
import com.lumi.app.R
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.uiText

/**
 * Логотип Google «G» — офіційні кольори.
 */
val GoogleGIcon: ImageVector = ImageVector.Builder(
    name = "GoogleG",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPathNodes(
        "M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
    ).let { nodes ->
        addPath(
            pathData = nodes,
            fill = SolidColor(Color(0xFF4285F4))
        )
    }
    addPathNodes(
        "M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
    ).let { nodes ->
        addPath(
            pathData = nodes,
            fill = SolidColor(Color(0xFF34A853))
        )
    }
    addPathNodes(
        "M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
    ).let { nodes ->
        addPath(
            pathData = nodes,
            fill = SolidColor(Color(0xFFFBBC05))
        )
    }
    addPathNodes(
        "M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
    ).let { nodes ->
        addPath(
            pathData = nodes,
            fill = SolidColor(Color(0xFFEA4335))
        )
    }
}.build()

/**
 * Повертає функцію запуску Google Sign-In.
 *
 * При успіху викликається [onIdToken] з idToken,
 * при помилці/скасуванні — [onError] з текстом для показу.
 *
 * Помилки віддаються як [UiText]: мова підставляється в момент показу,
 * тому повідомлення завжди відповідає поточним налаштуванням.
 */
@Composable
fun rememberGoogleSignInLauncher(
    onIdToken: (String) -> Unit,
    onError: (UiText) -> Unit,
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account: GoogleSignInAccount? =
                    GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken.isNullOrEmpty()) {
                    onError(uiText(R.string.google_error_no_token))
                } else {
                    onIdToken(idToken)
                }
            } catch (e: ApiException) {
                onError(uiText(R.string.google_error_failed, e.status.statusCode))
            }
        } else {
            onError(uiText(R.string.google_error_cancelled))
        }
    }

    return {
        runCatching {
            val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
            require(webClientId.isNotEmpty()) { "GOOGLE_WEB_CLIENT_ID is empty" }

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()

            GoogleSignIn.getClient(context, gso)
        }.onSuccess { client ->
            launcher.launch(client.signInIntent)
        }.onFailure {
            onError(uiText(R.string.google_error_not_configured))
        }
    }
}
