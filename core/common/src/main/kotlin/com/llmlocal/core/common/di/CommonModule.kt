package com.llmlocal.core.common.di

import com.llmlocal.core.common.coroutines.DefaultDispatcherProvider
import com.llmlocal.core.common.coroutines.DispatcherProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module that exposes shared utilities (dispatchers, etc.) to the rest
 * of the graph. Imported by the app-level Koin host and any feature that
 * needs to inject dispatchers.
 */
val commonModule: Module = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
}
