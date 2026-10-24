package com.llmlocal.feature.modelmanagement.presentation.mvi

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.llmlocal.feature.modelmanagement.R

/**
 * Brand-neutral labels for previews. Real apps build their own labels from
 * their own resources (e.g. `rememberRecipeModelManagementLabels()`).
 */
@Composable
fun defaultModelManagementLabels(): ModelManagementLabels {
    val resources = LocalContext.current.resources
    return ModelManagementLabels(
        screenTitle = stringResource(R.string.modelmanagement_default_screen_title),
        countLabelDefault = { visible, total, installed ->
            resources.getString(R.string.modelmanagement_default_count, visible, total, installed)
        },
        countLabelFiltered = { visible, total, installed ->
            resources.getString(R.string.modelmanagement_default_count_filtered, visible, total, installed)
        },
        introText = stringResource(R.string.modelmanagement_default_intro),
        emptyTitle = stringResource(R.string.modelmanagement_default_empty_title),
        emptyMessage = stringResource(R.string.modelmanagement_default_empty_message),
        emptyActionLabel = stringResource(R.string.modelmanagement_default_empty_action),
        filterByLabel = stringResource(R.string.modelmanagement_default_filter_by),
        clearFiltersAction = stringResource(R.string.modelmanagement_default_clear),
        filterStatusLabel = stringResource(R.string.modelmanagement_default_filter_status),
        filterFamilyLabel = stringResource(R.string.modelmanagement_default_filter_family),
        filterSizeLabel = stringResource(R.string.modelmanagement_default_filter_size),
        filterQuantLabel = stringResource(R.string.modelmanagement_default_filter_quant),
        filterAllLabel = stringResource(R.string.modelmanagement_default_filter_all),
        activeBadge = stringResource(R.string.modelmanagement_default_active),
        installedBadge = stringResource(R.string.modelmanagement_default_installed),
        detailsActionDescription = stringResource(R.string.modelmanagement_default_details),
        cancelDownloadLabel = stringResource(R.string.modelmanagement_default_cancel),
        useModelLabel = stringResource(R.string.modelmanagement_default_use),
        removeLabel = stringResource(R.string.modelmanagement_default_remove),
        downloadLabel = stringResource(R.string.modelmanagement_default_download),
        failureFooterFallback = stringResource(R.string.modelmanagement_default_failure_fallback),
        detailByTemplate = { author, license ->
            resources.getString(R.string.modelmanagement_default_detail_by, author, license)
        },
        detailSizeLabel = stringResource(R.string.modelmanagement_default_detail_size),
        detailFamilyLabel = stringResource(R.string.modelmanagement_default_detail_family),
        detailMinRamLabel = stringResource(R.string.modelmanagement_default_detail_min_ram),
        detailMinRamUnit = stringResource(R.string.modelmanagement_default_detail_min_ram_unit),
        detailFileLabel = stringResource(R.string.modelmanagement_default_detail_file),
        detailUrlLabel = stringResource(R.string.modelmanagement_default_detail_url),
        capabilityVision = resources.getString(R.string.modelmanagement_capability_vision),
        secondsLeftTemplate = { resources.getString(R.string.modelmanagement_progress_seconds_left, it) },
        minutesLeftTemplate = { resources.getString(R.string.modelmanagement_progress_minutes_left, it) },
        modelSelectedSnackbar = { resources.getString(R.string.modelmanagement_default_selected, it) },
        modelRemovedSnackbar = { resources.getString(R.string.modelmanagement_default_removed, it) },
        modelNotDownloadedSnackbar = { resources.getString(R.string.modelmanagement_default_not_downloaded, it) },
        unknownModelError = { resources.getString(R.string.modelmanagement_default_unknown_model, it) },
    )
}
