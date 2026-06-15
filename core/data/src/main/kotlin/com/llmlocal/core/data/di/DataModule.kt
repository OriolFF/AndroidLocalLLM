package com.llmlocal.core.data.di

import com.llmlocal.core.data.repository.RecipeRepositoryImpl
import com.llmlocal.core.domain.repository.RecipeRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the domain [RecipeRepository] to its data-layer implementation.
 */
val dataModule: Module = module {
    single<RecipeRepository> { RecipeRepositoryImpl(get(), get(), get()) }
}
