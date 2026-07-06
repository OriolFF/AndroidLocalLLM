package com.llmlocal.core.data.di

import com.llmlocal.core.data.repository.LlmTaskRepositoryImpl
import com.llmlocal.core.domain.repository.LlmTaskRepository
import com.llmlocal.core.domain.usecase.GenerateLlmTaskUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the domain [LlmTaskRepository] to its data-layer implementation and
 * exposes the domain [GenerateLlmTaskUseCase] to the DI graph.
 *
 * `:core:domain` is pure-Kotlin (no Koin dependency), so the use case is
 * registered here — the data module is the natural home for cross-layer
 * wiring.
 */
val dataModule: Module = module {
    single<LlmTaskRepository> { LlmTaskRepositoryImpl() }
    factory { GenerateLlmTaskUseCase(repository = get()) }
}