package com.llmlocal.recipekeeper.settings.presentation

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.llmlocal.recipekeeper.R

/**
 * Every user-visible string on the settings screen. No defaults: built from
 * `strings_settings.xml` by [rememberSettingsLabels] / [settingsLabels].
 */
@Immutable
data class SettingsLabels(
    val title: String,
    val back: String,
    val browserSection: String,
    val startPageTitle: String,
    val startPageSubtitle: String,
    val startPageGoogle: String,
    val startPageGoogleSubtitle: String,
    val startPageBlank: String,
    val startPageBlankSubtitle: String,
    val startPageCustom: String,
    val startPageCustomSubtitle: String,
    val customUrlLabel: String,
    val customUrlHint: String,
    val customUrlInvalid: String,
    val customUrlHelp: String,
    val modelsSection: String,
    val manageModels: String,
    val manageModelsSubtitle: String,
)

fun settingsLabels(resources: Resources): SettingsLabels = SettingsLabels(
    title = resources.getString(R.string.recipekeeper_settings_title),
    back = resources.getString(R.string.recipekeeper_back),
    browserSection = resources.getString(R.string.recipekeeper_settings_browser_section),
    startPageTitle = resources.getString(R.string.recipekeeper_settings_start_page_title),
    startPageSubtitle = resources.getString(R.string.recipekeeper_settings_start_page_subtitle),
    startPageGoogle = resources.getString(R.string.recipekeeper_settings_start_page_google),
    startPageGoogleSubtitle = resources.getString(R.string.recipekeeper_settings_start_page_google_subtitle),
    startPageBlank = resources.getString(R.string.recipekeeper_settings_start_page_blank),
    startPageBlankSubtitle = resources.getString(R.string.recipekeeper_settings_start_page_blank_subtitle),
    startPageCustom = resources.getString(R.string.recipekeeper_settings_start_page_custom),
    startPageCustomSubtitle = resources.getString(R.string.recipekeeper_settings_start_page_custom_subtitle),
    customUrlLabel = resources.getString(R.string.recipekeeper_settings_custom_url_label),
    customUrlHint = resources.getString(R.string.recipekeeper_settings_custom_url_hint),
    customUrlInvalid = resources.getString(R.string.recipekeeper_settings_custom_url_invalid),
    customUrlHelp = resources.getString(R.string.recipekeeper_settings_custom_url_help),
    modelsSection = resources.getString(R.string.recipekeeper_settings_models_section),
    manageModels = resources.getString(R.string.recipekeeper_settings_manage_models),
    manageModelsSubtitle = resources.getString(R.string.recipekeeper_settings_manage_models_subtitle),
)

@Composable
fun rememberSettingsLabels(): SettingsLabels {
    val resources = LocalContext.current.resources
    return remember(resources) { settingsLabels(resources) }
}
