package com.llmlocal.feature.modelmanagement.di

import com.llmlocal.feature.modelmanagement.data.DataStoreModelSelectionSource
import com.llmlocal.feature.modelmanagement.data.LlmModelCatalogSource
import com.llmlocal.feature.modelmanagement.data.LlmModelInstalledSource
import com.llmlocal.feature.modelmanagement.data.ModelDownloadScheduler
import com.llmlocal.feature.modelmanagement.data.StoreDownloadProgressSource
import com.llmlocal.feature.modelmanagement.data.WorkInfoObserver
import com.llmlocal.feature.modelmanagement.domain.DownloadProgressSource
import com.llmlocal.feature.modelmanagement.domain.DownloadScheduler
import com.llmlocal.feature.modelmanagement.domain.DownloadWorkSource
import com.llmlocal.feature.modelmanagement.domain.InstalledModelsSource
import com.llmlocal.feature.modelmanagement.domain.ModelCatalogSource
import com.llmlocal.feature.modelmanagement.domain.ModelSelectionSource
import com.llmlocal.feature.modelmanagement.domain.usecase.CancelDownloadUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.DownloadModelUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.FilterModelsUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.ObserveModelListingsUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.RemoveModelUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.SelectModelUseCase
import com.llmlocal.feature.modelmanagement.presentation.ModelManagementViewModel
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementLabels
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module wiring for the model management feature — the only place
 * that knows the concrete `data/` classes.
 *
 * Adapters over `:core:llm`'s stores/manager (provided by `llmModule`, and
 * `commonModule` for `DispatcherProvider`) are bound to the domain source
 * interfaces; use cases are `factory`.
 *
 * The caller-supplied [ModelManagementLabels] is received via
 * `parametersOf(labels)` from `ModelManagementRoute`, so the same module
 * can serve any app.
 */
val modelManagementModule: Module = module {
    single<ModelCatalogSource> { LlmModelCatalogSource() }
    single<InstalledModelsSource> { LlmModelInstalledSource(manager = get(), catalog = get()) }
    single<ModelSelectionSource> { DataStoreModelSelectionSource(store = get()) }
    single<DownloadProgressSource> { StoreDownloadProgressSource(store = get()) }
    single<DownloadScheduler> { ModelDownloadScheduler(androidContext()) }
    single<DownloadWorkSource> { WorkInfoObserver(androidContext()) }

    factory {
        ObserveModelListingsUseCase(
            catalog = get(),
            installed = get(),
            selection = get(),
            progress = get(),
            work = get(),
        )
    }
    factory { DownloadModelUseCase(catalog = get(), scheduler = get()) }
    factory { CancelDownloadUseCase(scheduler = get()) }
    factory { SelectModelUseCase(catalog = get(), installed = get(), selection = get()) }
    factory { RemoveModelUseCase(catalog = get(), installed = get()) }
    factory { FilterModelsUseCase() }

    viewModel { params ->
        ModelManagementViewModel(
            observeListings = get(),
            downloadModel = get(),
            cancelDownload = get(),
            selectModel = get(),
            removeModel = get(),
            filterModels = get(),
            dispatchers = get(),
            labels = params.get<ModelManagementLabels>(),
        )
    }
}
