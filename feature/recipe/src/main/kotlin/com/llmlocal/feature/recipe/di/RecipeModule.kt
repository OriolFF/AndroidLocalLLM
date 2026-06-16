package com.llmlocal.feature.recipe.di

import com.llmlocal.core.llm.di.DEMO_ENGINE_QUALIFIER
import com.llmlocal.core.llm.di.REAL_ENGINE_QUALIFIER
import com.llmlocal.feature.recipe.RecipeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val recipeModule: Module = module {
    viewModel {
        RecipeViewModel(
            generateRecipe = get(),
            modelManager = get(),
            realEngine = get(named(REAL_ENGINE_QUALIFIER)),
            demoEngine = get(named(DEMO_ENGINE_QUALIFIER)),
            dispatchers = get(),
        )
    }
}
