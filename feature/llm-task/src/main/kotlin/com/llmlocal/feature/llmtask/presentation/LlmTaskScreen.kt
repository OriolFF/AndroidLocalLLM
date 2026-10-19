package com.llmlocal.feature.llmtask.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.core.designsystem.component.GradientHero
import com.llmlocal.feature.llmtask.presentation.components.InputsCard
import com.llmlocal.feature.llmtask.presentation.components.NoModelAvailableBanner
import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskIntent
import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskLabels
import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskState
import com.llmlocal.feature.llmtask.presentation.mvi.ModelStatus

/**
 * Stateless LLM-task screen. Receives a [LlmTaskState] and an
 * [onIntent] callback; it does not know about the ViewModel. Previewable.
 *
 * Layout (top to bottom inside a [LazyColumn]):
 *
 *  1. **No-model banner** — only when [ModelStatus.NoModelAvailable].
 *  2. **Hero** — title + subtitle from [labels], with [heroIcon] (defaults
 *     to a generic spark icon).
 *  3. **Inputs card** — `Surface` containing the input row, an empty state
 *     when no inputs are added, and the chip row.
 *  4. **Generate CTA** — full-width [Button] that morphs into an outlined
 *     Stop button while generating.
 *  5. **Output** — [renderOutput] renders the app-specific result (recipe,
 *     medical advice, …) or the streamed text when nothing is parsed yet.
 *  6. **Error** — replaced by an [EmptyState] instead of a plain red text.
 *
 * @param renderOutput rendering for the parsed result (or streamed text
 *   while generating). The consuming app provides this composable to draw
 *   its domain result type. It receives the full [LlmTaskState] so the
 *   app can branch on `result`, `streamedText`, and `isGenerating` to
 *   render the streaming cursor / parsed card / etc.
 * @param heroIcon optional leading icon in the gradient hero. Defaults to
 *   [Icons.Outlined.AutoAwesome] which is generic enough for any task.
 * @param noModelIcon optional icon in the no-model-available banner.
 *   Defaults to [Icons.Outlined.Memory].
 */
@Composable
fun LlmTaskScreen(
    state: LlmTaskState,
    onIntent: (LlmTaskIntent) -> Unit,
    labels: LlmTaskLabels,
    renderOutput: @Composable (LlmTaskState) -> Unit,
    modifier: Modifier = Modifier,
    heroIcon: ImageVector = Icons.Outlined.AutoAwesome,
    noModelIcon: ImageVector = Icons.Outlined.Memory,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 0.dp,
            end = 0.dp,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---- 1. No-model banner (conditional) -------------------------
        if (state.modelStatus is ModelStatus.NoModelAvailable) {
            item(key = "no_model") {
                NoModelAvailableBanner(
                    selectedModel = state.selectedModel,
                    labels = labels,
                    onOpenModels = { onIntent(LlmTaskIntent.OpenModelManagement) },
                    icon = noModelIcon,
                )
            }
        }

        // ---- 2. Hero --------------------------------------------------
        item(key = "hero") {
            GradientHero(
                title = labels.heroTitle,
                subtitle = labels.heroSubtitle,
                icon = heroIcon,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ---- 3. Inputs card ------------------------------------------
        item(key = "inputs_card") {
            InputsCard(state = state, onIntent = onIntent, labels = labels)
        }

        // ---- 4. Generate CTA -----------------------------------------
        item(key = "generate_cta") {
            GenerateCta(
                isGenerating = state.isGenerating,
                canGenerate = state.canGenerate,
                generateLabel = labels.generateLabel,
                stopLabel = labels.stopLabel,
                onGenerate = { onIntent(LlmTaskIntent.Generate) },
                onCancel = { onIntent(LlmTaskIntent.CancelGeneration) },
            )
        }

        // ---- 5. Output (conditional) ---------------------------------
        if (state.result != null || state.streamedText.isNotEmpty() || state.isGenerating) {
            item(key = "output_card") {
                renderOutput(state)
            }
        }

        // ---- 6. Error (conditional) -----------------------------------
        if (state.errorMessage != null) {
            item(key = "error") {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = labels.errorTitle,
                    message = state.errorMessage,
                    actionLabel = labels.errorDismissLabel,
                    onAction = { onIntent(LlmTaskIntent.DismissError) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Generate / Stop button block. When `isGenerating`, the primary button
 * morphs into a `LinearProgressIndicator` and the outlined "Stop" CTA
 * appears below it — keeps the CTA visible without forcing a layout jump.
 */
@Composable
private fun GenerateCta(
    isGenerating: Boolean,
    canGenerate: Boolean,
    generateLabel: String,
    stopLabel: String,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isGenerating) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(
                    imageVector = Icons.Outlined.StopCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stopLabel,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            Button(
                onClick = onGenerate,
                enabled = canGenerate,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = generateLabel,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}