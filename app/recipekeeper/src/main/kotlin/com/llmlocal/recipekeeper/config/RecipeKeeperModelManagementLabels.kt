package com.llmlocal.recipekeeper.config

import com.llmlocal.feature.modelmanagement.mvi.ModelManagementLabels

/**
 * Recipe Keeper copy for the model-management screen. Provides every
 * string the feature's screens + components need via
 * `LocalModelManagementLabels`; passed to `ModelManagementRoute` from
 * `MainActivity`.
 *
 * Same fields as `RecipeModelManagementLabels` in `:app:recipes` except
 * [ModelManagementLabels.introText], which talks about clipping. WS3 adds
 * the three snackbar templates; WS5b fills them in here.
 */
val RecipeKeeperModelManagementLabels: ModelManagementLabels = ModelManagementLabels(
    // Screen header
    screenTitle = "Available models",
    countLabelDefault = { visible, total, installed ->
        "$visible of $total models · $installed installed"
    },
    countLabelFiltered = { visible, total, installed ->
        "Showing $visible of $total models · $installed installed"
    },

    // Intro card
    introText = "Tap a model to download, view details, or select it for " +
        "recipe clipping. Downloads run in the background and " +
        "survive closing the app.",

    // Empty state
    emptyTitle = "No models match your filters",
    emptyMessage = "Try widening the size bucket or clearing the family / status filter.",
    emptyActionLabel = "Clear filters",

    // Filter bar
    filterByLabel = "Filter by",
    clearFiltersAction = "Clear",
    filterStatusLabel = "Status",
    filterFamilyLabel = "Family",
    filterSizeLabel = "Size",
    filterQuantLabel = "Quant",
    filterAllLabel = "All",

    // Model card
    activeBadge = "Active",
    installedBadge = "Installed",
    detailsActionDescription = "Details",
    cancelDownloadLabel = "Cancel",
    useModelLabel = "Use this model",
    removeLabel = "Remove",
    downloadLabel = "Download",
    failureFooterFallback = "Download failed — tap Download to retry",

    // Detail sheet
    detailByTemplate = { author, license -> "by $author · $license" },
    detailSizeLabel = "Size",
    detailFamilyLabel = "Family",
    detailMinRamLabel = "Min RAM",
    detailMinRamUnit = "MB",
    detailFileLabel = "File",
    detailUrlLabel = "URL",

    // Download progress
    secondsLeftTemplate = { secs -> "${secs}s left" },
    minutesLeftTemplate = { mins -> "${mins}m left" },
)