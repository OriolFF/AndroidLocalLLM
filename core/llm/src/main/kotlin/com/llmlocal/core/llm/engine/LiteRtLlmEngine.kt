package com.llmlocal.core.llm.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.llmlocal.core.common.result.Outcome
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [LlmEngine] implementation backed by **LiteRT-LM** — Google's recommended
 * on-device LLM runtime, the successor to MediaPipe LLM Inference.
 *
 * Lifecycle:
 *  1. [initialize] creates the [Engine] (loads the model weights from disk)
 *     with a context window of [maxNumTokens] tokens.
 *  2. [generateStream] opens a fresh [Conversation] per call configured from
 *     the [LlmRequest] (system instruction, sampler temperature, output cap,
 *     optional JSON-schema constrained decoding), sends the prompt, and
 *     emits each streamed chunk as a [LlmToken.Partial] via
 *     `conversation.sendMessageAsync(...)`, which returns a `Flow<Message>`
 *     from LiteRT-LM.
 *  3. The conversation is closed in the `finally` block after streaming
 *     completes, fails, or is cancelled.
 *
 * Cancellation: LiteRT-LM's `Flow<Message>` is a `callbackFlow` whose
 * `awaitClose {}` body is empty, so cancelling the collector alone does
 * **not** stop native inference — the model keeps burning CPU/GPU until it
 * hits EOS. We therefore keep the live [Conversation] in [activeConversation]
 * and call `cancelProcess()` on it both from [cancel] and whenever the
 * collecting coroutine is cancelled. The JNI layer reports a cancelled
 * generation as a `java.util.concurrent.CancellationException`, which we
 * never surface as an [LlmToken.Error].
 *
 * LiteRT-LM's `Engine` is `AutoCloseable`; we hold it in an
 * [AtomicReference] and use a [Mutex] to serialize concurrent calls to
 * [generateStream] (LiteRT-LM does not support concurrent conversations on
 * the same engine instance).
 */
