package com.llmlocal.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pulsing rounded bar that stands in for a line of text that has not
 * arrived yet. Stack a few with different [widthFraction]s to sketch the
 * shape of a card before the model's first tokens land, then swap each one
 * for real content as it parses.
 *
 * Brand-free: `surfaceVariant` fill, alpha-pulsed so it reads on any
 * surface.
 *
 * @param widthFraction share of the parent width to fill, clamped to 0..1.
 * @param height bar height; defaults to roughly one body line.
 */
@Composable
fun SkeletonLine(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-pulse",
    )
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction.coerceIn(0f, 1f))
            .height(height)
            .alpha(pulse)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            ),
    )
}
