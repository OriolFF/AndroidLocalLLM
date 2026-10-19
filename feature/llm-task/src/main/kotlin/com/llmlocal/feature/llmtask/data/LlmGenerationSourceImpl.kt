package com.llmlocal.feature.llmtask.data

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.domain.usecase.GenerateLlmTaskUseCase
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.feature.llmtask.domain.LlmGenerationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Initialises the real [LlmEngine] (idempotent) and streams via [GenerateLlmTaskUseCase]. */
internal class LlmGenerationSourceImpl(
    private val engine: LlmEngine,
    private val generate: GenerateLlmTaskUseCase,
) : LlmGenerationSource {

    override fun <T> generate(config: LlmTaskConfig<T>, inputs: List<String>): Flow<LlmTaskEvent<T>> = flow {
        when (val init = engine.initialize()) {
            is Outcome.Success -> emitAll(generate(engine, config, inputs))
            is Outcome.Failure -> emit(LlmTaskEvent.Failed(init.error))
        }
    }

    override fun cancel() = engine.cancel()
}
