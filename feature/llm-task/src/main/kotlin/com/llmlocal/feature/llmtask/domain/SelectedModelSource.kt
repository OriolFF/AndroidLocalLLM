package com.llmlocal.feature.llmtask.domain

import com.llmlocal.core.model.LlmModelDescriptor
import kotlinx.coroutines.flow.Flow

/** Source of the user's model selection and the catalog lookup for it. */
interface SelectedModelSource {
    /** Selected model id; `null` while nothing has been picked (the default applies). */
    val selectedModelId: Flow<String?>

    /** Current selection, falling back to the default model id. Non-suspending. */
    fun current(): String

    /** Resolves [modelId] against the model catalog, or `null` when unknown. */
    fun describe(modelId: String): LlmModelDescriptor?
}
