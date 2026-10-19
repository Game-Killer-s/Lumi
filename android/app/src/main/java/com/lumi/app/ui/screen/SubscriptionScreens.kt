package com.lumi.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.app.R
import com.lumi.app.data.model.SubscriptionStatusResponse
import com.lumi.app.data.repository.SubscriptionRepository
import com.lumi.app.network.ApiResult
import com.lumi.app.ui.auth.Montserrat
import com.lumi.app.ui.i18n.LumiFormatters
import com.lumi.app.ui.i18n.currentAppLocale

// === Значення з Figma (екран «Управління підпискою») ===
private val SubscriptionBackground = Color(0xFF190E38)
private val SubscriptionText = Color(0xFFFFFFFF)
private val SubscriptionTextSecondary = Color(0xE6FFFFFF)   // rgba(255,255,255,0.9)
private val SubscriptionDivider = Color(0xFF7B7B7B)
private val SubscriptionCardBackground = Color(0x612C1B58)  // rgba(44,27,88,0.38)
private val SubscriptionRowBackground = Color(0xFF201344)
private val SubscriptionBadgeBackground = Color(0xFF006D14)
private val SubscriptionCrownColor = Color(0xFFDDE6FF)
private val SubscriptionVisaBlue = Color(0xFF111DFF)

// Secondary gradient, dark mode (Glass effect) з CSS.
private val SubscriptionTileGradient = Brush.horizontalGradient(
    colors = listOf(Color(0x664B1F6F), Color(0x666D54D9))
)

/** Корона з макета (fluent:crown-24-regular) — точний шлях іконки. */
private val SubscriptionCrown: ImageVector = ImageVector.Builder(
    name = "SubscriptionCrown",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = addPathNodes(
            "M12 17a2 2 0 1 0 0-4 2 2 0 0 0 0 4m2-12a2 2 0 0 1-.54 1.37l2.62 " +
                "3.28c.07.1.2.12.3.07l2.66-1.33a2 2 0 1 1 2.4 1.56l-1.8 9.62A1.75 " +
                "1.75 0 0 1 17.92 21H6.08c-.84 0-1.56-.6-1.72-1.43l-1.8-9.62a2 2 0 " +
                "1 1 2.4-1.56l2.65 1.33c.1.05.24.02.3-.07l2.63-3.28A2 2 0 0 1 12 " +
                "3a2 2 0 0 1 2 2m-2 2h-.04L9.1 10.59c-.51.64-1.41.84-2.15.47l-2.81" +
                "-1.4-.1.05 1.8 9.59q.06.18.25.2h11.84q.2-.02.25-.2l1.8-9.59-.1-.06" +
                "-2.81 1.4c-.74.38-1.64.18-2.15-.46L12.04 7z"
        ),
        fill = SolidColor(SubscriptionCrownColor)
    )
}.build()

/** Іконка картки в історії транзакцій (solar:card-bold) — точний шлях іконки. */
private val SubscriptionCardIcon: ImageVector = ImageVector.Builder(
    name = "SubscriptionCard",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = addPathNodes(
            "M22 12C22 15.7712 21.9997 17.6566 20.8281 18.8281C19.6566 19.9997 " +
                "17.7712 20 14 20H10C6.22876 20 4.34345 19.9997 3.17188 18.8281C2.0003 " +
                "17.6566 2 15.7712 2 12C2 11.5581 2.00007 11.142 2.00195 10.75H21.998C" +
                "21.9999 11.142 22 11.5581 22 12ZM6 15.25C5.58579 15.25 5.25 15.5858 " +
                "5.25 16C5.25 16.4142 5.58579 16.75 6 16.75H10C10.4142 16.75 10.75 " +
                "16.4142 10.75 16C10.75 15.5858 10.4142 15.25 10 15.25H6ZM12.5 " +
                "15.25C12.0858 15.25 11.75 15.5858 11.75 16C11.75 16.4142 12.0858 " +
                "16.75 12.5 16.75H14C14.4142 16.75 14.75 16.4142 14.75 16C14.75 " +
                "15.5858 14.4142 15.25 14 15.25H12.5Z"
        ),
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.EvenOdd
    )
    addPath(
        pathData = addPathNodes(
            "M14 4C17.7711 4 19.6565 4.00038 20.8281 5.17188C21.6717 6.01542 " +
                "21.9085 7.22882 21.9746 9.25H2.02637C2.0925 7.2289 2.3284 6.01541 " +
                "3.17188 5.17188C4.34343 4.00032 6.22887 4 10 4H14Z"
        ),
        fill = SolidColor(Color.White)
    )
}.build()

