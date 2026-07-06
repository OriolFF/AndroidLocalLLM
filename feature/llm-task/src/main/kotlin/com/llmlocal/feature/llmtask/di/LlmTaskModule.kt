package com.llmlocal.feature.llmtask.di

import com.llmlocal.core.llm.di.REAL_ENGINE_QUALIFIER
import com.llmlocal.feature.llmtask.LlmTaskViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module wiring for the LLM-task feature.
 *
 * The ViewModel is constructed via a parameterized definition because
 * different `:app:*` modules supply different `LlmTaskConfig<*>` values
 * (recipe vs. medical vs. …). The consuming app calls
 * `koinViewModel<LlmTaskViewModel> { parametersOf(myConfig, myLabels) }`
 * to inject the right config + labels.
 */
val llmTaskModule: Module = module {
    viewModel { params ->
        LlmTaskViewModel(
            generateTask = get(),
            modelManager = get(),
            selectionStore = get(),
            progressStore = get(),
            realEngine = get(named(REAL_ENGINE_QUALIFIER)),
            dispatchers = get(),
            config = params.get(),
            labels = params.get(),
        )
    }
}