package com.lumi.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.app.R
import com.lumi.app.ui.i18n.UiText
import com.lumi.app.ui.i18n.localized

// Шрифт Montserrat (з CSS дизайну)
val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_extrabold, FontWeight.ExtraBold)
)

// === Значення з CSS (Figma, темна тема #190E38) ===
val AuthBackgroundColor = Color(0xFF190E38)
val AuthTextColor = Color(0xFFDDE6FF)           // Secondary (text), dark mode
val AuthPlaceholderColor = Color(0xFF645A93)    // Input colour
val AuthErrorColor = Color(0xFFD73A31)          // Error
val AuthErrorBorderColor = Color(0xB3D73A31)    // rgba(215,58,49,0.7)
val AuthSuccessColor = Color(0xFF209F26)        // Success
val AuthMutedLinkColor = Color(0xFFD9D9D9)      // «Забули пароль?»
val AuthDividerColor = Color(0xFF737373)
val AuthLoginGoogleColor = Color(0xFFDDE6FF)    // кнопка Google на екрані входу
val AuthRegisterGoogleColor = Color(0xFFD4D4D4) // кнопка Google на екрані реєстрації

// linear-gradient(90deg, rgba(75,31,111,0.4) 0%, rgba(109,84,217,0.4) 100%) — поля вводу
val AuthGradient = Brush.horizontalGradient(
    colors = listOf(Color(0x664B1F6F), Color(0x666D54D9))
)

// Accent gradient, dark mode — головні кнопки
val AuthAccentGradient = Brush.horizontalGradient(
    colors = listOf(Color(0x664B1F6F), Color(0x66FFA26B))
)

// Оранжеві плями (Ellipse 24 та Ellipse 25 з CSS)
private data class Blob(val cx: Float, val cy: Float, val r: Float)
private val Blobs = listOf(
    Blob(312f, -10f, 49.5f), // Ellipse 24: 99x99, left 312, top -10
    Blob(37f, 154f, 39f)     // Ellipse 25: 78x78, left 37, top 154
)

/**
 * Мінімальна зона дотику за гайдлайнами доступності (48dp).
 * Використовується як невидимий «запас» навколо кнопок, тому
 * візуальний розмір елементів лишається за макетом Figma.
 */
val MinTouchTarget = 48.dp

/**
 * Фон екрана: колір #190E38 + дві оранжеві розмиті плями #FFA26B (blur 52.1px)
 * + еквалайзер уздовж нижнього краю.
 *
 * Еквалайзер малюється тут, до [content], тому він завжди лежить ПІД вмістом
 * (кнопками й текстами) і не залежить від прокрутки конкретного екрана.
 */
@Composable
fun AuthBackground(content: @Composable BoxScope.() -> Unit) {
    // Екрани автори зверстані за макетом Figma у фіксованій сітці 412x917:
    // геометрія задана в dp, а тексти — у sp. Якщо системний масштаб шрифту
    // збільшено, тексти перестають вміщатися у поля фіксованої висоти й
    // елементи наповзають один на одного. Тому в межах екранів автори
    // масштаб шрифту обмежується одиницею — вигляд лишається як у макеті.
    val designDensity = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthBackgroundColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / 412.dp.toPx()
            Blobs.forEach { b ->
                val center = Offset(b.cx.dp.toPx() * scaleX, b.cy.dp.toPx())
                val radius = (b.r * 2).dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFA26B).copy(alpha = 0.55f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
        // Фоновий шар: еквалайзер прибитий до низу екрана, вміст екранів
        // (кнопки, поля, тексти) малюється поверх нього.
        BottomEqualizer(Modifier.align(Alignment.BottomCenter))
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = designDensity.density,
                fontScale = designDensity.fontScale.coerceAtMost(1f)
            )
        ) {
            content()
        }
    }
}


/**
 * Логотип Lumi: Montserrat 800 / 64px + «Steam Your Sound» 600 / 13px.
 *
 * Розмір зменшено з макетних 80/16, щоб екрани входу й реєстрації
 * вміщалися на телефоні 360x800 без прокрутки.
 */