@Composable
fun SubscriptionScreen(onBack: () -> Unit, onSubscribe: () -> Unit) {
    val repository = remember { SubscriptionRepository() }
    var status by remember { mutableStateOf<SubscriptionStatusResponse?>(null) }

    LaunchedEffect(Unit) {
        when (val result = repository.getStatus()) {
            is ApiResult.Success -> status = result.data
            is ApiResult.Error -> Unit
        }
    }

    val locale = currentAppLocale()
    val active = status?.let {
        it.hasSubscription && it.status.uppercase() in setOf("ACTIVE", "TRIALING", "GRACE", "GRACE_PERIOD")
    } == true
    val until = LumiFormatters.formatDate(status?.nextChargeDate ?: status?.currentPeriodEnd, locale)
    val planTitle = status?.plan?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.subscription_plan_premium_individual)
    val planPrice = status?.plan?.let { LumiFormatters.formatMoney(it.priceCents, locale) }
        ?: stringResource(R.string.subscription_mock_amount)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SubscriptionBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 22.dp, bottom = 20.dp)
    ) {
        SubscriptionTopBar(onBack = onBack)
        // У макеті заголовок займає 19dp, а картка підписки починається на 102px.
        // Верхня панель тут — 48dp (зона дотику кнопки «Назад»), тому
        // 8dp дають ті самі 37dp від тексту заголовка до картки.
        Spacer(Modifier.height(8.dp))

        SubscriptionStatusCard(active = active, until = until)

        Spacer(Modifier.height(42.dp))
        SubscriptionSectionTitle(stringResource(R.string.subscription_info_section))
        Spacer(Modifier.height(17.dp))
        SubscriptionInfoRow(
            label = stringResource(R.string.subscription_plan_row),
            value = planTitle
        )
        Spacer(Modifier.height(17.dp))
        SubscriptionInfoRow(
            label = stringResource(R.string.subscription_price_row),
            value = stringResource(
                R.string.subscription_price,
                planPrice,
                pluralStringResource(
                    R.plurals.subscription_period_days,
                    status?.plan?.periodDays ?: 30,
                    status?.plan?.periodDays ?: 30
                )
            )
        )
        if (active && until != null) {
            Spacer(Modifier.height(17.dp))
            SubscriptionInfoRow(
                label = stringResource(R.string.subscription_next_charge_row),
                value = until
            )
        }

        Spacer(Modifier.height(27.dp))
        SubscriptionManageCard(active = active, onClick = onSubscribe)

        Spacer(Modifier.height(63.dp))
    }
}

/**
 * Верхня панель: стрілка 20×18 і заголовок 16sp.
 * Висота 48dp — зона дотику кнопки «Назад», а вміст вирівняний
 * по верхньому краю, як у макеті.
 */
@Composable
private fun SubscriptionTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(48.dp)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.TopStart
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = SubscriptionText,
                modifier = Modifier.size(width = 20.dp, height = 18.dp)
            )
        }
        Spacer(Modifier.width(15.dp))
        Text(
            text = stringResource(R.string.subscription_manage_title),
            fontFamily = Montserrat,
            fontSize = 16.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = SubscriptionText
        )
    }
}

