package com.llmlocal.core.llm.download

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * [WorkerFactory] that hands out [ModelDownloadWorker] instances with the
 * Koin-provided dependencies. Wired into the WorkManager `Configuration`
 * in `RecipeApp.onCreate` (which implements `Configuration.Provider`).
 *
 * Only [ModelDownloadWorker] is supported here — any other worker class
 * returns `null` so WorkManager falls back to its default reflection-based
 * construction (which is fine for workers that don't need Koin).
 */
class ModelDownloadWorkerFactory(
    private val manager: LlmModelManager,
    private val progressStore: ModelDownloadProgressStore,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        params: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == ModelDownloadWorker::class.java.name) {
            ModelDownloadWorker(appContext, params, manager, progressStore)
        } else {
            null
        }
}