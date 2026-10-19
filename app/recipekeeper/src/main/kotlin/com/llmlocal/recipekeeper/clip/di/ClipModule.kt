package com.llmlocal.recipekeeper.clip.di

import com.llmlocal.feature.webclipper.ClipSink
import com.llmlocal.feature.webclipper.WebClipConfig
import com.llmlocal.recipekeeper.clip.data.RecipeKeeperClipSink
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperClipConfig
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperTaskConfig
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bindings of the "clip" feature: how Recipe Keeper clips a page. Hands
 * `:feature:webclipper` its [WebClipConfig] and [ClipSink] (the sink needs
 * the recipes feature's repository and picture store, so install
 * `recipesModule` too), plus the `LlmTaskConfig` for previews / tests.
 */
val clipModule: Module = module {
    single { RecipeKeeperTaskConfig }
    single<WebClipConfig<ClippedRecipe>> { RecipeKeeperClipConfig() }
    single<ClipSink<ClippedRecipe>> { RecipeKeeperClipSink(repository = get(), pictures = get(), dispatchers = get()) }
}
