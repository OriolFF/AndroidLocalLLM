package com.llmlocal.feature.llmtask.presentation

import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskLabels

/** Full label fixture for JVM tests (no Android resources available). */
internal val TestLlmTaskLabels = LlmTaskLabels(
    heroTitle = "", heroSubtitle = "", inputsSectionTitle = "", inputFieldLabel = "",
    inputFieldPlaceholder = "", addInputActionDescription = "", addInputDisabledActionDescription = "",
    removeInputActionDescription = { it }, inputsEmptyTitle = "", inputsEmptyMessage = "",
    generateLabel = "", stopLabel = "", warmingUpLabel = "", errorTitle = "", errorDismissLabel = "",
    generationFailedMessage = "generation failed",
    noModelSelectedTitle = "", noModelSelectedSubtitle = { it }, noModelGenericTitle = "",
    noModelGenericSubtitle = "", browseModelsLabel = "", duplicateInputSnackbar = "dup",
)
