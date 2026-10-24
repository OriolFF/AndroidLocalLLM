package com.llmlocal.recipekeeper.settings.di

import com.llmlocal.recipekeeper.settings.presentation.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Bindings of the settings screen. Its use cases come from the modules that
 * own each preference: `ObserveStartPageUseCase` / `SetStartPageUseCase` from
 * `webClipperModule`, `ObserveDetailBackdropUseCase` /
 * `SetDetailBackdropUseCase` from `recipesModule`.
 */
val settingsModule: Module = module {
    viewModel {
        SettingsViewModel(
            observeStartPage = get(),
            setStartPage = get(),
            observeBackdrop = get(),
            setBackdrop = get(),
            dispatchers = get(),
        )
    }
}
