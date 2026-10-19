package com.llmlocal.feature.llmtask.domain

import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.task.LlmTaskConfig
import kotlinx.coroutines.flow.Flow

/**
 * One interface over the on-device engine: initialises it (idempotent) and
 * streams a task generation. An initialisation failure surfaces as
 * [LlmTaskEvent.Failed].
 */
interface LlmGenerationSource {
    fun <T> generate(config: LlmTaskConfig<T>, inputs: List<String>): Flow<LlmTaskEvent<T>>

    /** Stops any in-flight generation. Safe to call when idle. */
    fun cancel()
}