@Composable
fun AuthLogo(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.app_name),
            fontFamily = Montserrat,
            color = Color.White,
            fontSize = 64.sp,
            lineHeight = 78.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // Слоган бренду — один і той самий для всіх мов (translatable="false")
            text = stringResource(R.string.brand_tagline),
            fontFamily = Montserrat,
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Підпис над полем (Email / Пароль / Ім'я ...): Montserrat 14px, #DDE6FF.
 */
@Composable
fun AuthFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = Montserrat,
        color = AuthTextColor,
        fontSize = 14.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Поле вводу за дизайном:
 * - підпис над полем,
 * - саме поле: 50px, градієнт, radius 10,
 * - плейсхолдер #645A93 14px,
 * - введений текст #DDE6FF 14px,
 * - при помилці — червона рамка та текст помилки #D73A31 10px з крапочкою.
 *
 * Підпис, плейсхолдер і помилка приймаються як [UiText] — мова
 * підставляється в момент відображення (без перезапуску застосунку).
 * Для TalkBack підпис передається в сам edit box, а візуальний підпис
 * виключається з обходу, щоб він не озвучувався двічі.
 */
@Composable
fun LumiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: UiText,
    placeholder: UiText? = null,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: UiText? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    /**
     * Скільки рядків вміщає поле: 1 — звичайне однорядкове (форми входу),
     * більше — багаторядкове (коментар, деталі скарги). Поле малюється
     * рівно на [maxLines] рядків, тому висота не «стрибає» під час вводу.
     */
    maxLines: Int = 1
) {
    val labelText = label.localized()
    val placeholderText = placeholder?.localized().orEmpty()
    val errorMessage = errorText?.localized()

    Column(modifier = modifier.fillMaxWidth()) {
        AuthFieldLabel(labelText, modifier = Modifier.clearAndSetSemantics { })
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Однорядкове поле тримає 44dp (стиснуто з макетних 50dp),
                // багаторядкове виростає під текст — тому обмеження саме знизу.
                .heightIn(min = 44.dp)
                .background(AuthGradient, RoundedCornerShape(10.dp))
                .then(
                    if (isError) {
                        Modifier.border(1.dp, AuthErrorBorderColor, RoundedCornerShape(10.dp))
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        // Текст у кілька рядків не має впиратись у краї поля.
                        top = if (maxLines > 1) 8.dp else 0.dp,
                        bottom = if (maxLines > 1) 8.dp else 0.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = labelText
                                if (errorMessage != null) error(errorMessage)
                            },
                        textStyle = TextStyle(
                            fontFamily = Montserrat,
                            color = AuthTextColor,
                            fontSize = 14.sp
                        ),
                        singleLine = maxLines == 1,
                        // minLines == maxLines: багаторядкове поле не «стрибає»
                        // у висоті, поки користувач набирає текст.
                        minLines = maxLines,
                        maxLines = maxLines,
                        cursorBrush = SolidColor(AuthTextColor),
                        visualTransformation = visualTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (value.isEmpty() && placeholderText.isNotEmpty()) {
                                    Text(
                                        text = placeholderText,
                                        fontFamily = Montserrat,
                                        color = AuthPlaceholderColor,
                                        fontSize = 14.sp,
                                        // Плейсхолдер не переноситься на другий рядок,
                                        // інакше він вилазить за межі поля висотою 50dp.
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (trailingIcon != null) {
                    Spacer(Modifier.width(4.dp))
                    trailingIcon()
                }
            }
        }

        if (errorMessage != null) {
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(AuthErrorColor, CircleShape)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    // Помилку озвучує сам edit box через semantics.error
                    text = errorMessage,
                    fontFamily = Montserrat,
                    color = AuthErrorColor,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }
        }
    }
}

/**
 * Головна кнопка: акцентний градієнт, radius 10.
 * Висота: 50px (вхід), 44px (реєстрація / відновлення).
 *
 * Зона дотику не менша за 48dp (гайдлайни доступності), при цьому
 * сам градієнт малюється рівно на висоту [height] з макета.
 */
@Composable
fun PrimaryAuthButton(
    text: UiText,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val label = text.localized()
    val loadingLabel = stringResource(R.string.common_loading)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .semantics { if (loading) contentDescription = loadingLabel }
            .clickable(enabled = enabled && !loading, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(AuthAccentGradient, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = AuthTextColor,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = label,
                    fontFamily = Montserrat,
                    color = AuthTextColor,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Кнопка Google: висота 44px, radius 10, чорний текст, іконка Google 17px.
 * Колір фону залежить від екрана: вхід #DDE6FF, реєстрація #D4D4D4.
 *
 * Зона дотику — не менше 48dp; іконка позначена як декоративна,
 * щоб TalkBack не озвучував її окремо від тексту кнопки.
 */
@Composable
fun GoogleAuthButton(
    text: UiText,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AuthLoginGoogleColor
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(backgroundColor, RoundedCornerShape(10.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = GoogleGIcon,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text.localized(),
                fontFamily = Montserrat,
                color = Color(0xFF000000),
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Розділювач «або»: лінії #737373 + текст #DDE6FF.
 */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AuthDividerColor)
        )
        Text(
            text = stringResource(R.string.common_or),
            fontFamily = Montserrat,
            color = AuthTextColor,
            fontSize = 15.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AuthDividerColor)
        )
    }
}


/**
 * Іконка замка для екрана відновлення пароля:
 * коло з акцентним градієнтом + кільця + білий замок (за CSS).
 */
@Composable
fun LockIcon(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Зовнішні кільця (Ellipse 2 / 4 / 3)
        Box(
            modifier = Modifier
                .size(202.dp)
                .border(0.5.dp, Color(0xFF4B1F6F).copy(alpha = 0.8f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(177.dp)
                .border(0.8.dp, Color(0xFF420A66).copy(alpha = 0.9f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(148.dp)
                .border(2.dp, Color(0xFF420A66).copy(alpha = 0.9f), CircleShape)
        )
        // Коло з градієнтом (Ellipse 1)
        Box(
            modifier = Modifier
                .size(116.dp)
                .background(AuthAccentGradient, CircleShape)
        )
        // Підкладка (Ellipse 5)
        Box(
            modifier = Modifier
                .size(115.dp)
                .background(Color(0xFF420A66).copy(alpha = 0.55f), CircleShape)
        )
        // Сам замок (circum:lock)
        Canvas(modifier = Modifier.size(77.dp)) {
            val w = this.size.width
            val h = this.size.height
            // Дужка замка
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.28f, h * 0.12f),
                size = Size(w * 0.44f, h * 0.5f),
                style = Stroke(width = w * 0.09f)
            )
            // Корпус замка
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.16f, h * 0.42f),
                size = Size(w * 0.68f, h * 0.46f),
                cornerRadius = CornerRadius(w * 0.09f, w * 0.09f)
            )
            // Личманка
            drawCircle(
                color = AuthBackgroundColor,
                radius = w * 0.07f,
                center = Offset(w * 0.5f, h * 0.62f)
            )
        }
    }
}

