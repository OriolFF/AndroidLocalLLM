package com.llmlocal.recipekeeper.settings.presentation.mvi

/** One-shot side effects from `SettingsViewModel`, consumed by `SettingsRoute`. */
sealed interface SettingsEffect {
    data object NavigateToModelManagement : SettingsEffect
}
