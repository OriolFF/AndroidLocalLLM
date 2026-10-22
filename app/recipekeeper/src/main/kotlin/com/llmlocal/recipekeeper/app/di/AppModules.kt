package com.llmlocal.recipekeeper.app.di

import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.llmlocal.recipekeeper.clip.di.clipModule
import com.llmlocal.recipekeeper.recipes.di.recipeEditorModule
import com.llmlocal.recipekeeper.recipes.di.recipesModule
import com.llmlocal.recipekeeper.settings.di.settingsModule
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * App-level bindings that belong to no feature: Coil's [ImageLoader] on the
 * shared Koin [OkHttpClient]; `RecipeKeeperApp` exposes it through
 * `SingletonImageLoader.Factory`.
 */
val appModule: Module = module {
    single<ImageLoader> {
        val client: OkHttpClient = get()
        ImageLoader.Builder(androidContext())
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}

/** Every Recipe Keeper module, in install order; `RecipeKeeperApp` appends them to the shared `:core` / `:feature` modules. */
val recipeKeeperModules: List<Module> =
    listOf(appModule, recipesModule, recipeEditorModule, clipModule, settingsModule)
