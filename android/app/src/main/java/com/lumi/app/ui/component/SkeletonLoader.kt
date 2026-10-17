package com.lumi.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** A pulsing gray box used as a loading placeholder for images/text lines. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, cornerRadius: androidx.compose.ui.unit.Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    )
}

/** Skeleton for a horizontal carousel section on the Home screen. */
@Composable
fun SkeletonCarouselRow(itemCount: Int = 4) {
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        repeat(itemCount) {
            Column {
                SkeletonBox(modifier = Modifier.size(120.dp))
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(modifier = Modifier.width(90.dp).height(12.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

/** Skeleton for a single track list row. */
@Composable
fun SkeletonTrackRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SkeletonBox(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            SkeletonBox(modifier = Modifier.width(160.dp).height(14.dp))
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(modifier = Modifier.width(100.dp).height(12.dp))
        }
    }
}
