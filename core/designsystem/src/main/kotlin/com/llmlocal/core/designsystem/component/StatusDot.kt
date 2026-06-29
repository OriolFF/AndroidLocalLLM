package com.llmlocal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.theme.Sage40

/**
 * Semantic tone for a [StatusDot] — pairs a colored pill background with a
 * contrast-aware foreground. The actual `Color`s are resolved against the
 * active [MaterialTheme] via the [containerColor] / [onContainerColor]
 * extension functions; `StatusTone` itself is just a tag.
 */
sealed interface StatusTone {
    data object Active : StatusTone
    data object Installed : StatusTone
    data object Downloading : StatusTone
    data object Failed : StatusTone
    data object Idle : StatusTone
}

/** Theme-aware pill background for a [StatusTone]. */
@Composable
fun StatusTone.containerColor(): Color = when (this) {
    StatusTone.Active -> MaterialTheme.colorScheme.primary
    StatusTone.Installed -> Sage40
    StatusTone.Downloading -> MaterialTheme.colorScheme.secondaryContainer
    StatusTone.Failed -> MaterialTheme.colorScheme.errorContainer
    StatusTone.Idle -> MaterialTheme.colorScheme.surfaceVariant
}

/** Theme-aware pill foreground (icon + label color) for a [StatusTone]. */
@Composable
fun StatusTone.onContainerColor(): Color = when (this) {
    StatusTone.Active -> MaterialTheme.colorScheme.onPrimary
    StatusTone.Installed -> MaterialTheme.colorScheme.onPrimary
    StatusTone.Downloading -> MaterialTheme.colorScheme.onSecondaryContainer
    StatusTone.Failed -> MaterialTheme.colorScheme.onErrorContainer
    StatusTone.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Compact pill rendering a [label] with a leading [icon], using the tone's
 * container / on-container colors.
 *
 * Used in the model-card "Active / Installed" badges and anywhere a short
 * status word needs to float next to a title.
 */
@Composable
fun StatusDot(
    label: String,
    icon: ImageVector,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val container = tone.containerColor()
    val onContainer = tone.onContainerColor()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(container, MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = onContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}