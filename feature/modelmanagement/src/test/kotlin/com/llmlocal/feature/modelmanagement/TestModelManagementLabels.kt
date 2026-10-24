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
    detailAcceleratorLabel = "Runs on",
    detailLicenseLabel = "License",
    detailAuthorLabel = "Author",
    detailBaseModelLabel = "Base model",
    detailSourceLabel = "Source",
    detailUpdatedLabel = "Updated",
    detailChecksumLabel = "SHA-256",
    detailContextLabel = "Context window",
    discoverAction = "Check for new models",
    discoveringLabel = "Checking…",
    discoveryFoundSnackbar = { "$it new models" },
    discoveryNothingNewSnackbar = "No new models",
    discoveryFailedSnackbar = "Discovery failed",
)
