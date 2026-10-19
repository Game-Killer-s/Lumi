package com.lumi.app.ui.profile

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.app.network.ApiConstants
import com.lumi.app.ui.auth.Montserrat

// === Кольори з Figma (профіль / налаштування) ===
val ProfileBackground = Color(0xFF190E38)
val ProfileBottomBar = Color(0xFF1A0D35)
val ProfileCard = Color(0xFF2A124B)
val ProfileCardBorder = Color(0xFF4B2C78)
val ProfileTextPrimary = Color(0xFFF0E9FF)
val ProfileTextSecondary = Color(0xFF9A86C2)
val ProfileAccent = Color(0xFFD9C7FA)
val ProfileFieldBackground = Color(0xFF2A154A)
val ProfileFieldBorder = Color(0xFF6B4A9A)
val ProfileError = Color(0xFFD9464F)
val ProfileSuccess = Color(0xFF20A33D)

private val PremiumGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF3A1D60), Color(0xFF2A1248))
)

private val SaveButtonGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFD9C8F3), Color(0xFFB294D6))
)

/**
 * Верхня панель внутрішніх екранів профілю.
 * Окрема від LumiTopBar, бо в макеті стрілка й заголовок
 * вирівняні по лівому краю без підпису «Назад».
 */
@Composable
fun ProfileTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = ProfileAccent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            fontFamily = Montserrat,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = ProfileTextPrimary
        )
    }
}

/**
 * Круглий аватар. Якщо фото немає — показує першу літеру імені,
 * якщо немає й імені — нейтральну іконку.
 */
@Composable
fun ProfileAvatar(
    avatarUrl: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    uploading: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = CircleShape
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    val imageUrl = ApiConstants.mediaUrl(avatarUrl)

    Box(
        modifier = modifier
            .size(size)
            .then(clickModifier)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF4A2A70), Color(0xFF2C164C))
                )
            )
            .border(1.dp, ProfileCardBorder.copy(alpha = 0.7f), shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            !imageUrl.isNullOrBlank() -> AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            name.isNotBlank() -> Text(
                text = name.trim().take(1).uppercase(),
                fontFamily = Montserrat,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.34f).sp,
                color = ProfileAccent
            )

            else -> Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = ProfileAccent.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxSize(0.34f)
            )
        }

        if (uploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = ProfileAccent,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

data class ProfileMenuAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

/** Картка меню профілю: чотири рядки з тонкими розділювачами. */
@Composable
fun ProfileMenuCard(
    actions: List<ProfileMenuAction>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ProfileCard)
            .border(1.dp, ProfileCardBorder, RoundedCornerShape(12.dp))
    ) {
        actions.forEachIndexed { index, action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = action.onClick)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = ProfileTextPrimary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = action.label,
                    fontFamily = Montserrat,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ProfileTextPrimary
                )
            }

            if (index != actions.lastIndex) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = ProfileCardBorder.copy(alpha = 0.75f)
                )
            }
        }
    }
}


/** Premium-картка на головному екрані профілю. */
@Composable
fun ProfilePremiumCard(
    title: String,
    badge: String,
    caption: String,
    detail: String,
    active: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .clip(RoundedCornerShape(14.dp))
            .background(PremiumGradient)
            .border(1.dp, ProfileCardBorder.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 17.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.WorkspacePremium,
                contentDescription = null,
                tint = ProfileAccent,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = title,
                fontFamily = Montserrat,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProfileTextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = badge,
                fontFamily = Montserrat,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) ProfileSuccess else Color(0xFF5B3B86))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(13.dp))
        Text(
            text = caption,
            fontFamily = Montserrat,
            fontSize = 10.sp,
            color = ProfileTextSecondary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            fontFamily = Montserrat,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = ProfileTextPrimary.copy(alpha = 0.78f)
        )
    }
}

/** Поле введення у стилі Figma: підпис зверху, рамка і повідомлення про помилку. */
@Composable
fun ProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    /**
     * Підказка Autofill (ContentType). Без неї служби автозаповнення
     * вгадують призначення поля евристично й можуть підставити логін/пароль
     * у поле імені. null — поле не бере участі в автозаповненні.
     */
    contentType: ContentType? = null
) {
    val shape = RoundedCornerShape(9.dp)
    val borderColor = if (error != null) ProfileError else ProfileFieldBorder

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = Montserrat,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = ProfileTextSecondary
        )
        Spacer(Modifier.height(6.dp))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(ProfileAccent),
            textStyle = TextStyle(
                fontFamily = Montserrat,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ProfileTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (contentType != null) Modifier.contentType(contentType) else Modifier
                ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(49.dp)
                        .clip(shape)
                        .background(ProfileFieldBackground)
                        .border(1.dp, borderColor, shape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = Montserrat,
                            fontSize = 12.sp,
                            color = ProfileTextSecondary.copy(alpha = 0.72f)
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = ProfileError,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = error,
                    fontFamily = Montserrat,
                    fontSize = 10.sp,
                    color = ProfileError
                )
            }
        }
    }
}


/** Світло-лавандова кнопка «Зберегти» з макета. */
@Composable
fun ProfilePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SaveButtonGradient)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                color = Color(0xFF2A163E),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                fontFamily = Montserrat,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2A163E)
            )
        }
    }
}

/** Рядок списку налаштувань зі стрілкою праворуч. */
@Composable
fun ProfileSettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 49.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ProfileTextPrimary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            fontFamily = Montserrat,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = ProfileTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!value.isNullOrBlank()) {
            // Довгі назви рядків не повинні «налипати» на значення.
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                fontFamily = Montserrat,
                fontSize = 11.sp,
                color = ProfileTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(7.dp))
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = ProfileTextPrimary,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Рядок вибору в діалогах мови / теми. */
@Composable
fun ProfileChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = Montserrat,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) ProfileTextPrimary else ProfileTextSecondary,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = if (selected) ProfileAccent else ProfileTextSecondary,
                    shape = CircleShape
                )
                .padding(4.dp)
                .background(
                    color = if (selected) ProfileAccent else Color.Transparent,
                    shape = CircleShape
                )
        )
    }
}

/** Рядок із перемикачем для діалогів налаштувань. */
@Composable
fun ProfileSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Montserrat,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) ProfileTextPrimary else ProfileTextSecondary
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    fontFamily = Montserrat,
                    fontSize = 10.sp,
                    color = ProfileTextSecondary
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ProfileBackground,
                checkedTrackColor = ProfileAccent,
                uncheckedThumbColor = ProfileTextSecondary,
                uncheckedTrackColor = ProfileFieldBackground,
                uncheckedBorderColor = ProfileFieldBorder
            )
        )
    }
}

/** Порожній стан / помилка завантаження профілю. */
@Composable
fun ProfileMessageCard(
    title: String,
    message: String,
    action: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProfileCard)
            .border(1.dp, ProfileCardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = ProfileAccent,
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            fontFamily = Montserrat,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = ProfileTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            fontFamily = Montserrat,
            fontSize = 11.sp,
            color = ProfileTextSecondary,
            textAlign = TextAlign.Center
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            ProfilePrimaryButton(
                text = action,
                onClick = onAction,
                modifier = Modifier.width(180.dp)
            )
        }
    }
}

