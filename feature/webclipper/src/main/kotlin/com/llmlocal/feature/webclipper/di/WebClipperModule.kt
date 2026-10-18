package com.llmlocal.feature.webclipper.di

import com.llmlocal.feature.webclipper.ClipSession
import com.llmlocal.feature.webclipper.WebViewHolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module wiring for the web-clipper feature.
 *
 * WS4 adds the parameterised ViewModel definitions
 * (`viewModel { params -> BrowserViewModel(...) }`,
 * `viewModel { params -> ExtractionViewModel(...) }`), which receive the
 * app's `WebClipConfig<T>` / `ClipSink<T>` / `WebClipperLabels` via
 * `parametersOf` from the routes, like `llmTaskModule`.
 */
val webClipperModule: Module = module {
    single { ClipSession() }
    single { WebViewHolder(androidContext()) }
}
