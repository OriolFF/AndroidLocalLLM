package com.llmlocal.feature.recipe.di

import com.llmlocal.feature.recipe.RecipeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val recipeModule: Module = module {
    viewModel {
        RecipeViewModel(
            generateRecipe = get(),
            modelManager = get(),
            engine = get(),
            dispatchers = get(),
        )
    }
}
