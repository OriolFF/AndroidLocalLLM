package com.llmlocal.feature.llmtask.di

import com.llmlocal.core.llm.di.REAL_ENGINE_QUALIFIER
import com.llmlocal.feature.llmtask.data.LlmGenerationSourceImpl
import com.llmlocal.feature.llmtask.data.ModelReadinessSourceImpl
import com.llmlocal.feature.llmtask.data.SelectedModelSourceImpl
import com.llmlocal.feature.llmtask.domain.LlmGenerationSource
import com.llmlocal.feature.llmtask.domain.ModelReadinessSource
import com.llmlocal.feature.llmtask.domain.SelectedModelSource
import com.llmlocal.feature.llmtask.domain.usecase.CancelGenerationUseCase
import com.llmlocal.feature.llmtask.domain.usecase.GenerateTaskUseCase
import com.llmlocal.feature.llmtask.domain.usecase.ObserveModelReadinessUseCase
import com.llmlocal.feature.llmtask.domain.usecase.ObserveSelectedModelUseCase
import com.llmlocal.feature.llmtask.domain.usecase.RecheckModelUseCase
import com.llmlocal.feature.llmtask.presentation.LlmTaskViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module wiring for the LLM-task feature.
 *
 * Data adapters over `:core:llm` are bound to the feature's domain
 * interfaces; use cases are `factory`s over those interfaces. The
 * ViewModel is parameterized because different `:app:*` modules supply
 * different `LlmTaskConfig<*>` values; consumers call
 * `koinViewModel<LlmTaskViewModel> { parametersOf(myConfig, myLabels) }`.
 *
 * The readiness source is a `factory` (the underlying observer serves one
 * collector scope); the generation source is a `factory`
 * over the shared real engine.
 */
val llmTaskModule: Module = module {
    factory<ModelReadinessSource> { ModelReadinessSourceImpl(observer = get()) }
    factory<SelectedModelSource> { SelectedModelSourceImpl(store = get(), catalog = get()) }
    factory<LlmGenerationSource> {
        LlmGenerationSourceImpl(engine = get(named(REAL_ENGINE_QUALIFIER)), generate = get())
    }

    factory { ObserveModelReadinessUseCase(get()) }
    factory { ObserveSelectedModelUseCase(get()) }
    factory { RecheckModelUseCase(get()) }
    factory { GenerateTaskUseCase(get()) }
    factory { CancelGenerationUseCase(get()) }

    viewModel { params ->
        LlmTaskViewModel(
            observeReadiness = get(),
            observeSelectedModel = get(),
            recheckModel = get(),
            generateTask = get(),
            cancelGeneration = get(),
            dispatchers = get(),
            config = params.get(),
            labels = params.get(),
        )
    }
}
