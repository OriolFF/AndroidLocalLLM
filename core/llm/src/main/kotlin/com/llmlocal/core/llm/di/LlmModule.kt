package com.llmlocal.core.llm.di

import androidx.work.WorkerFactory
import com.google.ai.edge.litertlm.Backend
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.llm.BuildConfig
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.ModelDownloadProgressStore
import com.llmlocal.core.llm.download.ModelDownloadWorkerFactory
import com.llmlocal.core.llm.engine.LiteRtLlmEngine
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.llm.parser.RecipeParser
import com.llmlocal.core.llm.prompt.RecipePromptBuilder
import com.llmlocal.core.llm.selection.LlmModelSelectionStore
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module wiring for the LLM engine and supporting classes.
 *
 *   - [LlmEngine] (real)  — bound to [LiteRtLlmEngine] under the
 *     qualifier [REAL_ENGINE_QUALIFIER]. The model's path is resolved
 *     lazily on each `initialize()` call via [LlmModelSelectionStore].
 *   - [LlmModelManager]   — file / download / cache management.
 *   - [ModelDownloadProgressStore] — per-model download progress (DataStore).
 *   - [LlmModelSelectionStore]     — currently-selected model id (DataStore).
 *   - [ModelDownloadWorkerFactory] — factory handed to WorkManager so the
 *     worker can receive Koin-managed dependencies.
 *
 * The real engine's backend is selected at build time via
 * `BuildConfig.LLM_BACKEND` — "CPU" for the x86_64 emulator (where the
 * GPU/NPU accelerators fail to load) and "GPU" for release builds on
 * real hardware. See `core/llm/build.gradle.kts`.
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

    single { ModelDownloadProgressStore(androidContext()) }

    // Selection store — a `single` so the in-memory snapshot is shared.
    // The `start()` collector is launched at Koin init via `init` so the
    // snapshot is populated before any consumer reads it.
    single {
        LlmModelSelectionStore(androidContext()).also { it.start() }
    }

    single {
        ModelDownloadWorkerFactory(
            manager = get(),
            progressStore = get(),
        )
    } bind WorkerFactory::class

    // Koin classic DSL infers the type from the lambda's return value, so
    // we must declare the LlmEngine interface explicitly with `bind<LlmEngine>()`
    // — otherwise the binding is registered as LiteRtLlmEngine and
    // `get<LlmEngine>(named(...))` fails at runtime.
    single(named(REAL_ENGINE_QUALIFIER)) {
        LiteRtLlmEngine(
            context = androidContext(),
            modelPathProvider = {
                val selectionStore = get<LlmModelSelectionStore>()
                val manager = get<LlmModelManager>()
                val descriptor = LlmModelCatalog.findById(selectionStore.current())
                    ?: LlmModelCatalog.DEFAULT_MODEL
                manager.targetFile(descriptor)
            },
            backend = buildBackend(BuildConfig.LLM_BACKEND),
        )
    } bind LlmEngine::class
}

/**
 * Resolves the build-time backend name to a [Backend] instance. Unknown
 * values fall back to [Backend.CPU] for safety — it's slow but works on
 * every ABI that the AAR ships.
 */
private fun buildBackend(name: String): Backend = when (name.uppercase()) {
    "GPU" -> Backend.GPU()
    "NPU" -> Backend.NPU()
    "CPU" -> Backend.CPU()
    else -> {
        android.util.Log.w(
            "LlmModule",
            "Unknown LLM_BACKEND='$name', falling back to CPU",
        )
        Backend.CPU()
    }
}

const val REAL_ENGINE_QUALIFIER = "real-llm-engine"