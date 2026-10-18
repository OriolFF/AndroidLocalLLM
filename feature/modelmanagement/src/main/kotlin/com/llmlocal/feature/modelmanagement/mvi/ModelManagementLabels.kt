package com.llmlocal.feature.modelmanagement.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Every user-visible string the model-management screen needs.
 *
 * Each consuming `:app:*` module supplies its own implementation (e.g.
 * `RecipeModelManagementLabels` in `:app:recipes`) and provides it via
 * [LocalModelManagementLabels].
 *
 * The labels use small lambdas for parameterized strings so the consuming
 * app can shape messages (e.g. "X of Y models · Z installed") without
 * forking the screen.
 */
@Immutable
data class ModelManagementLabels(
    // Screen header
    val screenTitle: String,
    val countLabelDefault: (visible: Int, total: Int, installed: Int) -> String,
    val countLabelFiltered: (visible: Int, total: Int, installed: Int) -> String,

    // Intro card
    /**
     * Free-form description of what the user can do here. Should reference
     * the app's primary feature by name (e.g. "Tap a model to download, view
     * details, or select it for recipe generation. Downloads run in the
     * background and survive closing the app.").
     */
    val introText: String,

    // Empty state (no models match filters)
    val emptyTitle: String,
    val emptyMessage: String,
    val emptyActionLabel: String,

    // Filter bar
    val filterByLabel: String,
    val clearFiltersAction: String,
    val filterStatusLabel: String,
    val filterFamilyLabel: String,
    val filterSizeLabel: String,
    val filterQuantLabel: String,
    val filterAllLabel: String,

    // Model card
    val activeBadge: String,
    val installedBadge: String,
    val detailsActionDescription: String,
    val cancelDownloadLabel: String,
    val useModelLabel: String,
    val removeLabel: String,
    val downloadLabel: String,
    val failureFooterFallback: String,

    // Detail sheet
    val detailByTemplate: (author: String, license: String) -> String,
    val detailSizeLabel: String,
    val detailFamilyLabel: String,
    val detailMinRamLabel: String,
    val detailMinRamUnit: String,
    val detailFileLabel: String,
    val detailUrlLabel: String,

    // Download progress
    val secondsLeftTemplate: (Int) -> String,
    val minutesLeftTemplate: (Int) -> String,

    // Snackbars
    /** Confirms [modelName] is now the active model; say what it will be used for. */
    val modelSelectedSnackbar: (modelName: String) -> String,
    /** Confirms [modelName]'s file was deleted from the device. */
    val modelRemovedSnackbar: (modelName: String) -> String,
    /** Shown when the user selects [modelName] before downloading it. */
    val modelNotDownloadedSnackbar: (modelName: String) -> String,
)

/**
 * CompositionLocal propagated by [com.llmlocal.feature.modelmanagement.ModelManagementRoute].
 *
 * Components inside the route can read labels via
 * [LocalModelManagementLabels.current] without taking them as a parameter.
 */
val LocalModelManagementLabels = staticCompositionLocalOf<ModelManagementLabels> {
    error(
        "ModelManagementLabels not provided. Wrap your content in " +
            "CompositionLocalProvider(LocalModelManagementLabels provides …) " +
            "inside ModelManagementRoute."
    )
}

/** Convenience accessor — `@Composable @ReadOnlyComposable`. */
val modelManagementLabels: ModelManagementLabels
    @Composable
    @ReadOnlyComposable
    get() = LocalModelManagementLabels.current
