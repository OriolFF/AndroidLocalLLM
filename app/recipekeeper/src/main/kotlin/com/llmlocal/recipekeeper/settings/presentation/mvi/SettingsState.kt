package com.llmlocal.recipekeeper.settings.presentation.mvi

import androidx.compose.runtime.Immutable

/** Which start-page radio is selected on the settings screen. */
enum class StartPageOption { GOOGLE, BLANK, CUSTOM }

/**
 * Settings screen state.
 *
 * @property isLoading until the saved start page has been read
 * @property startPage the selected radio; [StartPageOption.CUSTOM] is only
 *   persisted once [customUrl] is a valid address
 * @property customUrl text in the custom-address field
 * @property customUrlError the last commit of [customUrl] was not a valid address
 */
@Immutable
data class SettingsState(
    val isLoading: Boolean = true,
    val startPage: StartPageOption = StartPageOption.GOOGLE,
    val customUrl: String = "",
    val customUrlError: Boolean = false,
)