/**
 * Чекбокс згоди: квадрат 16x16, #DDE6FF, radius 3, темна галочка.
 *
 * Якщо [onCheckedChange] не передано, чекбокс виконує лише візуальну роль —
 * взаємодію та семантику (роль Checkbox, стан) забезпечує батьківський
 * рядок через Modifier.toggleable.
 */
@Composable
fun AuthCheckBox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .background(
                color = if (checked) AuthTextColor else AuthPlaceholderColor,
                shape = RoundedCornerShape(3.dp)
            )
            .border(1.dp, AuthTextColor.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = onCheckedChange
                    )
                } else {
                    Modifier.clearAndSetSemantics { }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF12101C),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}


// Пурпурні «бари» еквалайзера внизу екрана (Group 8 з CSS)
private data class Bar(val left: Float, val top: Float, val w: Float, val h: Float)
private val EqualizerBars = listOf(
    Bar(1f, 824f, 5f, 76f), Bar(15f, 809f, 7f, 108f), Bar(31f, 837f, 8f, 63f), Bar(47f, 824f, 7f, 93f),
    Bar(63f, 782f, 6f, 127f), Bar(79f, 824f, 5f, 93f), Bar(93f, 804f, 8f, 96f), Bar(110f, 824f, 7f, 93f),
    Bar(125f, 782f, 8f, 127f), Bar(142f, 824f, 5f, 93f), Bar(156f, 804f, 7f, 96f), Bar(172f, 837f, 7f, 80f),
    Bar(188f, 821f, 5f, 88f), Bar(202f, 782f, 8f, 135f), Bar(218f, 819f, 7f, 81f), Bar(233f, 809f, 9f, 108f),
    Bar(251f, 821f, 5f, 88f), Bar(266f, 782f, 6f, 135f), Bar(281f, 819f, 6f, 81f), Bar(296f, 809f, 8f, 108f),
    Bar(313f, 834f, 7f, 75f), Bar(329f, 824f, 5f, 93f), Bar(343f, 782f, 7f, 118f), Bar(359f, 824f, 5f, 93f),
    Bar(373f, 807f, 10f, 102f), Bar(392f, 824f, 5f, 93f), Bar(405f, 782f, 8f, 118f)
)

/**
 * Висота смуги еквалайзера внизу екрана — макетні 135dp.
 *
 * Смуга є частиною фону ([AuthBackground]), тому вміст екранів може
 * прокручуватись поверх неї й не потребує відступу знизу.
 */
val BottomEqualizerHeight = 135.dp

/** Висота, під яку намальовані бари в [EqualizerBars] (координати макета 917dp). */
private val EqualizerDesignHeight = 135.dp

/**
 * Еквалайзер внизу екрана: прямокутники rgba(56,37,112,0.63),
 * blur 2px, radius 13px.
 */
@Composable
fun BottomEqualizer(modifier: Modifier = Modifier) {
    val barColor = Color(0xFF382570).copy(alpha = 0.63f)
    Canvas(modifier = modifier.fillMaxWidth().height(BottomEqualizerHeight)) {
        val scaleX = size.width / 412.dp.toPx()
        // Бари описані в координатах макета (135dp заввишки) — стискаємо їх
        // по вертикалі під фактичну висоту смуги, щоб вони не обрізались.
        val scaleY = size.height / EqualizerDesignHeight.toPx()
        EqualizerBars.forEach { b ->
            drawRoundRect(
                color = barColor,
                topLeft = Offset((b.left).dp.toPx() * scaleX, (b.top - 782).dp.toPx() * scaleY),
                size = Size((b.w).dp.toPx() * scaleX, (b.h).dp.toPx() * scaleY),
                cornerRadius = CornerRadius(13.dp.toPx() * scaleY, 13.dp.toPx() * scaleY)
            )
        }
    }
}

