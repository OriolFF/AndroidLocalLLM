package com.llmlocal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.theme.heroDarkGradient
import com.llmlocal.core.designsystem.theme.heroLightGradient

/**
 * Hero surface used at the top of a screen to introduce the feature.
 *
 * Renders a vertical 3-stop gradient (orange → brown → cream in light
 * mode; espresso stops in dark mode) inside a rounded `extraLarge` Surface.
 * An optional [icon] renders in a circular tinted container; [title] uses
 * `headlineMedium`; [subtitle] uses `bodyMedium` in `onSurfaceVariant`.
 *
 * Used by:
 *   - the recipe screen's "What's in your kitchen?" intro
 *   - the no-model-available banner
 *
 * @param title        Required headline.
 * @param subtitle     Optional supporting line; pass `null` to omit.
 * @param icon         Optional leading icon (rendered in a 40dp tinted circle).
 * @param modifier     Outer modifier.
 * @param actionLabel  Optional action label rendered at the bottom-right.
 * @param onAction     Click handler for [actionLabel]. Required when
 *                     [actionLabel] is non-null.
 */
@Composable
fun GradientHero(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val darkTheme = isSystemInDarkTheme()
    val gradient = if (darkTheme) heroDarkGradient else heroLightGradient
    val scheme = MaterialTheme.colorScheme
    val onHeroColor = if (darkTheme) scheme.onSurface else scheme.onPrimaryContainer

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    color = scheme.primaryContainer,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = onHeroColor,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = onHeroColor.copy(alpha = 0.78f),
                            )
                        }
                    }
                }
                if (actionLabel != null && onAction != null) {
                    androidx.compose.material3.Button(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(text = actionLabel)
                    }
                }
            }
        }
    }
}
