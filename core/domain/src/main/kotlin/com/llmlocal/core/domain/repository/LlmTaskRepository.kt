package com.llmlocal.core.domain.repository

import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.engine.LlmRequest
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level repository for generating structured tasks with the on-device
 * LLM. Implementations live in `:core:data` and wire together an
 * `LlmEngine`, a `LlmTaskConfig<T>`, and the engine's token stream.
 */
interface LlmTaskRepository {

    /**
     * Streams [LlmTaskEvent]s as the result [T] is generated, using [engine]
     * as the source of truth for the text and [config] to bind the prompt
     * and parser.
     *
     * Passing the engine in (rather than holding it in a field) lets the
     * caller swap between the real on-device engine and a fake / demo one
     * without re-creating the repository.
     *
     * Backed by [generateStream] with [LlmRequestOptions.Legacy] — system +
     * user text are concatenated into a single prompt and sent as one user
     * turn. This preserves the exact behaviour every existing caller
     * (`:app:recipes`, `:app:medical`, `:feature:llm-task`) depends on,
     * since their parsers were tuned on the legacy prompt shape.
     *
     * The flow completes (with a [LlmTaskEvent.Complete] or
     * [LlmTaskEvent.Failed]) exactly once per call. Cancelling the
     * collector cancels the underlying generation.
     */
    fun <T> generateStream(
        engine: LlmEngine,
        config: LlmTaskConfig<T>,
        inputs: List<String>,
    ): Flow<LlmTaskEvent<T>> = generateStream(engine, config, inputs, LlmRequestOptions.Legacy)

    /**
     * Overload with extra [LlmRequest] knobs (`systemInstruction`,
     * `maxOutputTokens`, `temperature`, JSON-schema constrained decoding).
     *
     * Use when the prompt builder produces a stable system instruction
     * (recipes, recipe-clipping, medical advice) so the engine can keep
     * the system turn separate from the user turn: token budget and
     * sampling temperature can then be tuned independently of the legacy
     * concatenation contract.
     */
    fun <T> generateStream(
        engine: LlmEngine,
        config: LlmTaskConfig<T>,
        inputs: List<String>,
        options: LlmRequestOptions,
    ): Flow<LlmTaskEvent<T>>
}

/**
 * Extra knobs applied to the [LlmRequest] sent to the engine.
 *
 * @property systemInstruction when `true`, the prompt builder's
 *   `systemPrompt()` is sent as a separate `systemInstruction` turn and
 *   `userPrompt(inputs)` is sent as the user turn; when `false` (the
 *   legacy 3-arg behaviour used by `:feature:llm-task`, `:app:recipes`,
 *   `:app:medical`) both halves are concatenated into a single user turn.
 * @property maxOutputTokens hard cap on tokens generated for this call;
 *   `null` lets the engine run until EOS or the context window is exhausted.
 * @property temperature sampling temperature override; `null` keeps the
 *   engine default.
 * @property jsonSchema JSON Schema string for constrained decoding; `null`
 *   keeps free text.
 */
data class LlmRequestOptions(
    val systemInstruction: Boolean,
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    val jsonSchema: String? = null,
) {
    companion object {
        /**
         * The 3-arg overload's behaviour: `systemPrompt()` + `\n\n` +
         * `userPrompt(inputs)` as a single user turn, no tuning.
         * Defaults unchanged for existing callers.
         */
        val Legacy: LlmRequestOptions = LlmRequestOptions(systemInstruction = false)
    }
}

/**
 * Thrown (inside [com.llmlocal.core.domain.model.LlmTaskEvent.Failed]) when
 * the stream completed normally but the config's parser returned `null`.
 *
 * Distinguishes "the model answered, but not in the expected shape" from
 * engine / transport failures so callers can offer a different recovery
 * (deterministic fallback, "try the whole page", raw-output expander)
 * instead of a generic "model failed" error.
 *
 * @property rawOutput the complete text the model produced
 */
class LlmTaskParseException(val rawOutput: String) : IllegalStateException(
    "Parser returned null for completed stream. Expected at least a partial result.",
)
