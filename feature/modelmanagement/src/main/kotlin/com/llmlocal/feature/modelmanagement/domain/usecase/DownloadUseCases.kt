package com.llmlocal.feature.modelmanagement.domain.usecase

import com.llmlocal.feature.modelmanagement.domain.DownloadScheduler
import com.llmlocal.feature.modelmanagement.domain.ModelCatalogSource

/** Outcome of [DownloadModelUseCase]. */
sealed interface StartDownloadResult {
    data object Started : StartDownloadResult
    data class UnknownModel(val modelId: String) : StartDownloadResult
}

/** Enqueues a download for a catalog model. */
class DownloadModelUseCase(
    private val catalog: ModelCatalogSource,
    private val scheduler: DownloadScheduler,
) {
    operator fun invoke(modelId: String): StartDownloadResult {
        if (catalog.find(modelId) == null) return StartDownloadResult.UnknownModel(modelId)
        scheduler.enqueue(modelId)
        return StartDownloadResult.Started
    }
}

/** Cancels an in-flight or pending download. Idempotent. */
class CancelDownloadUseCase(private val scheduler: DownloadScheduler) {
    operator fun invoke(modelId: String) = scheduler.cancel(modelId)
}
