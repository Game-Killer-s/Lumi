package com.lumi.app.ui.screen

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.app.R
import com.lumi.app.data.local.ProfilePreferences
import com.lumi.app.data.model.SubscriptionStatusResponse
import com.lumi.app.data.model.UserProfile
import com.lumi.app.data.repository.AuthRepository
import com.lumi.app.data.repository.SubscriptionRepository
import com.lumi.app.data.repository.UserRepository
import com.lumi.app.network.ApiError
import com.lumi.app.network.ApiResult
import com.lumi.app.network.TokenManager
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.LumiFormatters
import com.lumi.app.ui.i18n.currentAppLocale
import com.lumi.app.ui.profile.ProfileAccent
import com.lumi.app.ui.profile.ProfileAvatar
import com.lumi.app.ui.profile.ProfileBackground
import com.lumi.app.ui.profile.ProfileCard
import com.lumi.app.ui.profile.ProfileCardBorder
import com.lumi.app.ui.profile.ProfileEditRules
import com.lumi.app.ui.profile.ProfileField
import com.lumi.app.ui.profile.ProfileMenuAction
import com.lumi.app.ui.profile.ProfileMenuCard
import com.lumi.app.ui.profile.ProfileMessageCard
import com.lumi.app.ui.profile.ProfilePremiumCard
import com.lumi.app.ui.profile.ProfilePrimaryButton
import com.lumi.app.ui.profile.ProfileTextPrimary
import com.lumi.app.ui.profile.ProfileTextSecondary
import com.lumi.app.ui.profile.ProfileTextField
import com.lumi.app.ui.profile.ProfileTopBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Ready(val profile: UserProfile) : ProfileUiState
    data class Failed(val error: ApiError) : ProfileUiState
}

