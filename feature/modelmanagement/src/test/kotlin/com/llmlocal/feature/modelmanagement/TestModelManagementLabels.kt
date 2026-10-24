package com.llmlocal.feature.modelmanagement

import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementLabels

/** Test fixture; JVM tests have no Android resources. */
val TestModelManagementLabels = ModelManagementLabels(
    screenTitle = "", countLabelDefault = { _, _, _ -> "" }, countLabelFiltered = { _, _, _ -> "" },
    introText = "", emptyTitle = "", emptyMessage = "", emptyActionLabel = "",
    filterByLabel = "", clearFiltersAction = "", filterStatusLabel = "", filterFamilyLabel = "",
    filterSizeLabel = "", filterQuantLabel = "", filterAllLabel = "",
    activeBadge = "", installedBadge = "", detailsActionDescription = "", cancelDownloadLabel = "",
    useModelLabel = "", removeLabel = "", downloadLabel = "", failureFooterFallback = "",
    detailByTemplate = { _, _ -> "" }, detailSizeLabel = "", detailFamilyLabel = "",
    detailMinRamLabel = "", detailMinRamUnit = "", detailFileLabel = "", detailUrlLabel = "",
    capabilityVision = "Reads images",
    secondsLeftTemplate = { "" }, minutesLeftTemplate = { "" },
    modelSelectedSnackbar = { "selected $it" },
    modelRemovedSnackbar = { "removed $it" },
    modelNotDownloadedSnackbar = { "not downloaded $it" },
    unknownModelError = { "Unknown model: $it" },
)
