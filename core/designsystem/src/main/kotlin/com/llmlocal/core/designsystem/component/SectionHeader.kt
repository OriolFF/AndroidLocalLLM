package com.llmlocal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Section title for content cards: a 32dp `primaryContainer` disc holding
 * [icon], followed by a bold `titleSmall` [label] ("Ingredients", "Steps",
 * "When to seek care", …).
 *
 * Brand-free: `primaryContainer` / `onPrimaryContainer` for the disc,
 * `onSurfaceVariant` for the label. [iconTint] overrides the icon colour
 * for semantically loaded sections (the medical card tints its
 * "When to seek care" icon with `colorScheme.error`).
 *
 * @param label the section title; exposed as an accessibility heading.
 * @param icon leading icon; `null` renders the label alone.
 * @param iconTint optional icon colour; defaults to `onPrimaryContainer`.
 * @param trailing optional trailing content (e.g. a "Reset" text button); when
 *   set, the label stretches to push it to the end of the row.
 */
@Composable
fun SectionHeader(
    label: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = (if (trailing != null) Modifier.weight(1f) else Modifier)
                // Section titles are headings so TalkBack heading navigation
                // can jump between Ingredients / Steps / Notes.
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}
