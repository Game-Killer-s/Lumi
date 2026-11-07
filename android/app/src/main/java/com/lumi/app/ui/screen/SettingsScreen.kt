package com.lumi.app.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumi.app.R
import com.lumi.app.data.repository.SubscriptionRepository
import com.lumi.app.data.repository.UserRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.network.TokenManager
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.AppLanguage
import com.lumi.app.ui.i18n.AppSettingsStore
import com.lumi.app.ui.i18n.resolveCurrent
import com.lumi.app.ui.i18n.uiText
import com.lumi.app.ui.profile.ProfileAccent
import com.lumi.app.ui.profile.ProfileBackground
import com.lumi.app.ui.profile.ProfileCard
import com.lumi.app.ui.profile.ProfileCardBorder
import com.lumi.app.ui.profile.ProfileChoiceRow
import com.lumi.app.ui.profile.ProfileError
import com.lumi.app.ui.profile.ProfilePrimaryButton
import com.lumi.app.ui.profile.ProfileSettingsRow
import com.lumi.app.ui.profile.ProfileSwitchRow
import com.lumi.app.ui.profile.ProfileTextPrimary
import com.lumi.app.ui.profile.ProfileTextSecondary
import com.lumi.app.ui.profile.ProfileTopBar
import com.lumi.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private enum class SettingsDialog {
    LANGUAGE,
    THEME,
    NOTIFICATIONS,
    PRIVACY,
    SOUND,
    DATA_SAVER,
    CACHE,
    ACCOUNT,
    LOGOUT
}

