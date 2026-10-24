package com.llmlocal.core.llm.engine

import com.llmlocal.core.common.result.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scripted [LlmEngine] for unit tests.
 *
 * Every call to [generateStream] records the [LlmRequest] in [requests] and
 * replays the tokens produced by [script] for that request, pausing
 * [tokenDelayMs] between tokens (use `runTest`'s virtual time to make that
 * free). The fake never touches native code, so it is safe in plain JVM
 * tests of repositories, use cases and ViewModels.
 *
 * Typical usage:
 * ```
 * val engine = FakeLlmEngine { request -> FakeLlmEngine.chunks("Title: Soup\n", 4) }
 * repository.generateStream(engine, config, inputs).test { ... }
 * assertThat(engine.requests.single().jsonSchema).isNull()
 * ```
 */
class FakeLlmEngine(
    var initializeOutcome: Outcome<Unit> = Outcome.Success(Unit),
    var tokenDelayMs: Long = 0,
    private val vision: Boolean = false,
    private val script: (LlmRequest) -> List<LlmToken> = { listOf(LlmToken.Done) },
) : LlmEngine {

    /** Every request passed to [generateStream], oldest first. */
    val requests = mutableListOf<LlmRequest>()

    /** Number of [initialize] calls. */
    var initializeCalls = 0
        private set

    /** Number of [cancel] calls. */
    var cancelCalls = 0
        private set

    /** The last request, or `null` when nothing has been generated yet. */
    val lastRequest: LlmRequest?
        get() = requests.lastOrNull()

    override suspend fun initialize(): Outcome<Unit> {
        initializeCalls++
        return initializeOutcome
    }

    override fun capabilities(): LlmCapabilities =
        LlmCapabilities(vision = vision, maxImages = if (vision) 3 else 0)

    override fun generateStream(request: LlmRequest): Flow<LlmToken> = flow {
        requests += request
        if (request.images.isNotEmpty() && !vision) {
            emit(LlmToken.Error(UnsupportedImagesException("The loaded model or build cannot read images")))
            return@flow
        }
        for (token in script(request)) {
            if (tokenDelayMs > 0) delay(tokenDelayMs)
            emit(token)
        }
    }

    override fun cancel() {
        cancelCalls++
    }

    companion object {
        /**
         * Splits [text] into [LlmToken.Partial]s of at most [chunkSize]
         * characters followed by [LlmToken.Done] — the shape a healthy
         * streaming generation produces.
         */
        fun chunks(text: String, chunkSize: Int = 8): List<LlmToken> {
            require(chunkSize > 0) { "chunkSize must be positive" }
            return text.chunked(chunkSize).map { LlmToken.Partial(it) } + LlmToken.Done
        }

        /** A generation that streams [text] in full and then fails with [error]. */
        fun failing(error: Throwable, text: String = ""): List<LlmToken> =
            text.chunked(8).map { LlmToken.Partial(it) } + LlmToken.Error(error)

        /** An engine that always streams the same [text]. */
        fun streaming(text: String, chunkSize: Int = 8): FakeLlmEngine =
            FakeLlmEngine { chunks(text, chunkSize) }
    }
}
