package com.llmlocal.feature.llmtask.mvi

import androidx.compose.runtime.Immutable
import com.llmlocal.core.model.LlmModelDescriptor

/**
 * The single source of truth for the LLM-task screen.
 *
 * Marked [Immutable] so Compose can skip recomposition when the reference
 * is unchanged.
 *
 * `result` is intentionally typed `Any?` (not generic over the app's
 * domain result type) so the ViewModel doesn't need to know the result
 * type at runtime. The consuming screen casts it to its domain type
 * inside its `renderOutput` lambda (see [com.llmlocal.feature.llmtask.LlmTaskScreen]).
 *
 * @property input the current text in the input field
 * @property inputs the list of confirmed inputs (additions to input)
 * @property modelStatus the on-disk + engine state of the selected model
 * @property isGenerating whether a generation pass is in flight
 * @property streamedText cumulative raw text from the LLM (set incrementally
 *   during streaming; empty when the LLM is idle)
 * @property result the parsed, app-specific result. `null` while streaming
 *   or before generation starts; non-null once the parser succeeds.
 * @property errorMessage user-facing error to display; cleared on dismiss
 */
@Immutable
data class LlmTaskState(
    val input: String = "",
    val inputs: List<String> = emptyList(),
    val modelStatus: ModelStatus = ModelStatus.Unknown,
    val isGenerating: Boolean = false,
    val streamedText: String = "",
    val result: Any? = null,
    val errorMessage: String? = null,
    /** Currently selected model — null until the user picks one. */
    val selectedModel: LlmModelDescriptor? = null,
    /** True when the user has selected a model but the file is missing on disk. */
    val selectedModelInstalled: Boolean = false,
) {
    /**
     * Whether the Generate button is enabled. Generation can only fire
     * when there are inputs AND no generation is in flight AND a model is
     * installed and the engine is ready.
     */
    val canGenerate: Boolean
        get() = inputs.isNotEmpty() &&
            !isGenerating &&
            modelStatus is ModelStatus.Ready
}