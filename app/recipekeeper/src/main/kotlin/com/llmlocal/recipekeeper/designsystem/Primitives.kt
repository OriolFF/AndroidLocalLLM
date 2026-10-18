package com.llmlocal.recipekeeper.designsystem

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// TEMP shim: replaced by WS3 at merge. WS3 adds `SkeletonLine`,
// `StreamingCursor`, `SectionHeader` and `NumberBadge` to
// `com.llmlocal.core.designsystem.component` with these same signatures;
// the lead swaps the imports in `output/ClipDraftCard.kt` and
// `detail/RecipeDetailScreen.kt` and deletes this file.

/**
 * `true` when the user turned animator duration scale off (Settings →
 * Developer options or Accessibility → Remove animations). Pulses and
 * shimmers are disabled then (UX spec §9).
 */
@Composable
internal fun reducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Placeholder line for content still being generated: a rounded bar at
 * [widthFraction] of the available width with a slow opacity shimmer.
 * Hidden from accessibility services.
 */
@Composable
internal fun SkeletonLine(
    widthFraction: Float = 1f,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val alpha = if (reducedMotion()) {
        0.6f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "skeletonAlpha",
        )
        animated
    }
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
            .semantics { hideFromAccessibility() },
    )
}

/** Pulsing primary-tinted caret shown after the dimmed streaming tail. */
@Composable
internal fun StreamingCursor(modifier: Modifier = Modifier) {
    val alpha = if (reducedMotion()) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "cursor")
        val animated by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cursorAlpha",
        )
        animated
    }
    Box(
        modifier = modifier
            .width(10.dp)
            .height(18.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
            .semantics { hideFromAccessibility() },
    )
}

/** Section title with a 32dp tinted icon disc: "Ingredients", "Steps", "Notes"… */
@Composable
internal fun SectionHeader(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 28dp numbered disc for step lists. [selected] swaps to the secondary slot. */
@Composable
internal fun NumberBadge(
    number: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(28.dp)
            .background(if (selected) scheme.secondary else scheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onSecondary else scheme.onPrimary,
        )
    }
}
