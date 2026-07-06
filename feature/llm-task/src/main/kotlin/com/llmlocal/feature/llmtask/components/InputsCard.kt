package com.llmlocal.feature.llmtask.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.feature.llmtask.mvi.LlmTaskIntent
import com.llmlocal.feature.llmtask.mvi.LlmTaskLabels
import com.llmlocal.feature.llmtask.mvi.LlmTaskState
import com.llmlocal.feature.llmtask.mvi.ModelStatus

/**
 * Card wrapping the input field + chip list, with an inline empty state when
 * no inputs are added yet.
 *
 * The leading icon is a generic spark ([Icons.Outlined.AutoAwesome]) — the
 * section title comes from [LlmTaskLabels.inputsSectionTitle]. The empty
 * state copy comes from [LlmTaskLabels.inputsEmptyTitle] / `inputsEmptyMessage`.
 */
@Composable
fun InputsCard(
    state: LlmTaskState,
    onIntent: (LlmTaskIntent) -> Unit,
    labels: LlmTaskLabels,
    modifier: Modifier = Modifier,
) {
    val elevatedShape = MaterialTheme.shapes.large
    val padding by animateDpAsState(
        targetValue = if (state.inputs.isEmpty()) 20.dp else 12.dp,
        animationSpec = tween(durationMillis = 200),
        label = "inputsCardPadding",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = elevatedShape,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = labels.inputsSectionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            InputRow(
                value = state.input,
                onValueChange = { onIntent(LlmTaskIntent.UpdateInput(it)) },
                onAdd = { onIntent(LlmTaskIntent.AddInput) },
                labels = InputRowLabels(
                    fieldLabel = labels.inputFieldLabel,
                    fieldPlaceholder = labels.inputFieldPlaceholder,
                    addActionDescription = labels.addInputActionDescription,
                    addDisabledActionDescription = labels.addInputDisabledActionDescription,
                ),
                enabled = state.modelStatus is ModelStatus.Ready,
            )

            AnimatedVisibility(
                visible = state.inputs.isEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = labels.inputsEmptyTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = labels.inputsEmptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            InputChips(
                inputs = state.inputs,
                onRemove = { onIntent(LlmTaskIntent.RemoveInput(it)) },
                removeActionDescription = labels.removeInputActionDescription,
            )
        }
    }
}