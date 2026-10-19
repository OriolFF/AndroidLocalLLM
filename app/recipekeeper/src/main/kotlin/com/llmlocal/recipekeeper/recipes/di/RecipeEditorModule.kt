package com.llmlocal.recipekeeper.recipes.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bindings of the recipe editor: its use cases and
 * `RecipeEditorViewModel` (with `parametersOf(recipeId)` from
 * `RecipeEditorRoute`). Empty in the F0 contract freeze; stream F3 fills it.
 */
val recipeEditorModule: Module = module { }
