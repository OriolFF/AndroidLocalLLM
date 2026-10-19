package com.llmlocal.feature.modelmanagement.domain.usecase

import com.llmlocal.feature.modelmanagement.domain.InstalledModelsSource
import com.llmlocal.feature.modelmanagement.domain.ModelCatalogSource
import com.llmlocal.feature.modelmanagement.domain.ModelSelectionSource
import com.llmlocal.core.model.LlmModelDescriptor

/** Outcome of [SelectModelUseCase]. */
sealed interface SelectModelResult {
    data class Selected(val descriptor: LlmModelDescriptor) : SelectModelResult
    data class NotInstalled(val descriptor: LlmModelDescriptor) : SelectModelResult
    data object UnknownModel : SelectModelResult
}

/** Marks an installed model as the active one. Refuses models not on disk. */
class SelectModelUseCase(
    private val catalog: ModelCatalogSource,
    private val installed: InstalledModelsSource,
    private val selection: ModelSelectionSource,
) {
    suspend operator fun invoke(modelId: String): SelectModelResult {
        val descriptor = catalog.find(modelId) ?: return SelectModelResult.UnknownModel
        if (modelId !in installed.installedIds()) return SelectModelResult.NotInstalled(descriptor)
        selection.select(modelId)
        return SelectModelResult.Selected(descriptor)
    }
}
