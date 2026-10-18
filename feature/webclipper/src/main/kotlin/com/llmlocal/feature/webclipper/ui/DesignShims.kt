// TEMP shim: replaced by WS3 at merge.
// WS3 adds `PhaseProgress`, `PhaseItem`, `SkeletonLine` and `StreamingCursor`
// to `:core:designsystem/component`. These private-to-the-feature copies
// have the same API shape so the lead only swaps imports.
package com.llmlocal.feature.webclipper.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.theme.designTokens
import com.llmlocal.feature.webclipper.mvi.PhaseStatus

/**
 * One row of [PhaseProgress].
 *
 * @property detail optional sub-line under the label (hint / elapsed time)
 */
@Immutable
data class PhaseItem(
    val label: String,
    val status: PhaseStatus,
    val detail: String? = null,
)

/**
 * Vertical list of pipeline phases. Done = check in the brand success
 * color; active = 20dp ring; pending = outline circle; failed = error
 * icon; warning = tertiary warning icon.
 */
@Composable
fun PhaseProgress(
    items: List<PhaseItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item -> PhaseRow(item) }
    }
}

@Composable
private fun PhaseRow(item: PhaseItem) {
    val scheme = MaterialTheme.colorScheme
    val labelColor = when (item.status) {
        PhaseStatus.Pending -> scheme.onSurfaceVariant
        PhaseStatus.Failed -> scheme.error
        else -> scheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (item.status) {
                PhaseStatus.Pending -> Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(scheme.outlineVariant),
                )
                PhaseStatus.Active -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                PhaseStatus.Done -> Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = designTokens.successColor,
                )
                PhaseStatus.Failed -> Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = scheme.error,
                )
                PhaseStatus.Warning -> Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = scheme.tertiary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
            )
            item.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Placeholder bar for content that has not streamed in yet. Invisible to
 * accessibility services; shimmer disabled under reduced motion.
 */
@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    widthFraction: Float = 1f,
) {
    val reducedMotion = rememberReducedMotion()
    val alpha = if (reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val pulse by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
        pulse
    }
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .semantics { hideFromAccessibility() }
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

/** Blinking caret appended to text that is still streaming. */
@Composable
fun StreamingCursor(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val reducedMotion = rememberReducedMotion()
    val alpha = if (reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "cursor")
        val blink by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
            label = "cursorAlpha",
        )
        blink
    }
    Box(
        modifier = modifier
            .width(2.dp)
            .height(16.dp)
            .alpha(alpha)
            .background(color)
            .semantics { hideFromAccessibility() },
    )
}
