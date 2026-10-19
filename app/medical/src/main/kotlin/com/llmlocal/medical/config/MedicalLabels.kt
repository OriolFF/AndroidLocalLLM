package com.llmlocal.medical.config

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskLabels
import com.llmlocal.medical.R

/** Medical-themed copy for the LLM-task scaffold, built from `strings_medical.xml`. */
@Composable
fun rememberMedicalLabels(): LlmTaskLabels {
    val resources = LocalContext.current.resources
    return remember(resources) { medicalLabels(resources) }
}

/** Non-composable factory for callers without a composition (Koin, workers, tests). */
fun medicalLabels(resources: Resources): LlmTaskLabels = LlmTaskLabels(
    heroTitle = resources.getString(R.string.medical_hero_title),
    heroSubtitle = resources.getString(R.string.medical_hero_subtitle),
    inputsSectionTitle = resources.getString(R.string.medical_inputs_section_title),
    inputFieldLabel = resources.getString(R.string.medical_input_field_label),
    inputFieldPlaceholder = resources.getString(R.string.medical_input_field_placeholder),
    addInputActionDescription = resources.getString(R.string.medical_input_add),
    addInputDisabledActionDescription = resources.getString(R.string.medical_input_add_disabled),
    removeInputActionDescription = { name -> resources.getString(R.string.medical_input_remove, name) },
    inputsEmptyTitle = resources.getString(R.string.medical_inputs_empty_title),
    inputsEmptyMessage = resources.getString(R.string.medical_inputs_empty_message),
    generateLabel = resources.getString(R.string.medical_generate),
    stopLabel = resources.getString(R.string.medical_stop),
    warmingUpLabel = resources.getString(R.string.medical_warming_up),
    errorTitle = resources.getString(R.string.medical_error_title),
    errorDismissLabel = resources.getString(R.string.medical_error_dismiss),
    generationFailedMessage = resources.getString(R.string.medical_error_generation_failed),
    noModelSelectedTitle = resources.getString(R.string.medical_no_model_selected_title),
    noModelSelectedSubtitle = { name -> resources.getString(R.string.medical_no_model_selected_subtitle, name) },
    noModelGenericTitle = resources.getString(R.string.medical_no_model_generic_title),
    noModelGenericSubtitle = resources.getString(R.string.medical_no_model_generic_subtitle),
    browseModelsLabel = resources.getString(R.string.medical_browse_models),
    duplicateInputSnackbar = resources.getString(R.string.medical_duplicate_input),
)
