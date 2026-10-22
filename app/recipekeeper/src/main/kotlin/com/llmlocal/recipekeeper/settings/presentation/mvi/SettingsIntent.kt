package com.llmlocal.recipekeeper.settings.presentation.mvi

/** User actions on the settings screen. */
sealed interface SettingsIntent {
    /** A start-page radio tapped. */
    data class SelectStartPage(val option: StartPageOption) : SettingsIntent

    /** Custom-address field edited. */
    data class UpdateCustomUrl(val value: String) : SettingsIntent

    /** Custom-address field committed (IME Done / focus lost): validate and save. */
    data object CommitCustomUrl : SettingsIntent

    /** "Manage models" row tapped. */
    data object OpenModelManagement : SettingsIntent
}
