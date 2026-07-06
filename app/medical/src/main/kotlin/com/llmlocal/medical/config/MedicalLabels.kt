package com.llmlocal.medical.config

import com.llmlocal.feature.llmtask.mvi.LlmTaskLabels

/**
 * Medical-themed copy for the LLM-task scaffold.
 *
 * Implements [LlmTaskLabels] so the consuming app can drop the labels
 * directly into [com.llmlocal.feature.llmtask.LlmTaskRoute] / Screen
 * without per-call overrides.
 */
val MedicalLabels: LlmTaskLabels = LlmTaskLabels(
    // Hero
    heroTitle = "How are you feeling today?",
    heroSubtitle = "Add the symptoms you're experiencing and we'll offer " +
        "general wellness suggestions — all on your device.",

    // Inputs card
    inputsSectionTitle = "Symptoms",
    inputFieldLabel = "Symptom",
    inputFieldPlaceholder = "e.g. headache, sore throat, fatigue…",
    addInputActionDescription = "Add symptom",
    addInputDisabledActionDescription = "Add symptom, disabled",
    removeInputActionDescription = { name -> "Remove $name" },
    inputsEmptyTitle = "No symptoms added",
    inputsEmptyMessage = "Add what you're feeling to get started.",

    // Generate CTA
    generateLabel = "Get advice",
    stopLabel = "Stop",

    // Loading / warming-up
    warmingUpLabel = "Reviewing your symptoms…",

    // Errors
    errorTitle = "Something went wrong",
    errorDismissLabel = "Dismiss",

    // No-model banner
    noModelSelectedTitle = "Model not installed",
    noModelSelectedSubtitle = { name -> "'$name' is selected but its file isn't on this device yet." },
    noModelGenericTitle = "No LLM on this device",
    noModelGenericSubtitle = "Pick and download a model to start getting " +
        "wellness suggestions locally.",
    browseModelsLabel = "Browse models",

    // Misc
    duplicateInputSnackbar = "Symptom already added",
)