private const val QUALITY_STANDARD = "STANDARD"
private const val QUALITY_HQ = "HQ"
private const val QUALITY_LOSSLESS = "LOSSLESS"

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
 * Екран налаштувань застосунку (ТЗ 6.3.1 / 6.3.7) у стилі Figma:
 * картки з рядками, діалоги вибору мови/теми/приватності та підтвердження
 * очищення кешу й виходу з акаунта.
 *
 * Мова й тема діють негайно: стан читається з [AppSettingsStore], тому
 * ProvideLumiLocalization і LumiTheme перемальовуються без перезапуску.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val language by AppSettingsStore.language.collectAsState()
    val themeMode by AppSettingsStore.themeMode.collectAsState()
    val dataSaver by AppSettingsStore.dataSaver.collectAsState()
    val notifications by AppSettingsStore.notifications.collectAsState()
    val audioQuality by AppSettingsStore.audioQuality.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val userRepository = remember { UserRepository() }
    val subscriptionRepository = remember { SubscriptionRepository() }

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var profile by remember { mutableStateOf(TokenManager.getCurrentUser()) }
    var premiumActive by remember { mutableStateOf(false) }
    var privacyPublic by remember { mutableStateOf(profile?.isPublic ?: true) }
    var privacyListening by remember { mutableStateOf(profile?.showCurrentListening ?: true) }
    var privacySaving by remember { mutableStateOf(false) }
    var privacyError by remember { mutableStateOf(false) }
    val isLoggedIn = remember { TokenManager.isLoggedIn() }

    LaunchedEffect(Unit) {
        if (!TokenManager.isLoggedIn()) return@LaunchedEffect

        when (val result = userRepository.getProfile()) {
            is ApiResult.Success -> {
                profile = result.data
                privacyPublic = result.data.isPublic
                privacyListening = result.data.showCurrentListening
                TokenManager.updateCurrentUser(result.data)
            }

            is ApiResult.Error -> Unit
        }

        when (val status = subscriptionRepository.getStatus()) {
            is ApiResult.Success -> premiumActive = status.data.hasSubscription &&
                status.data.status.uppercase() in setOf("ACTIVE", "TRIALING", "GRACE")

            is ApiResult.Error -> premiumActive = false
        }
    }

    val qualityLabel = when (audioQuality) {
        QUALITY_HQ -> stringResource(R.string.settings_sound_hq)
        QUALITY_LOSSLESS -> stringResource(R.string.settings_sound_lossless)
        else -> stringResource(R.string.settings_value_standard)
    }
    val enabledLabel = stringResource(R.string.settings_value_on)
    val disabledLabel = stringResource(R.string.settings_value_off)
    val guestLabel = stringResource(R.string.settings_account_guest)

    val updatePrivacy: (Boolean, Boolean) -> Unit = { nextPublic, nextListening ->
        privacySaving = true
        privacyError = false
        scope.launch {
            when (
                val result = userRepository.updatePrivacy(
                    isPublic = nextPublic,
                    showCurrentListening = nextListening
                )
            ) {
                is ApiResult.Success -> {
                    profile = result.data
                    privacyPublic = result.data.isPublic
                    privacyListening = result.data.showCurrentListening
                    TokenManager.updateCurrentUser(result.data)
                }

                is ApiResult.Error -> {
                    privacyError = true
                    // Повертаємо перемикачі до стану на сервері.
                    profile?.let { saved ->
                        privacyPublic = saved.isPublic
                        privacyListening = saved.showCurrentListening
                    }
                }
            }
            privacySaving = false
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            ProfileTopBar(title = stringResource(R.string.settings_topbar), onBack = onBack)

            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_title),
                fontFamily = Montserrat,
                color = ProfileTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_subtitle),
                fontFamily = Montserrat,
                color = ProfileTextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(22.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_general))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                ProfileSettingsRow(
                    title = stringResource(R.string.settings_row_language),
                    icon = Icons.Outlined.Language,
                    value = stringResource(language.titleRes()),
                    onClick = { dialog = SettingsDialog.LANGUAGE }
                )
                SettingsDivider()
                ProfileSettingsRow(
                    title = stringResource(R.string.settings_row_theme),
                    icon = Icons.Outlined.DarkMode,
                    value = stringResource(themeMode.titleRes()),
                    onClick = { dialog = SettingsDialog.THEME }
                )
                SettingsDivider()
                ProfileSettingsRow(
                    title = stringResource(R.string.settings_row_notifications),
                    icon = Icons.Outlined.NotificationsNone,
                    value = if (notifications) enabledLabel else disabledLabel,
                    onClick = { dialog = SettingsDialog.NOTIFICATIONS }
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_playback))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                ProfileSettingsRow(
                    title = stringResource(R.string.settings_row_privacy),
                    icon = Icons.Outlined.Lock,
                    value = stringResource(
                        if (privacyPublic) {
                            R.string.settings_value_public
                        } else {
                            R.string.settings_value_private
                        }
                    ),
                    onClick = { dialog = SettingsDialog.PRIVACY }
                )
                SettingsDivider()
                ProfileSettingsRow(
                    title = stringResource(R.string.settings_row_sound),
                    icon = Icons.Outlined.Headphones,
                    value = qualityLabel,
                    onClick = { dialog = SettingsDialog.SOUND }
                )
                SettingsDivider()
                ProfileSettingsRow(
                    title = stringResource(R.string.settings_row_data_saver),
                    icon = Icons.Outlined.DataSaverOn,
                    value = if (dataSaver) enabledLabel else disabledLabel,
                    onClick = { dialog = SettingsDialog.DATA_SAVER }
                )
                SettingsDivider()
                ProfileSettingsRow(
                    title = stringResource(R.string.settings_row_cache),
                    icon = Icons.Outlined.DeleteOutline,
                    value = stringResource(R.string.settings_value_manage),
                    onClick = { dialog = SettingsDialog.CACHE }
                )
            }


            Spacer(Modifier.height(20.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_account))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                ProfileSettingsRow(
                    title = stringResource(R.string.settings_row_account),
                    icon = Icons.Outlined.PersonOutline,
                    value = profile?.nickname ?: guestLabel,
                    onClick = { dialog = SettingsDialog.ACCOUNT }
                )
            }

            Spacer(Modifier.height(26.dp))
            SettingsDangerButton(
                text = stringResource(R.string.settings_logout),
                icon = Icons.Outlined.Logout,
                onClick = { dialog = SettingsDialog.LOGOUT }
            )
            Spacer(Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

    when (dialog) {
        SettingsDialog.LANGUAGE -> SettingsDialogFrame(
            title = stringResource(R.string.settings_dialog_language_title),
            onDismiss = { dialog = null }
        ) {
            AppLanguage.selectable.forEach { option ->
                ProfileChoiceRow(
                    label = stringResource(option.titleRes()),
                    selected = option == language,
                    onClick = { AppSettingsStore.setLanguage(option) }
                )
            }
        }

        SettingsDialog.THEME -> SettingsDialogFrame(
            title = stringResource(R.string.settings_dialog_theme_title),
            onDismiss = { dialog = null }
        ) {
            ThemeMode.selectable.forEach { option ->
                ProfileChoiceRow(
                    label = stringResource(option.titleRes()),
                    selected = option == themeMode,
                    onClick = { AppSettingsStore.setThemeMode(option) }
                )
            }
        }

        SettingsDialog.NOTIFICATIONS -> SettingsDialogFrame(
            title = stringResource(R.string.settings_dialog_notifications_title),
            onDismiss = { dialog = null }
        ) {
            ProfileSwitchRow(
                title = stringResource(R.string.settings_notifications_label),
                description = stringResource(R.string.settings_notifications_desc),
                checked = notifications,
                onCheckedChange = { AppSettingsStore.setNotifications(it) }
            )
        }

        SettingsDialog.DATA_SAVER -> SettingsDialogFrame(
            title = stringResource(R.string.settings_dialog_data_saver_title),
            onDismiss = { dialog = null }
        ) {
            ProfileSwitchRow(
                title = stringResource(R.string.settings_data_saver_label),
                description = stringResource(R.string.settings_data_saver_desc),
                checked = dataSaver,
                onCheckedChange = { AppSettingsStore.setDataSaver(it) }
            )
        }


        SettingsDialog.SOUND -> SettingsDialogFrame(
            title = stringResource(R.string.settings_dialog_sound_title),
            onDismiss = { dialog = null }
        ) {
            Text(
                text = stringResource(R.string.settings_sound_quality),
                fontFamily = Montserrat,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProfileTextPrimary
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.settings_sound_quality_desc),
                fontFamily = Montserrat,
                fontSize = 10.sp,
                color = ProfileTextSecondary
            )
            Spacer(Modifier.height(8.dp))
            ProfileChoiceRow(
                label = stringResource(R.string.settings_value_standard),
                selected = audioQuality == QUALITY_STANDARD,
                onClick = { AppSettingsStore.setAudioQuality(QUALITY_STANDARD) }
            )
            ProfileChoiceRow(
                label = stringResource(R.string.settings_sound_hq),
                selected = audioQuality == QUALITY_HQ,
                onClick = {
                    if (premiumActive) AppSettingsStore.setAudioQuality(QUALITY_HQ)
                }
            )
            ProfileChoiceRow(
                label = stringResource(R.string.settings_sound_lossless),
                selected = audioQuality == QUALITY_LOSSLESS,
                onClick = {
                    if (premiumActive) AppSettingsStore.setAudioQuality(QUALITY_LOSSLESS)
                }
            )
            if (!premiumActive) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_sound_plan_note),
                    fontFamily = Montserrat,
                    fontSize = 10.sp,
                    color = ProfileAccent
                )
            }
        }

        SettingsDialog.PRIVACY -> SettingsDialogFrame(
            title = stringResource(R.string.settings_dialog_privacy_title),
            onDismiss = { dialog = null }
        ) {
            if (!isLoggedIn && profile == null) {
                Text(
                    text = stringResource(R.string.settings_privacy_guest),
                    fontFamily = Montserrat,
                    fontSize = 11.sp,
                    color = ProfileTextSecondary
                )
            } else {
                ProfileSwitchRow(
                    title = stringResource(R.string.settings_privacy_public),
                    description = stringResource(R.string.settings_privacy_public_desc),
                    checked = privacyPublic,
                    enabled = !privacySaving,
                    onCheckedChange = { updatePrivacy(it, privacyListening) }
                )
                ProfileSwitchRow(
                    title = stringResource(R.string.settings_privacy_listening),
                    description = stringResource(R.string.settings_privacy_listening_desc),
                    checked = privacyListening,
                    enabled = !privacySaving,
                    onCheckedChange = { updatePrivacy(privacyPublic, it) }
                )
                if (privacySaving) {
                    Spacer(Modifier.height(6.dp))
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(20.dp),
                        color = ProfileAccent,
                        strokeWidth = 2.dp
                    )
                }
                if (privacyError) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_privacy_error),
                        fontFamily = Montserrat,
                        fontSize = 10.sp,
                        color = ProfileError
                    )
                }
            }
        }

        SettingsDialog.CACHE -> SettingsDialogFrame(
            title = stringResource(R.string.settings_dialog_cache_title),
            onDismiss = { dialog = null }
        ) {
            Text(
                text = stringResource(R.string.settings_cache_dialog_text),
                fontFamily = Montserrat,
                fontSize = 11.sp,
                color = ProfileTextSecondary
            )
            Spacer(Modifier.height(14.dp))
            SettingsDangerButton(
                text = stringResource(R.string.settings_dialog_clear),
                onClick = {
                    dialog = null
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
        }


        SettingsDialog.ACCOUNT -> SettingsDialogFrame(
            title = stringResource(R.string.settings_dialog_account_title),
            onDismiss = { dialog = null }
        ) {
            val account = profile
            Text(
                text = if (account != null) {
                    stringResource(R.string.settings_account_signed_in, account.email)
                } else {
                    stringResource(R.string.settings_account_guest)
                },
                fontFamily = Montserrat,
                fontSize = 12.sp,
                color = ProfileTextSecondary
            )
            Spacer(Modifier.height(16.dp))
            if (account != null) {
                ProfilePrimaryButton(
                    text = stringResource(R.string.settings_account_edit),
                    onClick = {
                        dialog = null
                        onOpenProfile()
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
            SettingsDangerButton(
                text = stringResource(R.string.settings_logout),
                icon = Icons.Outlined.Logout,
                onClick = { dialog = SettingsDialog.LOGOUT }
            )
        }

        SettingsDialog.LOGOUT -> SettingsDialogFrame(
            title = stringResource(R.string.settings_dialog_logout_title),
            onDismiss = { dialog = null }
        ) {
            Text(
                text = stringResource(R.string.settings_logout_dialog_text),
                fontFamily = Montserrat,
                fontSize = 11.sp,
                color = ProfileTextSecondary
            )
            Spacer(Modifier.height(16.dp))
            SettingsDangerButton(
                text = stringResource(R.string.settings_logout),
                icon = Icons.Outlined.Logout,
                onClick = {
                    dialog = null
                    onLogout()
                }
            )
        }

        null -> Unit
    }
}

/** Заголовок групи налаштувань. */
@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        fontFamily = Montserrat,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = ProfileTextSecondary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/** Картка з рядками налаштувань у стилі макета. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProfileCard)
            .border(1.dp, ProfileCardBorder, RoundedCornerShape(14.dp)),
        content = content
    )
}

/** Тонкий розділювач між рядками картки. */
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 15.dp),
        thickness = 1.dp,
        color = ProfileCardBorder.copy(alpha = 0.55f)
    )
}


/** Загальна рамка діалогів налаштувань: заголовок, вміст і кнопка «Готово». */
@Composable
private fun SettingsDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ProfileCard)
                .border(1.dp, ProfileCardBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontFamily = Montserrat,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ProfileTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.width(40.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.settings_dialog_close),
                        tint = ProfileTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            content()
            Spacer(Modifier.height(18.dp))
            ProfilePrimaryButton(
                text = stringResource(R.string.settings_dialog_done),
                onClick = onDismiss
            )
        }
    }
}

/** Контурна кнопка небезпечної дії (вихід, очищення кешу). */
@Composable
private fun SettingsDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(ProfileError.copy(alpha = 0.12f))
            .border(1.dp, ProfileError.copy(alpha = 0.65f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ProfileError,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontFamily = Montserrat,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ProfileError
        )
    }
}