class LiteRtLlmEngine(
    @Suppress("unused") private val context: Context,
    private val modelPathProvider: () -> File,
    private val cacheDirProvider: () -> File = { context.cacheDir },
    private val maxNumTokens: Int = DEFAULT_MAX_NUM_TOKENS,
    private val topK: Int = DEFAULT_TOP_K,
    private val topP: Double = DEFAULT_TOP_P,
    private val temperature: Double = DEFAULT_TEMPERATURE,
    private val seed: Int = DEFAULT_SEED,
    private val backend: Backend = Backend.GPU(),
) : LlmEngine {

    private val tag = "LiteRtLlmEngine"
    private val mutex = Mutex()
    // Serializes initialize(). LlmTaskViewModel can trigger CheckModel twice
    // in quick succession at startup (init block + selection observer), and
    // without this lock both callers pass the `engineRef == null` check and
    // build two Engine instances — one of which leaks with its weights loaded.
    private val initMutex = Mutex()
    private val engineRef = AtomicReference<Engine?>(null)
    // The Conversation currently streaming, if any. Set under [mutex] for the
    // duration of one generateStream() call so cancel() can reach it.
    private val activeConversation = AtomicReference<Conversation?>(null)

    override suspend fun initialize(): Outcome<Unit> = withContext(Dispatchers.IO) {
        // Idempotent: once the engine is built we keep using it. The
        // LiteRT-LM `Engine` holds the loaded weights in memory and is
        // safe to share across calls (concurrent use is serialized by
        // [generateStream]'s mutex). Re-initializing on every re-check
        // would mark the model as "Failed" with "Engine already
        // initialized" whenever the user toggles demo mode or
        // re-triggers the model download.
        if (engineRef.get() != null) {
            return@withContext Outcome.Success(Unit)
        }
        initMutex.withLock {
            // Re-check under the lock: a concurrent caller may have finished.
            if (engineRef.get() != null) {
                return@withLock Outcome.Success(Unit)
            }
            buildEngine()
        }
    }

    private fun buildEngine(): Outcome<Unit> {
        return try {
            val modelFile = modelPathProvider()
            require(modelFile.exists()) {
                "Model file does not exist at ${modelFile.absolutePath}"
            }
            require(modelFile.length() > MIN_MODEL_BYTES) {
                "Model file is suspiciously small (${modelFile.length()} bytes). " +
                    "Did the download complete?"
            }

            // maxNumTokens is the total context window (prompt + output).
            // LiteRT-LM 0.18 clamps it to the bundle's static limit and logs
            // when it does; leaving it null makes the runtime pick
            // max(4096, prompt rounded up to a 4096 multiple), which is
            // unpredictable for memory budgeting, so we always set it.
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                maxNumTokens = maxNumTokens,
                cacheDir = cacheDirProvider().absolutePath,
            )
            val engine = Engine(config)
            engine.initialize()
            engineRef.set(engine)
            Log.i(
                tag,
                "Engine ready (model=${modelFile.length() / 1024 / 1024} MB, " +
                    "backend=${backend::class.simpleName}, maxNumTokens=$maxNumTokens)",
            )
            Outcome.Success(Unit)
        } catch (t: Throwable) {
            Outcome.Failure(t)
        }
    }

    override fun generateStream(request: LlmRequest): Flow<LlmToken> = flow {
        val engine = engineRef.get()
        if (engine == null) {
            emit(
                LlmToken.Error(
                    IllegalStateException("Engine not initialized — call initialize() first")
                )
            )
            return@flow
        }

        if (request.images.isNotEmpty()) {
            // I1 replaces this with the real vision path.
            emit(LlmToken.Error(UnsupportedImagesException("This engine does not read images yet")))
            return@flow
        }

        mutex.withLock {
            val conversationConfig = ConversationConfig(
                systemInstruction = request.systemInstruction?.let { Contents.of(it) },
                samplerConfig = SamplerConfig(
                    topK = topK,
                    topP = topP,
                    temperature = request.temperature ?: temperature,
                    seed = seed,
                ),
                maxOutputToken = request.maxOutputTokens,
                // Must be true for sendMessageAsync(responseFormat = ...) to be
                // honoured; LiteRT-LM rejects a responseFormat otherwise.
                enableResponseFormat = request.jsonSchema != null,
            )
            val responseFormat = request.jsonSchema?.let { ResponseFormat.json(it) }

            val conversation: Conversation = try {
                engine.createConversation(conversationConfig)
            } catch (t: Throwable) {
                emit(LlmToken.Error(t))
                return@withLock
            }
            activeConversation.set(conversation)

            try {
                // Flow<Message> — one Message per streamed chunk. The
                // streaming engine emits the assistant's reply as a
                // sequence of partial Messages; we unwrap the text
                // payloads and surface them as Partial tokens.
                conversation
                    .sendMessageAsync(Contents.of(request.prompt), responseFormat = responseFormat)
                    .collect { message ->
                        val text = message.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString(separator = "") { it.text }
                        if (text.isNotEmpty()) {
                            emit(LlmToken.Partial(text))
                        }
                    }
                runCatching { conversation.getTokenCount() }
                    .onSuccess { Log.d(tag, "Generation done (conversation tokens=$it)") }
                emit(LlmToken.Done)
            } catch (ce: CancellationException) {
                // Two ways to get here: (a) our collector was cancelled, so the
                // producer coroutine is no longer active; (b) cancel() called
                // cancelProcess() and the JNI callback closed the channel with
                // a CancellationException while the collector is still alive.
                // In both cases make sure native inference has stopped, and in
                // neither case report it as an error.
                stopNative(conversation)
                if (currentCoroutineContext().isActive) {
                    Log.i(tag, "Generation stopped via cancel()")
                } else {
                    throw ce
                }
            } catch (t: Throwable) {
                emit(LlmToken.Error(t))
            } finally {
                activeConversation.compareAndSet(conversation, null)
                runCatching { conversation.close() }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        activeConversation.get()?.let(::stopNative)
    }

    /**
     * Asks LiteRT-LM to abort the in-flight inference on [conversation].
     * Harmless when nothing is running; any native complaint is logged and
     * swallowed because the conversation is about to be closed anyway.
     */
    private fun stopNative(conversation: Conversation) {
        runCatching { conversation.cancelProcess() }
            .onFailure { Log.w(tag, "cancelProcess() failed: ${it.message}") }
    }

    companion object {
        /** Total context window (prompt + output) requested from the engine. */
        const val DEFAULT_MAX_NUM_TOKENS = 4096
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.95
        const val DEFAULT_TEMPERATURE = 0.7
        const val DEFAULT_SEED = 0
        const val MIN_MODEL_BYTES = 1L * 1024 * 1024 // 1 MB sanity floor
    }
}
