package com.llmlocal.recipekeeper.clip.di

import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.recipekeeper.clip.data.RecipeKeeperClipSink
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperClipConfig
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperTaskConfig
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import org.koin.core.module.Module
import com.llmlocal.recipekeeper.R
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Bindings of the "clip" feature: how Recipe Keeper clips a page. Hands
 * `:feature:webclipper` its [WebClipConfig] and [ClipSink] (the sink needs
 * the recipes feature's repository and picture store, so install
 * `recipesModule` too), plus the `LlmTaskConfig` for previews / tests.
 */
val clipModule: Module = module {
    single { RecipeKeeperTaskConfig }
    single<WebClipConfig<ClippedRecipe>> { RecipeKeeperClipConfig(untitledTitle = androidContext().getString(R.string.recipekeeper_untitled_recipe)) }
    single<ClipSink<ClippedRecipe>> {
        val config = get<WebClipConfig<ClippedRecipe>>()
        RecipeKeeperClipSink(
            repository = get(), pictures = get(), dispatchers = get(),
            untitledTitle = androidContext().getString(R.string.recipekeeper_untitled_recipe),
            preferredPictures = { candidate, snapshot, max -> config.rankPictures(candidate, snapshot, max).filter { it.preferred }.map { it.url } },
        )
    }
}
