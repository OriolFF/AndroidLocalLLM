package com.llmlocal.recipekeeper.config

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Per-app Koin module for Recipe Keeper.
 *
 * WS5a fills it with `single { RecipeKeeperClipConfig }`,
 * `single<RecipeRepository> { RoomRecipeRepository(get()) }`,
 * `single<ClipSink<ClippedRecipe>> { RecipeKeeperClipSink(...) }`, the Room
 * database and the Coil `ImageLoader` built on the Koin `OkHttpClient`.
 * Empty in WS0 so the app boots.
 */
val recipeKeeperConfigModule: Module = module {
}
