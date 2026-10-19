package com.llmlocal.feature.modelmanagement.domain.usecase

import com.llmlocal.feature.modelmanagement.domain.InstalledModelsSource
import com.llmlocal.feature.modelmanagement.domain.ModelCatalogSource
import com.llmlocal.core.model.LlmModelDescriptor

/** Outcome of [RemoveModelUseCase]. */
sealed interface RemoveModelResult {
    data class Removed(val descriptor: LlmModelDescriptor) : RemoveModelResult
    data object NothingRemoved : RemoveModelResult
}

/**
 * Deletes a model file. The selection is deliberately left untouched: the
 * engine surfaces a "file missing" failure on next initialise, which is
 * the right signal.
 */
class RemoveModelUseCase(
    private val catalog: ModelCatalogSource,
    private val installed: InstalledModelsSource,
) {
    suspend operator fun invoke(modelId: String): RemoveModelResult {
        val descriptor = catalog.find(modelId) ?: return RemoveModelResult.NothingRemoved
        return if (installed.remove(modelId)) RemoveModelResult.Removed(descriptor)
        else RemoveModelResult.NothingRemoved
    }
}
