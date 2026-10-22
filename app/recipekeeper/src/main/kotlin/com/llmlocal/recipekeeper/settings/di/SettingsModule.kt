package com.llmlocal.recipekeeper.settings.di

import com.llmlocal.recipekeeper.settings.presentation.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Bindings of the settings screen. Its use cases (`ObserveStartPageUseCase`,
 * `SetStartPageUseCase`) come from `webClipperModule`, which owns the
 * start-page preference.
 */
val settingsModule: Module = module {
    viewModel {
        SettingsViewModel(
            observeStartPage = get(),
            setStartPage = get(),
            dispatchers = get(),
        )
    }
}
