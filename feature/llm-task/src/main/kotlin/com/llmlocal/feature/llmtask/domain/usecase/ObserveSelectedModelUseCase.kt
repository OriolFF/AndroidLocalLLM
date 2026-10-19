package com.llmlocal.feature.llmtask.domain.usecase

import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.feature.llmtask.domain.SelectedModelSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Emits the selected model's descriptor (the default model when nothing is picked). */
class ObserveSelectedModelUseCase(private val source: SelectedModelSource) {
    operator fun invoke(): Flow<LlmModelDescriptor?> =
        source.selectedModelId
            .map { id -> source.describe(id ?: source.current()) }
            .distinctUntilChanged()
}
