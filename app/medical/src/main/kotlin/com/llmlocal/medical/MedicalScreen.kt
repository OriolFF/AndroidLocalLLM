package com.llmlocal.medical

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.llmlocal.feature.llmtask.LlmTaskRoute
import com.llmlocal.medical.config.MedicalLabels
import com.llmlocal.medical.config.MedicalTaskConfig
import com.llmlocal.medical.domain.MedicalAdvice
import com.llmlocal.medical.output.StreamedMedicalCard

/**
 * The medical app's wrapper around the generic
 * [com.llmlocal.feature.llmtask.LlmTaskRoute].
 *
 * Provides:
 *  - the medical-themed [MedicalLabels]
 *  - the [MedicalTaskConfig] that binds prompt + parser
 *  - a [renderOutput] lambda that casts `state.result` to
 *    [MedicalAdvice] and renders the [StreamedMedicalCard] with the
 *    "always-render-disclaimer" treatment.
 *
 * The `LlmTaskRoute` host owns the `LlmTaskViewModel` via Koin's
 * parameter mechanism — call sites only pass the config + labels +
 * output renderer.
 */
@Composable
fun MedicalScreen(
    modifier: Modifier = Modifier,
    onNavigateToModelManagement: () -> Unit = {},
) {
    LlmTaskRoute(
        config = MedicalTaskConfig,
        labels = MedicalLabels,
        renderOutput = { state ->
            @Suppress("UNCHECKED_CAST")
            val advice = state.result as? MedicalAdvice
            StreamedMedicalCard(
                streamedText = state.streamedText,
                advice = advice,
                isGenerating = state.isGenerating,
                warmingUpLabel = MedicalLabels.warmingUpLabel,
            )
        },
        modifier = modifier,
        onNavigateToModelManagement = onNavigateToModelManagement,
    )
}