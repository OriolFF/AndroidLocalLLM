package com.llmlocal.core.data.di

import com.llmlocal.core.data.repository.RecipeRepositoryImpl
import com.llmlocal.core.domain.repository.RecipeRepository
import com.llmlocal.core.domain.usecase.GenerateRecipeUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the domain [RecipeRepository] to its data-layer implementation and
 * exposes the domain [GenerateRecipeUseCase] to the DI graph.
 *
 * `:core:domain` is pure-Kotlin (no Koin dependency), so the use case is
 * registered here — the data module is the natural home for cross-layer
 * wiring.
 */
val dataModule: Module = module {
    single<RecipeRepository> { RecipeRepositoryImpl(promptBuilder = get(), parser = get()) }
    factory { GenerateRecipeUseCase(repository = get()) }
}
