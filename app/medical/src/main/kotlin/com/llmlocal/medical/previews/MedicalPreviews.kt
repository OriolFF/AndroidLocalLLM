package com.llmlocal.medical.previews

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelFamily
import com.llmlocal.feature.llmtask.LlmTaskScreen
import com.llmlocal.feature.llmtask.mvi.LlmTaskState
import com.llmlocal.feature.llmtask.mvi.ModelStatus
import com.llmlocal.medical.config.MedicalLabels
import com.llmlocal.medical.designsystem.MedicalTokens
import com.llmlocal.medical.domain.MedicalAdvice
import com.llmlocal.medical.output.StreamedMedicalCard

/**
 * @Preview composables for the medical screen.
 *
 * Render [LlmTaskScreen] with hand-built [LlmTaskState] and the medical-
 * themed [MedicalLabels], drawing the result with [StreamedMedicalCard].
 */

private val SAMPLE_MODEL = LlmModelDescriptor(
    id = "gemma-4-E2B-it",
    displayName = "Gemma 4 E2B IT",
    description = "Google's compact instruction-tuned model.",
    url = "https://example.invalid/gemma-4-E2B-it.litertlm",
    sizeBytes = 2_580L * 1024 * 1024,
    filename = "gemma-4-E2B-it.litertlm",
    tags = listOf("instruction-tuned", "google"),
    license = "Gemma Terms",
    author = "Google",
    minRamMb = 4_096,
    family = ModelFamily.INSTRUCT,
)

private val SAMPLE_SYMPTOMS = listOf(
    "Headache",
    "Mild fever",
    "Runny nose",
    "Fatigue",
)

private val SAMPLE_ADVICE = MedicalAdvice(
    condition = "Mild seasonal allergies or a common cold",
    likelyCauses = listOf(
        "Pollen or other airborne allergens",
        "Recent viral exposure",
        "Indoor dust or pet dander",
    ),
    selfCare = listOf(
        "Stay well hydrated and rest as needed",
        "Use a saline nasal rinse to relieve congestion",
        "Try an over-the-counter antihistamine if symptoms suggest allergies",
    ),
    whenToSeekCare = "Seek urgent care if you develop a fever above 39 °C, " +
        "difficulty breathing, chest tightness, severe headache, a stiff neck, " +
        "confusion, or symptoms that persist for more than 10 days without " +
        "improvement. People with chronic conditions should contact their " +
        "primary clinician sooner rather than later.",
    disclaimer = "This is general information, not a substitute for a qualified clinician.",
)

private fun emptyState() = LlmTaskState(
    modelStatus = ModelStatus.Ready,
    selectedModel = SAMPLE_MODEL,
    selectedModelInstalled = true,
)

private fun readyState() = LlmTaskState(
    modelStatus = ModelStatus.Ready,
    selectedModel = SAMPLE_MODEL,
    selectedModelInstalled = true,
    inputs = SAMPLE_SYMPTOMS,
)

private fun streamingState() = readyState().copy(
    isGenerating = true,
    streamedText = "Condition: Mild seasonal allergies or a common cold\n\n" +
        "Likely Causes:\n- Pollen or other airborne allergens\n- Recent viral exposure\n\n" +
        "Self-Care:\n- Stay well hydrated and rest as needed\n- Use a saline nasal rinse",
)

private fun doneState() = readyState().copy(result = SAMPLE_ADVICE)

private fun errorState() = readyState().copy(
    errorMessage = "The model failed to start. Try restarting the app or " +
        "picking a different model.",
)

@Composable
private fun PreviewSurface(
    darkTheme: Boolean = false,
    state: LlmTaskState,
) {
    AppTheme(tokens = MedicalTokens, darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LlmTaskScreen(
                state = state,
                onIntent = {},
                labels = MedicalLabels,
                heroIcon = Icons.Filled.HealthAndSafety,
                renderOutput = { s ->
                    @Suppress("UNCHECKED_CAST")
                    val advice = s.result as? MedicalAdvice
                    StreamedMedicalCard(
                        streamedText = s.streamedText,
                        advice = advice,
                        isGenerating = s.isGenerating,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Medical / Empty", showBackground = true, heightDp = 900)
@Composable
private fun MedicalScreenEmptyPreview() {
    PreviewSurface(state = emptyState())
}

@Preview(name = "Medical / Ready", showBackground = true, heightDp = 900)
@Composable
private fun MedicalScreenReadyPreview() {
    PreviewSurface(state = readyState())
}

@Preview(name = "Medical / Streaming", showBackground = true, heightDp = 900)
@Composable
private fun MedicalScreenStreamingPreview() {
    PreviewSurface(state = streamingState())
}

@Preview(name = "Medical / Done", showBackground = true, heightDp = 1300)
@Composable
private fun MedicalScreenDonePreview() {
    PreviewSurface(state = doneState())
}

@Preview(name = "Medical / Error", showBackground = true, heightDp = 900)
@Composable
private fun MedicalScreenErrorPreview() {
    PreviewSurface(state = errorState())
}

@Preview(name = "Medical / Ready (Dark)", showBackground = true, heightDp = 900)
@Composable
private fun MedicalScreenReadyDarkPreview() {
    PreviewSurface(darkTheme = true, state = readyState())
}

@Preview(name = "Medical / Done (Dark)", showBackground = true, heightDp = 1300)
@Composable
private fun MedicalScreenDoneDarkPreview() {
    PreviewSurface(darkTheme = true, state = doneState())
}