private const val MAX_AVATAR_BYTES = 5L * 1024L * 1024L

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenSupportChat: () -> Unit
) {
    val repository = remember { UserRepository() }
    val authRepository = remember { AuthRepository() }
    val subscriptionRepository = remember { SubscriptionRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val cachedUser = remember { TokenManager.getCurrentUser() }
    val isLoggedIn = remember { TokenManager.isLoggedIn() }

    var state by remember {
        mutableStateOf<ProfileUiState>(
            cachedUser?.let { ProfileUiState.Ready(it) } ?: ProfileUiState.Loading
        )
    }
    var subscription by remember { mutableStateOf<SubscriptionStatusResponse?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var avatarUploading by remember { mutableStateOf(false) }

    val notify: (Int) -> Unit = { messageRes ->
        scope.launch { snackbarHostState.showSnackbar(context.getString(messageRes)) }
    }

    LaunchedEffect(reloadKey) {
        if (!isLoggedIn && cachedUser == null) {
            state = ProfileUiState.Failed(ApiError.UNAUTHORIZED)
            return@LaunchedEffect
        }

        if (state !is ProfileUiState.Ready) state = ProfileUiState.Loading

        when (val result = repository.getProfile()) {
            is ApiResult.Success -> {
                TokenManager.updateCurrentUser(result.data)
                state = ProfileUiState.Ready(result.data)
            }

            is ApiResult.Error -> {
                val fallback = TokenManager.getCurrentUser()
                if (fallback != null) {
                    state = ProfileUiState.Ready(fallback)
                    notify(R.string.profile_cached_notice)
                } else {
                    state = ProfileUiState.Failed(result.error)
                }
            }
        }
    }

    LaunchedEffect((state as? ProfileUiState.Ready)?.profile?.id) {
        if (!isLoggedIn) {
            subscription = null
            return@LaunchedEffect
        }
        when (val result = subscriptionRepository.getStatus()) {
            is ApiResult.Success -> subscription = result.data
            is ApiResult.Error -> Unit // Картка просто покаже базовий стан.
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            avatarUploading = true
            try {
                val prepared = withContext(Dispatchers.IO) {
                    prepareAvatar(context.contentResolver, uri)
                }
                if (prepared == null) {
                    notify(R.string.profile_avatar_invalid)
                    return@launch
                }

                val file = prepared.file
                try {
                    when (val result = repository.uploadAvatar(file, prepared.mimeType)) {
                        is ApiResult.Success -> {
                            state = ProfileUiState.Ready(result.data)
                            notify(R.string.profile_avatar_updated)
                        }

                        is ApiResult.Error -> notify(R.string.profile_avatar_error)
                    }
                } finally {
                    file.delete()
                }
            } finally {
                avatarUploading = false
            }
        }
    }

    val logout: () -> Unit = {
        scope.launch { authRepository.logout() }
        onLoggedOut()
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileBackground)
    ) {
        when (val current = state) {
            is ProfileUiState.Loading -> ProfileLoading(
                onBack = onBack,
                modifier = Modifier.fillMaxSize()
            )

            is ProfileUiState.Failed -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                ProfileTopBar(title = stringResource(R.string.profile_title), onBack = onBack)
                Spacer(Modifier.height(12.dp))
                ProfileMessageCard(
                    title = stringResource(
                        if (current.error.statusCode == 401) {
                            R.string.profile_error_session_title
                        } else {
                            R.string.profile_error_title
                        }
                    ),
                    message = stringResource(
                        if (current.error.statusCode == 401) {
                            R.string.profile_error_session_message
                        } else {
                            R.string.profile_error_message
                        }
                    ),
                    action = stringResource(
                        if (current.error.statusCode == 401) {
                            R.string.profile_login
                        } else {
                            R.string.profile_retry
                        }
                    ),
                    onAction = {
                        if (current.error.statusCode == 401) logout() else reloadKey++
                    }
                )
            }

            is ProfileUiState.Ready -> {
                val user = current.profile
                if (editing) {
                    EditProfileContent(
                        user = user,
                        avatarUploading = avatarUploading,
                        onPickAvatar = { avatarPicker.launch("image/*") },
                        onCancel = { editing = false },
                        onSaved = { updated ->
                            state = ProfileUiState.Ready(updated)
                            editing = false
                            notify(R.string.profile_update_success)
                        },
                        onSave = { nickname, email ->
                            repository.updateProfile(username = nickname, email = email)
                        },
                        onNotice = { messageRes -> notify(messageRes) }
                    )
                } else {
                    ProfileContent(
                        user = user,
                        subscription = subscription,
                        avatarUploading = avatarUploading,
                        onEdit = { editing = true },
                        onOpenStatistics = onOpenStatistics,
                        onOpenSubscription = onOpenSubscription,
                        onOpenSupportChat = onOpenSupportChat,
                        onOpenSettings = onOpenSettings
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}



@Composable
private fun ProfileContent(
    user: UserProfile,
    subscription: SubscriptionStatusResponse?,
    avatarUploading: Boolean,
    onEdit: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenSupportChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val locale = currentAppLocale()
    val premiumActive = subscription?.hasSubscription == true &&
        subscription.status.uppercase() in setOf("ACTIVE", "TRIALING", "GRACE")
    val chargeDate = LumiFormatters.formatDate(
        subscription?.nextChargeDate ?: subscription?.currentPeriodEnd,
        locale
    )
    val premiumCaption = if (premiumActive && chargeDate != null) {
        stringResource(R.string.profile_premium_next_charge, chargeDate)
    } else {
        stringResource(R.string.profile_premium_caption)
    }
    val premiumDetail = subscription?.plan?.let { plan ->
        "${plan.title} • ${LumiFormatters.formatMoney(plan.priceCents, locale)}"
    } ?: stringResource(
        if (premiumActive) R.string.profile_premium_active else R.string.profile_premium_free
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                fontFamily = Montserrat,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProfileTextPrimary
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ProfileCard)
                    .border(1.dp, ProfileCardBorder, CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.profile_edit_action),
                    tint = ProfileAccent,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                ProfileAvatar(
                    avatarUrl = user.avatarUrl,
                    name = user.nickname,
                    size = 94.dp,
                    uploading = avatarUploading,
                    onClick = onEdit
                )
                AvatarCameraBadge(
                    onClick = onEdit,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = user.nickname,
                fontFamily = Montserrat,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProfileTextPrimary
            )
            if (user.email.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = user.email,
                    fontFamily = Montserrat,
                    fontSize = 11.sp,
                    color = ProfileTextSecondary
                )
            }

            Spacer(Modifier.height(8.dp))
            ProfileRoleBadge(role = user.role)
        }

        Spacer(Modifier.height(24.dp))

        ProfileMenuCard(
            actions = listOf(
                ProfileMenuAction(
                    icon = Icons.Outlined.Headphones,
                    label = stringResource(R.string.profile_menu_statistics),
                    onClick = onOpenStatistics
                ),
                ProfileMenuAction(
                    icon = Icons.Outlined.CreditCard,
                    label = stringResource(R.string.profile_menu_subscription),
                    onClick = onOpenSubscription
                ),
                ProfileMenuAction(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = stringResource(R.string.profile_menu_support),
                    onClick = onOpenSupportChat
                ),
                ProfileMenuAction(
                    icon = Icons.Outlined.Settings,
                    label = stringResource(R.string.profile_menu_settings),
                    onClick = onOpenSettings
                )
            )
        )

        Spacer(Modifier.height(16.dp))

        ProfilePremiumCard(
            title = stringResource(R.string.profile_premium_title),
            badge = stringResource(
                if (premiumActive) {
                    R.string.profile_premium_active
                } else {
                    R.string.profile_premium_inactive
                }
            ),
            caption = premiumCaption,
            detail = premiumDetail,
            active = premiumActive,
            onClick = onOpenSubscription
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileRoleBadge(role: String, modifier: Modifier = Modifier) {
    val label = stringResource(
        when (role.uppercase()) {
            "ARTIST" -> R.string.profile_role_artist
            "ADMIN" -> R.string.profile_role_admin
            else -> R.string.profile_role_listener
        }
    )

    Text(
        text = label,
        fontFamily = Montserrat,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = ProfileAccent,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(ProfileCard)
            .border(1.dp, ProfileCardBorder, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 4.dp)
    )
}

@Composable
private fun AvatarCameraBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(29.dp)
            .clip(CircleShape)
            .background(ProfileAccent)
            .border(2.dp, ProfileBackground, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = stringResource(R.string.profile_avatar_change),
            tint = ProfileBackground,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun EditProfileContent(
    user: UserProfile,
    avatarUploading: Boolean,
    onPickAvatar: () -> Unit,
    onCancel: () -> Unit,
    onSaved: (UserProfile) -> Unit,
    onSave: suspend (nickname: String?, email: String?) -> ApiResult<UserProfile>,
    onNotice: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Ключ — увесь профіль, а не лише id: якщо дані на сервері змінилися
    // (GET після збереження), поля мають показати актуальні значення.
    var name by remember(user) { mutableStateOf(user.nickname) }
    var email by remember(user) { mutableStateOf(user.email) }
    var phone by remember(user.id) { mutableStateOf(ProfilePreferences.getPhone(user.id)) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val nameRequired = stringResource(R.string.profile_edit_name_required)
    val nameTooLong = stringResource(R.string.profile_edit_name_too_long)
    val emailRequired = stringResource(R.string.profile_edit_email_required)
    val emailInvalid = stringResource(R.string.profile_edit_email_invalid)
    val phoneInvalid = stringResource(R.string.profile_edit_phone_invalid)

    val validate: () -> Boolean = {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val digits = phone.count { it.isDigit() }

        nameError = when {
            trimmedName.isEmpty() -> nameRequired
            trimmedName.length > ProfileEditRules.NAME_MAX_LENGTH -> nameTooLong
            else -> null
        }
        emailError = when {
            trimmedEmail.isEmpty() -> emailRequired
            !EMAIL_REGEX.matches(trimmedEmail) -> emailInvalid
            else -> null
        }
        phoneError = if (
            phone.isNotBlank() &&
            (digits !in 10..15 || !PHONE_REGEX.matches(phone.trim()))
        ) {
            phoneInvalid
        } else {
            null
        }

        nameError == null && emailError == null && phoneError == null
    }

    val submit: () -> Unit = {
        if (!saving && validate()) {
            scope.launch {
                saving = true
                val changes = ProfileEditRules.changes(
                    currentNickname = user.nickname,
                    currentEmail = user.email,
                    typedName = name,
                    typedEmail = email
                )

                try {
                    if (changes.isEmpty) {
                        val phoneChanged = phone.trim() != ProfilePreferences.getPhone(user.id)
                        if (phoneChanged) {
                            ProfilePreferences.setPhone(user.id, phone)
                            onNotice(R.string.profile_update_success)
                        } else {
                            onNotice(R.string.profile_edit_unchanged)
                        }
                        return@launch
                    }

                    /*
                     * Надсилаємо лише змінені поля: незмінене імʼя більше
                     * не може спричинити 409 «імʼя вже зайняте» і збереження
                     * email/інших даних не блокується.
                     */
                    when (val result = onSave(changes.nickname, changes.email)) {
                        is ApiResult.Success -> {
                            ProfilePreferences.setPhone(user.id, phone)
                            onSaved(result.data)
                        }

                        is ApiResult.Error -> {
                            val failure = ProfileEditRules.failure(result.error)
                            when (failure.field) {
                                ProfileField.NICKNAME ->
                                    nameError = context.getString(failure.messageRes)
                                ProfileField.EMAIL ->
                                    emailError = context.getString(failure.messageRes)
                                null -> onNotice(failure.messageRes)
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Непередбачений збій не має «тихо» ламати збереження.
                    onNotice(R.string.profile_update_error)
                } finally {
                    saving = false
                }
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        ProfileTopBar(title = stringResource(R.string.profile_edit_title), onBack = onCancel)

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                ProfileAvatar(
                    avatarUrl = user.avatarUrl,
                    name = user.nickname,
                    size = 88.dp,
                    uploading = avatarUploading,
                    onClick = onPickAvatar
                )
                AvatarCameraBadge(
                    onClick = onPickAvatar,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onPickAvatar, enabled = !avatarUploading) {
                Text(
                    text = stringResource(R.string.profile_avatar_change),
                    fontFamily = Montserrat,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ProfileAccent
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.profile_edit_section),
            fontFamily = Montserrat,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ProfileTextPrimary
        )

        Spacer(Modifier.height(12.dp))

        ProfileTextField(
            label = stringResource(R.string.profile_edit_name),
            value = name,
            onValueChange = {
                name = it
                nameError = null
            },
            placeholder = user.nickname,
            error = nameError,
            enabled = !saving,
            contentType = ContentType.PersonFullName
        )

        Spacer(Modifier.height(14.dp))

        ProfileTextField(
            label = stringResource(R.string.profile_edit_email),
            value = email,
            onValueChange = {
                email = it
                emailError = null
            },
            placeholder = user.email,
            error = emailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !saving,
            contentType = ContentType.EmailAddress
        )

        Spacer(Modifier.height(14.dp))

        ProfileTextField(
            label = stringResource(R.string.profile_edit_phone),
            value = phone,
            onValueChange = {
                phone = it
                phoneError = null
            },
            placeholder = stringResource(R.string.profile_edit_phone_hint),
            error = phoneError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            enabled = !saving,
            contentType = ContentType.PhoneNumber
        )

        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.profile_edit_phone_note),
            fontFamily = Montserrat,
            fontSize = 9.sp,
            color = ProfileTextSecondary
        )

        Spacer(Modifier.height(26.dp))

        ProfilePrimaryButton(
            text = stringResource(R.string.profile_edit_save),
            onClick = submit,
            loading = saving
        )
    }
}

@Composable
private fun ProfileLoading(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp)
    ) {
        ProfileTopBar(title = stringResource(R.string.profile_title), onBack = onBack)
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = ProfileAccent,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.profile_loading),
                fontFamily = Montserrat,
                fontSize = 11.sp,
                color = ProfileTextSecondary
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

private data class PreparedAvatar(val file: File, val mimeType: String)

/**
 * Копіює вибране зображення у тимчасовий файл: Multipart вимагає
 * реальний файл, а content://-uri може стати недоступним під час запиту.
 */
private fun prepareAvatar(resolver: ContentResolver, uri: Uri): PreparedAvatar? {
    val mimeType = resolver.getType(uri) ?: "image/jpeg"
    if (!mimeType.startsWith("image/")) return null

    val extension = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }
    val file = File.createTempFile("lumi_avatar_", ".$extension")

    val copied = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
    }.getOrDefault(false)

    if (!copied || file.length() == 0L || file.length() > MAX_AVATAR_BYTES) {
        file.delete()
        return null
    }

    return PreparedAvatar(file = file, mimeType = mimeType)
}

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val PHONE_REGEX = Regex("^\\+?[0-9 ()\\-]+$")

