package com.llmlocal.medical.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementLabels
import com.llmlocal.medical.R

/**
 * Model-management copy for this app, built from `strings_modelmanagement.xml`.
 * Pass the result to `ModelManagementRoute(labels = …)`.
 */
@Composable
fun rememberMedicalModelManagementLabels(): ModelManagementLabels {
    val resources = LocalContext.current.resources
    return ModelManagementLabels(
        screenTitle = stringResource(R.string.medical_modelmanagement_screen_title),
        countLabelDefault = { visible, total, installed ->
            resources.getString(R.string.medical_modelmanagement_count, visible, total, installed)
        },
        countLabelFiltered = { visible, total, installed ->
            resources.getString(R.string.medical_modelmanagement_count_filtered, visible, total, installed)
        },
        introText = stringResource(R.string.medical_modelmanagement_intro),
        emptyTitle = stringResource(R.string.medical_modelmanagement_empty_title),
        emptyMessage = stringResource(R.string.medical_modelmanagement_empty_message),
        emptyActionLabel = stringResource(R.string.medical_modelmanagement_empty_action),
        filterByLabel = stringResource(R.string.medical_modelmanagement_filter_by),
        clearFiltersAction = stringResource(R.string.medical_modelmanagement_clear),
        filterStatusLabel = stringResource(R.string.medical_modelmanagement_filter_status),
        filterFamilyLabel = stringResource(R.string.medical_modelmanagement_filter_family),
        filterSizeLabel = stringResource(R.string.medical_modelmanagement_filter_size),
        filterQuantLabel = stringResource(R.string.medical_modelmanagement_filter_quant),
        filterAllLabel = stringResource(R.string.medical_modelmanagement_filter_all),
        activeBadge = stringResource(R.string.medical_modelmanagement_active),
        installedBadge = stringResource(R.string.medical_modelmanagement_installed),
        detailsActionDescription = stringResource(R.string.medical_modelmanagement_details),
        cancelDownloadLabel = stringResource(R.string.medical_modelmanagement_cancel),
        useModelLabel = stringResource(R.string.medical_modelmanagement_use),
        removeLabel = stringResource(R.string.medical_modelmanagement_remove),
        downloadLabel = stringResource(R.string.medical_modelmanagement_download),
        failureFooterFallback = stringResource(R.string.medical_modelmanagement_failure_fallback),
        detailByTemplate = { author, license -> resources.getString(R.string.medical_modelmanagement_detail_by, author, license) },
        detailSizeLabel = stringResource(R.string.medical_modelmanagement_detail_size),
        detailFamilyLabel = stringResource(R.string.medical_modelmanagement_detail_family),
        detailMinRamLabel = stringResource(R.string.medical_modelmanagement_detail_min_ram),
        detailMinRamUnit = stringResource(R.string.medical_modelmanagement_detail_min_ram_unit),
        detailFileLabel = stringResource(R.string.medical_modelmanagement_detail_file),
        detailUrlLabel = stringResource(R.string.medical_modelmanagement_detail_url),
        capabilityVision = resources.getString(R.string.medical_modelmanagement_capability_vision),
        secondsLeftTemplate = { resources.getString(R.string.medical_modelmanagement_seconds_left, it) },
        minutesLeftTemplate = { resources.getString(R.string.medical_modelmanagement_minutes_left, it) },
        modelSelectedSnackbar = { resources.getString(R.string.medical_modelmanagement_selected, it) },
        modelRemovedSnackbar = { resources.getString(R.string.medical_modelmanagement_removed, it) },
        modelNotDownloadedSnackbar = { resources.getString(R.string.medical_modelmanagement_not_downloaded, it) },
        unknownModelError = { resources.getString(R.string.medical_modelmanagement_unknown_model, it) },
    )
}
