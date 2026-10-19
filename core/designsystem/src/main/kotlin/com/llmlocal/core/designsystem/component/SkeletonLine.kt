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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pulsing rounded bar that stands in for a line of text that has not
 * arrived yet. Stack a few with different [widthFraction]s to sketch the
 * shape of a card before the model's first tokens land, then swap each one
 * for real content as it parses.
 *
 * Brand-free: a translucent `onSurface` wash (≈ 10 % alpha, pulsing
 * between [MIN_ALPHA] and [MAX_ALPHA]) drawn *over the parent's own
 * colour*, so it keeps the same contrast on a plain surface, a tonally
 * elevated card or a container — a fixed `surfaceVariant` fill disappeared
 * on tonal-elevation-2 cards of some brands (WS10 bug 10).
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
    val alpha by transition.animateFloat(
        initialValue = MIN_ALPHA,
        targetValue = MAX_ALPHA,
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
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                shape = RoundedCornerShape(8.dp),
            ),
    )
}

/** Pulse range of the `onSurface` wash; the midpoint is the nominal 10 %. */
private const val MIN_ALPHA = 0.07f
private const val MAX_ALPHA = 0.14f
