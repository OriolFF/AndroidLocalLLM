package com.llmlocal.recipekeeper.settings.presentation.mvi

import com.llmlocal.recipekeeper.recipes.domain.DetailBackdrop

/** User actions on the settings screen. */
sealed interface SettingsIntent {
    /** A start-page radio tapped. */
    data class SelectStartPage(val option: StartPageOption) : SettingsIntent

    /** Custom-address field edited. */
    data class UpdateCustomUrl(val value: String) : SettingsIntent

    /** Custom-address field committed (IME Done / focus lost): validate and save. */
    data object CommitCustomUrl : SettingsIntent

    /** A "Recipe background" radio tapped. */
    data class SelectBackdrop(val backdrop: DetailBackdrop) : SettingsIntent

    /** "Manage models" row tapped. */
    data object OpenModelManagement : SettingsIntent
}
