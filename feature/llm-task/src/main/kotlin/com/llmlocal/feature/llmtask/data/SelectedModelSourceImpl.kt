package com.llmlocal.feature.llmtask.data

import com.llmlocal.core.llm.model.catalog.ModelCatalogRepository
import com.llmlocal.core.llm.selection.LlmModelSelectionStore
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.feature.llmtask.domain.SelectedModelSource
import kotlinx.coroutines.flow.Flow

/** Adapts [LlmModelSelectionStore] + [ModelCatalogRepository] to [SelectedModelSource]. */
internal class SelectedModelSourceImpl(
    private val store: LlmModelSelectionStore,
    private val catalog: ModelCatalogRepository,
) : SelectedModelSource {
    override val selectedModelId: Flow<String?> get() = store.selectedModelId
    override fun current(): String = store.current()
    override fun describe(modelId: String): LlmModelDescriptor? = catalog.findById(modelId)
}
