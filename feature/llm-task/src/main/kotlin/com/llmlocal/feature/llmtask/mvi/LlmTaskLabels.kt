package com.llmlocal.feature.llmtask.mvi

import androidx.compose.runtime.Immutable

/**
 * Every user-visible string the LLM-task scaffold needs.
 *
 * Each consuming `:app:*` module supplies its own implementation (e.g.
 * `RecipeLabels` in `:app:recipes` provides the recipe-themed copy) and
 * provides it via [com.llmlocal.feature.llmtask.LocalLlmTaskLabels].
 *
 * Define all the strings the default [com.llmlocal.feature.llmtask.LlmTaskScreen]
 * uses here so an app can plug in labels without forking the screen.
 */
@Immutable
data class LlmTaskLabels(
    // Hero
    val heroTitle: String,
    val heroSubtitle: String,

    // Inputs card
    val inputsSectionTitle: String,
    val inputFieldLabel: String,
    val inputFieldPlaceholder: String,
    val addInputActionDescription: String,   // for icon contentDescription
    val addInputDisabledActionDescription: String,
    val removeInputActionDescription: (String) -> String,
    val inputsEmptyTitle: String,
    val inputsEmptyMessage: String,

    // Generate CTA
    val generateLabel: String,
    val stopLabel: String,

    // Loading / warming-up
    val warmingUpLabel: String,

    // Errors
    val errorTitle: String,
    val errorDismissLabel: String,

    // No-model banner
    val noModelSelectedTitle: String,
    val noModelSelectedSubtitle: (LlmModelDescriptorName: String) -> String,
    val noModelGenericTitle: String,
    val noModelGenericSubtitle: String,
    val browseModelsLabel: String,

    // Misc
    val duplicateInputSnackbar: String,
)

/**
 * Convenience so callers don't have to repeat themselves building the
 * "remove $name" content description on every chip.
 */
fun String.removeInputActionDescription(template: (String) -> String): String =
    template(this)