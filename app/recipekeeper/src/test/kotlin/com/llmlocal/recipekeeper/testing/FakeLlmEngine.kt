package com.llmlocal.recipekeeper.testing

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.engine.LlmRequest
import com.llmlocal.core.llm.engine.LlmToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scripted [LlmEngine] for unit tests — a copy of
 * `core/llm/src/test/.../FakeLlmEngine.kt` (test sources are not shared
 * between modules). Keep the two in sync.
 */
class FakeLlmEngine(
    var initializeOutcome: Outcome<Unit> = Outcome.Success(Unit),
    var tokenDelayMs: Long = 0,
    private val script: (LlmRequest) -> List<LlmToken> = { listOf(LlmToken.Done) },
) : LlmEngine {

    val requests = mutableListOf<LlmRequest>()

    var initializeCalls = 0
        private set

    var cancelCalls = 0
        private set

    val lastRequest: LlmRequest?
        get() = requests.lastOrNull()

    override suspend fun initialize(): Outcome<Unit> {
        initializeCalls++
        return initializeOutcome
    }

    override fun generateStream(request: LlmRequest): Flow<LlmToken> = flow {
        requests += request
        for (token in script(request)) {
            if (tokenDelayMs > 0) delay(tokenDelayMs)
            emit(token)
        }
    }

    override fun cancel() {
        cancelCalls++
    }

    companion object {
        fun chunks(text: String, chunkSize: Int = 8): List<LlmToken> {
            require(chunkSize > 0) { "chunkSize must be positive" }
            return text.chunked(chunkSize).map { LlmToken.Partial(it) } + LlmToken.Done
        }

        fun failing(error: Throwable, text: String = ""): List<LlmToken> =
            text.chunked(8).map { LlmToken.Partial(it) } + LlmToken.Error(error)

        fun streaming(text: String, chunkSize: Int = 8): FakeLlmEngine =
            FakeLlmEngine { chunks(text, chunkSize) }
    }
}
