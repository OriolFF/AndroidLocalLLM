package com.llmlocal.core.data.repository

import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.repository.LlmTaskRepository
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.engine.LlmToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.transform

/**
 * [LlmTaskRepository] implementation that uses the on-device LLM.
 *
 * It wires together:
 *  - [LlmEngine]        — generates the text (injected per call so the
 *    caller can swap between the real engine and a fake / demo one)
 *  - [com.llmlocal.core.domain.task.PromptBuilder] (carried inside [config])
 *    — formats the prompt
 *  - [com.llmlocal.core.domain.task.TaskParser] (carried inside [config])
 *    — extracts the structured result `T`
 *
 * The streamed text is *cumulative* — we keep a running buffer and re-parse
 * it on every new chunk so the UI can render a partial result. The final
 * [LlmTaskEvent.Complete] is emitted when the LLM reports `done` and the
 * parser produces a non-null result.
 *
 * If the parser returns `null` at `done` (the LLM output didn't conform to
 * the expected schema), we emit [LlmTaskEvent.Failed] so the UI can show an
 * error rather than silently swallowing the failure.
 */
class LlmTaskRepositoryImpl : LlmTaskRepository {

    override fun <T> generateStream(
        engine: LlmEngine,
        config: LlmTaskConfig<T>,
        inputs: List<String>,
    ): Flow<LlmTaskEvent<T>> {
        val prompt = buildString {
            append(config.promptBuilder.systemPrompt())
            append("\n\n")
            append(config.promptBuilder.userPrompt(inputs))
        }

        val buffer = StringBuilder()

        return engine.generateStream(prompt)
            .transform { token ->
                when (token) {
                    is LlmToken.Partial -> {
                        buffer.append(token.text)
                        emit(LlmTaskEvent.Token(token.text))
                    }
                    LlmToken.Done -> {
                        val parsed = config.parser.parse(buffer.toString())
                        if (parsed != null) {
                            emit(LlmTaskEvent.Complete(parsed))
                        } else {
                            emit(
                                LlmTaskEvent.Failed(
                                    IllegalStateException(
                                        "Parser returned null for completed stream. " +
                                            "Expected at least a partial result.",
                                    ),
                                ),
                            )
                        }
                    }
                    is LlmToken.Error -> {
                        emit(LlmTaskEvent.Failed(token.error))
                    }
                }
            }
            .catch { e -> emit(LlmTaskEvent.Failed(e)) }
    }
}