/** Картка підписки: 117dp, rgba(44,27,88,0.38), radius 13. */
@Composable
private fun SubscriptionStatusCard(active: Boolean, until: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(117.dp)
            .background(SubscriptionCardBackground, RoundedCornerShape(13.dp))
            .padding(horizontal = 19.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            imageVector = SubscriptionCrown,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(21.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.profile_premium_title),
                fontFamily = Montserrat,
                fontSize = 13.sp,
                lineHeight = 13.sp,
                color = SubscriptionText
            )
            Text(
                text = stringResource(
                    if (active) R.string.subscription_active_badge else R.string.subscription_inactive_badge
                ),
                modifier = Modifier
                    .background(
                        if (active) SubscriptionBadgeBackground else Color(0xFF5B3B86),
                        RoundedCornerShape(5.dp)
                    )
                    .padding(horizontal = 13.dp, vertical = 4.dp),
                fontFamily = Montserrat,
                fontSize = 13.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SubscriptionText
            )
        }
        Text(
            text = if (active && until != null) {
                stringResource(R.string.subscription_active_until, until)
            } else {
                stringResource(R.string.subscription_inactive_hint)
            },
            modifier = Modifier.width(109.dp),
            fontFamily = Montserrat,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            color = SubscriptionText
        )
    }
}

/** Заголовок секції: 13sp/13, Bold. */
@Composable
private fun SubscriptionSectionTitle(text: String) {
    Text(
        text = text,
        fontFamily = Montserrat,
        fontSize = 13.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Bold,
        color = SubscriptionText
    )
}

@Composable
private fun SubscriptionInfoRow(label: String, value: String) {
    SubscriptionRow {
        SubscriptionRowLabel(label)
        SubscriptionRowValue(value)
    }
}

@Composable
private fun SubscriptionRowLabel(text: String) {
    Text(
        text = text,
        fontFamily = Montserrat,
        fontSize = 13.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
        color = SubscriptionText
    )
}

@Composable
private fun SubscriptionRowValue(text: String) {
    Text(
        text = text,
        fontFamily = Montserrat,
        fontSize = 13.sp,
        lineHeight = 13.sp,
        color = SubscriptionTextSecondary
    )
}

/** Рядок інформації: висота 25dp і лінія #7B7B7B по нижньому краю. */
@Composable
private fun SubscriptionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(25.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = SubscriptionDivider,
                    start = Offset(0f, size.height - stroke / 2f),
                    end = Offset(size.width, size.height - stroke / 2f),
                    strokeWidth = stroke
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content
    )
}

/** Картка Visa 24×21 з макета (fa-brands:cc-visa). */
@Composable
private fun SubscriptionVisaBadge() {
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(21.dp)
            .background(SubscriptionVisaBlue, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "VISA",
            fontFamily = Montserrat,
            fontSize = 6.sp,
            lineHeight = 6.sp,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic,
            color = Color.White
        )
    }
}



/** Картка «Керувати підпискою» / «Купити Premium»: 378×50, #201344, radius 9. */
@Composable
private fun SubscriptionManageCard(active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(SubscriptionRowBackground, RoundedCornerShape(9.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(
                    if (active) R.string.subscription_manage_row else R.string.subscription_buy
                ),
                fontFamily = Montserrat,
                fontSize = 13.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SubscriptionText
            )
            Text(
                text = stringResource(
                    if (active) R.string.subscription_manage_row_hint else R.string.subscription_buy_hint
                ),
                fontFamily = Montserrat,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                color = SubscriptionTextSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        SubscriptionChevron()
    }
}

/** Шеврон 9×13 з макета. */
@Composable
private fun SubscriptionChevron(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 9.dp, height = 13.dp)) {
        val stroke = 1.4.dp.toPx()
        drawLine(
            color = SubscriptionText,
            start = Offset(stroke / 2f, stroke / 2f),
            end = Offset(size.width - stroke / 2f, size.height / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = SubscriptionText,
            start = Offset(size.width - stroke / 2f, size.height / 2f),
            end = Offset(stroke / 2f, size.height - stroke / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionSuccessScreen(onDone: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.subscription_success_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.subscription_success_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.subscription_success_done))
            }
        }
    }
}
