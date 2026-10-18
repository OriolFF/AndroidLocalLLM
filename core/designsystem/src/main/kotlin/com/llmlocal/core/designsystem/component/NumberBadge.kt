package com.llmlocal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 28dp `primary` disc with [n] centred in `onPrimary`, used as the marker
 * in front of each row of an ordered list (recipe steps, pipeline stages).
 *
 * Brand-free: the primary / onPrimary pair is a guaranteed-contrast slot
 * in every Material 3 scheme.
 *
 * @param n the 1-based number to show.
 * @param selected swaps to the secondary colour slot (e.g. the current step).
 */
@Composable
fun NumberBadge(
    n: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(28.dp)
            .background(
                color = if (selected) scheme.secondary else scheme.primary,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = n.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onSecondary else scheme.onPrimary,
        )
    }
}
