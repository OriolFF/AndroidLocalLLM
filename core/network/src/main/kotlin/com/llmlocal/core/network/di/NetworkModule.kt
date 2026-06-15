package com.llmlocal.core.network.di

import com.llmlocal.core.network.HttpClientProvider
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module that exposes the singleton [OkHttpClient]. The Koin graph picks
 * this up automatically; no per-feature wiring is required.
 */
val networkModule: Module = module {
    single<OkHttpClient> { HttpClientProvider.create(enableLogging = false) }
}
