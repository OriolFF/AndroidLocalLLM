package com.llmlocal.recipe.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementLabels
import com.llmlocal.recipe.R

/**
 * Model-management copy for this app, built from `strings_modelmanagement.xml`.
 * Pass the result to `ModelManagementRoute(labels = …)`.
 */
@Composable
fun rememberRecipeModelManagementLabels(): ModelManagementLabels {
    val resources = LocalContext.current.resources
    return ModelManagementLabels(
        screenTitle = stringResource(R.string.recipes_modelmanagement_screen_title),
        countLabelDefault = { visible, total, installed ->
            resources.getString(R.string.recipes_modelmanagement_count, visible, total, installed)
        },
        countLabelFiltered = { visible, total, installed ->
            resources.getString(R.string.recipes_modelmanagement_count_filtered, visible, total, installed)
        },
        introText = stringResource(R.string.recipes_modelmanagement_intro),
        emptyTitle = stringResource(R.string.recipes_modelmanagement_empty_title),
        emptyMessage = stringResource(R.string.recipes_modelmanagement_empty_message),
        emptyActionLabel = stringResource(R.string.recipes_modelmanagement_empty_action),
        filterByLabel = stringResource(R.string.recipes_modelmanagement_filter_by),
        clearFiltersAction = stringResource(R.string.recipes_modelmanagement_clear),
        filterStatusLabel = stringResource(R.string.recipes_modelmanagement_filter_status),
        filterFamilyLabel = stringResource(R.string.recipes_modelmanagement_filter_family),
        filterSizeLabel = stringResource(R.string.recipes_modelmanagement_filter_size),
        filterQuantLabel = stringResource(R.string.recipes_modelmanagement_filter_quant),
        filterAllLabel = stringResource(R.string.recipes_modelmanagement_filter_all),
        activeBadge = stringResource(R.string.recipes_modelmanagement_active),
        installedBadge = stringResource(R.string.recipes_modelmanagement_installed),
        detailsActionDescription = stringResource(R.string.recipes_modelmanagement_details),
        cancelDownloadLabel = stringResource(R.string.recipes_modelmanagement_cancel),
        useModelLabel = stringResource(R.string.recipes_modelmanagement_use),
        removeLabel = stringResource(R.string.recipes_modelmanagement_remove),
        downloadLabel = stringResource(R.string.recipes_modelmanagement_download),
        failureFooterFallback = stringResource(R.string.recipes_modelmanagement_failure_fallback),
        detailByTemplate = { author, license -> resources.getString(R.string.recipes_modelmanagement_detail_by, author, license) },
        detailSizeLabel = stringResource(R.string.recipes_modelmanagement_detail_size),
        detailFamilyLabel = stringResource(R.string.recipes_modelmanagement_detail_family),
        detailMinRamLabel = stringResource(R.string.recipes_modelmanagement_detail_min_ram),
        detailMinRamUnit = stringResource(R.string.recipes_modelmanagement_detail_min_ram_unit),
        detailFileLabel = stringResource(R.string.recipes_modelmanagement_detail_file),
        detailUrlLabel = stringResource(R.string.recipes_modelmanagement_detail_url),
        capabilityVision = resources.getString(R.string.recipes_modelmanagement_capability_vision),
        secondsLeftTemplate = { resources.getString(R.string.recipes_modelmanagement_seconds_left, it) },
        minutesLeftTemplate = { resources.getString(R.string.recipes_modelmanagement_minutes_left, it) },
        modelSelectedSnackbar = { resources.getString(R.string.recipes_modelmanagement_selected, it) },
        modelRemovedSnackbar = { resources.getString(R.string.recipes_modelmanagement_removed, it) },
        modelNotDownloadedSnackbar = { resources.getString(R.string.recipes_modelmanagement_not_downloaded, it) },
        unknownModelError = { resources.getString(R.string.recipes_modelmanagement_unknown_model, it) },
        detailAcceleratorLabel = stringResource(R.string.recipes_modelmanagement_detail_accelerator),
        detailLicenseLabel = stringResource(R.string.recipes_modelmanagement_detail_license),
        detailAuthorLabel = stringResource(R.string.recipes_modelmanagement_detail_author),
        detailBaseModelLabel = stringResource(R.string.recipes_modelmanagement_detail_base_model),
        detailSourceLabel = stringResource(R.string.recipes_modelmanagement_detail_source),
        detailUpdatedLabel = stringResource(R.string.recipes_modelmanagement_detail_updated),
        detailChecksumLabel = stringResource(R.string.recipes_modelmanagement_detail_checksum),
        detailContextLabel = stringResource(R.string.recipes_modelmanagement_detail_context),
        discoverAction = stringResource(R.string.recipes_modelmanagement_discover_action),
        discoveringLabel = stringResource(R.string.recipes_modelmanagement_discovering),
        discoveryFoundSnackbar = { n -> resources.getQuantityString(R.plurals.recipes_modelmanagement_discovery_found, n, n) },
        discoveryNothingNewSnackbar = stringResource(R.string.recipes_modelmanagement_discovery_nothing_new),
        discoveryFailedSnackbar = stringResource(R.string.recipes_modelmanagement_discovery_failed),
    )
}
