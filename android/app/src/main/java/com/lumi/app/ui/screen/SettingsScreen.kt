package com.lumi.app.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.app.R
import com.lumi.app.ui.auth.AuthBackground
import com.lumi.app.ui.auth.AuthPlaceholderColor
import com.lumi.app.ui.auth.AuthTextColor
import com.lumi.app.ui.auth.MinTouchTarget
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.AppLanguage
import com.lumi.app.ui.i18n.AppSettingsStore
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.i18n.uiText
import com.lumi.app.ui.subscription.LumiSecondaryButton
import com.lumi.app.ui.subscription.LumiTopBar
import com.lumi.app.ui.subscription.SectionTitle
import com.lumi.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** Підпис мови береться з ресурсів — тому міняється разом з мовою. */
@StringRes
private fun AppLanguage.titleRes(): Int = when (this) {
    AppLanguage.UKRAINIAN -> R.string.language_uk
    AppLanguage.ENGLISH -> R.string.language_en
}

/** Підпис теми береться з ресурсів — тому міняється разом з мовою. */
@StringRes
private fun ThemeMode.titleRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/**
 * Екран налаштувань застосунку (ТЗ 6.3.1 / 6.3.7): мова інтерфейсу,
 * тема та акаунт. Обидва перемикачі діють негайно — без перезапуску:
 * стан читається з [AppSettingsStore], тому ProvideLumiLocalization
 * і LumiTheme перемальовуються одразу.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val language by AppSettingsStore.language.collectAsState()
    val themeMode by AppSettingsStore.themeMode.collectAsState()
    val selectedStateLabel = stringResource(R.string.settings_selected_state)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AuthBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LumiTopBar(title = stringResource(R.string.settings_topbar), onBack = onBack)

                Text(
                    text = stringResource(R.string.settings_title),
                    fontFamily = Montserrat,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    fontFamily = Montserrat,
                    color = AuthTextColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                SectionTitle(stringResource(R.string.settings_section_language))
                AppLanguage.selectable.forEach { option ->
                    SelectOptionRow(
                        title = stringResource(option.titleRes()),
                        selected = option == language,
                        selectedStateLabel = selectedStateLabel,
                        onSelect = { AppSettingsStore.setLanguage(option) }
                    )
                }

                SectionTitle(stringResource(R.string.settings_section_theme))
                ThemeMode.selectable.forEach { option ->
                    SelectOptionRow(
                        title = stringResource(option.titleRes()),
                        selected = option == themeMode,
                        selectedStateLabel = selectedStateLabel,
                        onSelect = { AppSettingsStore.setThemeMode(option) }
                    )
                }

                SectionTitle(stringResource(R.string.settings_section_account))
                LumiSecondaryButton(
                    text = stringResource(R.string.settings_clear_cache),
                    onClick = {
                        scope.launch {
                            val cleared = AppSettingsStore.clearLocalCache()
                            val message = if (cleared) {
                                R.string.settings_cache_cleared
                            } else {
                                R.string.settings_cache_failed
                            }
                            // Snackbar перекладається в момент показу — обраною мовою.
                            snackbarHostState.showSnackbar(uiText(message).resolveCurrent(context))
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                LumiSecondaryButton(
                    text = stringResource(R.string.settings_logout),
                    onClick = onLogout
                )

                Spacer(Modifier.height(32.dp))
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

/**
 * Рядок вибору одного з варіантів (мова або тема).
 *
 * Рядок — одна ціль для TalkBack із роллю RadioButton (ТЗ 6.3.7):
 * підпис озвучується один раз, стан — «Обрано» через stateDescription,
 * зона дотику — щонайменше 48dp.
 */
@Composable
private fun SelectOptionRow(
    title: String,
    selected: Boolean,
    selectedStateLabel: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .semantics(mergeDescendants = true) {
                contentDescription = title
                if (selected) {
                    stateDescription = selectedStateLabel
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontFamily = Montserrat,
            color = if (selected) AuthTextColor else AuthPlaceholderColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { }
        )

        Spacer(Modifier.width(12.dp))

        // Радіо-індикатор: коло з рамкою і крапка всередині — як у макетах.
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = if (selected) AuthTextColor else AuthPlaceholderColor,
                    shape = CircleShape
                )
                .padding(4.dp)
                .background(
                    color = if (selected) AuthTextColor else Color.Transparent,
                    shape = CircleShape
                )
        )
    }
}
