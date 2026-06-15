package com.llmlocal.core.llm.di

import android.content.Context
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.engine.LiteRtLlmEngine
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.parser.RecipeParser
import com.llmlocal.core.llm.prompt.RecipePromptBuilder
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module wiring for the LLM engine and supporting classes.
 *
 *   - [LlmEngine]            — the on-device model wrapper
 *   - [LlmModelManager]      — file / download / cache management
 *   - [RecipePromptBuilder]  — converts ingredients to a model prompt
 *   - [RecipeParser]         — converts streamed model text into a Recipe
 */
val llmModule: Module = module {
    single { RecipePromptBuilder() }
    single { RecipeParser() }

    single {
        LlmModelManager(
            context = androidContext(),
            httpClient = get<OkHttpClient>(),
            dispatchers = get<DispatcherProvider>(),
        )
    }

    single<LlmEngine> {
        LiteRtLlmEngine(
            context = androidContext(),
            modelPathProvider = { get<LlmModelManager>().targetFile() },
        )
    }
}
