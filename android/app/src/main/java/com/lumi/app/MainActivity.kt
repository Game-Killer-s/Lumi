package com.lumi.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lumi.app.ui.i18n.LumiLocalization
import com.lumi.app.ui.i18n.ProvideLumiLocalization
import com.lumi.app.ui.theme.LumiTheme

class MainActivity : ComponentActivity() {

    /**
     * Мова застосунку застосовується до створення Activity,
     * тому перший кадр після холодного старту вже локалізований.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LumiLocalization.applyToBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Мова + тема реагують на налаштування без перезапуску застосунку.
            ProvideLumiLocalization {
                LumiTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LumiApp()
                    }
                }
            }
        }
    }
}
