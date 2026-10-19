package com.llmlocal.medical.medical.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.llmlocal.feature.llmtask.presentation.LlmTaskRoute
import com.llmlocal.medical.config.rememberMedicalLabels
import com.llmlocal.medical.config.MedicalTaskConfig
import com.llmlocal.medical.medical.domain.MedicalAdvice
import com.llmlocal.medical.medical.presentation.StreamedMedicalCard

/**
 * The medical app's wrapper around the generic
 * [com.llmlocal.feature.llmtask.presentation.LlmTaskRoute].
 *
 * Provides:
 *  - the medical-themed labels from [rememberMedicalLabels]
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
    val labels = rememberMedicalLabels()
    LlmTaskRoute(
        config = MedicalTaskConfig,
        labels = labels,
        renderOutput = { state ->
            @Suppress("UNCHECKED_CAST")
            val advice = state.result as? MedicalAdvice
            StreamedMedicalCard(
                streamedText = state.streamedText,
                advice = advice,
                isGenerating = state.isGenerating,
                warmingUpLabel = labels.warmingUpLabel,
            )
        },
        modifier = modifier,
        onNavigateToModelManagement = onNavigateToModelManagement,
    )
}