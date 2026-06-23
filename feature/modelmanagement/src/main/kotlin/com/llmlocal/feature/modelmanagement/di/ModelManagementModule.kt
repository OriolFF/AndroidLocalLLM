package com.llmlocal.feature.modelmanagement.di

import com.llmlocal.feature.modelmanagement.ModelManagementViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module wiring for the model management feature.
 *
 * The ViewModel pulls together the catalog, manager, progress / selection
 * stores, scheduler, and WorkManager observer. All its dependencies are
 * already provided by `:core:llm`'s `llmModule` (and `commonModule` for
 * `DispatcherProvider`).
 */
val modelManagementModule: Module = module {
    single { ModelDownloadScheduler(androidContext()) }
    single { WorkInfoObserver(androidContext()) }

    viewModel {
        ModelManagementViewModel(
            manager = get(),
            selectionStore = get(),
            progressStore = get(),
            scheduler = get(),
            workInfoObserver = get(),
            dispatchers = get(),
        )
    }
}