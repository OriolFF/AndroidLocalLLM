package com.llmlocal.feature.llmtask.data

import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.llm.selection.LlmModelSelectionStore
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.feature.llmtask.domain.SelectedModelSource
import kotlinx.coroutines.flow.Flow

/** Adapts [LlmModelSelectionStore] + [LlmModelCatalog] to [SelectedModelSource]. */
internal class SelectedModelSourceImpl(
    private val store: LlmModelSelectionStore,
) : SelectedModelSource {
    override val selectedModelId: Flow<String?> get() = store.selectedModelId
    override fun current(): String = store.current()
    override fun describe(modelId: String): LlmModelDescriptor? = LlmModelCatalog.findById(modelId)
}
