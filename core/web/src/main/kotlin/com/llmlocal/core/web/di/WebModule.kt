package com.llmlocal.core.web.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for `:core:web`.
 *
 * WS2 fills it with `single<PageCapture> { WebViewPageCapture(get()) }`
 * and `single { ImageDownloader(client = get(), dispatchers = get()) }`.
 * Kept empty in WS0 so apps can already install it without resolving
 * unimplemented bindings at runtime.
 */
val webModule: Module = module {
}
