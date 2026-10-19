package com.llmlocal.feature.llmtask.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * Renders the current list of inputs as a wrap-row of chips with a
 * staggered fade+scale-in animation. Tapping the close icon on a chip
 * removes that input (the chip itself plays the fade-out animation).
 *
 * Inputs are plain `String` — the scaffold is domain-agnostic. Each app's
 * screen wrapper can map its own domain type (Ingredient, Symptom, …)
 * to a `String` before passing it in.
 *
 * The animation is per-chip via `AnimatedVisibility` + a brief delay so
 * the chips appear left-to-right rather than all at once. Doesn't animate
 * on every recomposition — only when the visible set actually changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputChips(
    inputs: List<String>,
    onRemove: (String) -> Unit,
    removeActionDescription: (String) -> String,
    modifier: Modifier = Modifier,
) {
    if (inputs.isEmpty()) return
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        inputs.forEachIndexed { index, value ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 220,
                        delayMillis = index * 35,
                    ),
                ) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(
                        durationMillis = 220,
                        delayMillis = index * 35,
                    ),
                ),
                exit = fadeOut(animationSpec = tween(durationMillis = 160)),
            ) {
                InputChip(
                    selected = false,
                    onClick = { onRemove(value) },
                    label = {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = removeActionDescription(value),
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        trailingIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                )
            }
        }
    }
}