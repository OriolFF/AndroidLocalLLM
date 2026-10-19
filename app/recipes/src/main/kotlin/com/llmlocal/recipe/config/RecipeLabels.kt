package com.llmlocal.recipe.config

import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskLabels

/**
 * Recipe-themed copy for the LLM-task scaffold.
 *
 * Implements [LlmTaskLabels] so the consuming app can drop the labels
 * directly into [com.llmlocal.feature.llmtask.presentation.LlmTaskRoute] / Screen
 * without per-call overrides.
 */
val RecipeLabels: LlmTaskLabels = LlmTaskLabels(
    // Hero
    heroTitle = "What's in your kitchen?",
    heroSubtitle = "Add a few ingredients and we'll cook up a recipe — all on your device.",

    // Inputs card
    inputsSectionTitle = "Ingredients",
    inputFieldLabel = "Ingredient",
    inputFieldPlaceholder = "e.g. eggs, garlic, tomatoes…",
    addInputActionDescription = "Add ingredient",
    addInputDisabledActionDescription = "Add ingredient, disabled",
    removeInputActionDescription = { name -> "Remove $name" },
    inputsEmptyTitle = "Your kitchen is empty",
    inputsEmptyMessage = "Add tomatoes, garlic, eggs… to get started.",

    // Generate CTA
    generateLabel = "Generate recipe",
    stopLabel = "Stop generating",

    // Loading / warming-up
    warmingUpLabel = "Warming up the model…",

    // Errors
    errorTitle = "Something went wrong",
    errorDismissLabel = "Dismiss",

    // No-model banner
    noModelSelectedTitle = "Model not installed",
    noModelSelectedSubtitle = { name -> "'$name' is selected but its file isn't on this device yet." },
    noModelGenericTitle = "No LLM on this device",
    noModelGenericSubtitle = "Pick and download a model to start generating recipes locally.",
    browseModelsLabel = "Browse models",

    // Misc
    duplicateInputSnackbar = "Ingredient already added",
)