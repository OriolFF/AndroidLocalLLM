package com.llmlocal.core.web.di

import com.llmlocal.core.web.ImageDownloader
import com.llmlocal.core.web.PageCapture
import com.llmlocal.core.web.WebViewPageCapture
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for `:core:web`.
 *
 * Provides the [PageCapture] (the `WebView`-bound capture that reads
 * scripts from the app's `assets/`) and the [ImageDownloader] (OkHttp +
 * 15 s call timeout + JPEG q85 resize). Consumed by `:feature:webclipper`
 * and `:app:recipekeeper` via the Koin graph.
 */
val webModule: Module = module {
    single<PageCapture> {
        WebViewPageCapture(
            context = androidContext(),
            dispatchers = get(),
        )
    }
    single {
        ImageDownloader(
            client = get<OkHttpClient>(),
            dispatchers = get(),
        )
    }
}
