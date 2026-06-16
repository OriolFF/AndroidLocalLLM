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
import com.google.ai.edge.litertlm.SamplerConfig
import com.llmlocal.core.common.result.Outcome
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [LlmEngine] implementation backed by **LiteRT-LM** — Google's recommended
 * on-device LLM runtime, the successor to MediaPipe LLM Inference.
 *
 * Lifecycle:
 *  1. [initialize] creates the [Engine] (loads the model weights from disk).
 *  2. [generateStream] opens a fresh [Conversation] per call, sends the
 *     prompt, and emits each token as a [LlmToken.Partial] via
 *     `conversation.sendMessageAsync(...)`, which returns a
 *     `Flow<Message>` from LiteRT-LM.
 *  3. The conversation is closed in the `finally` block after streaming
 *     completes (or is cancelled).
 *
 * LiteRT-LM's `Engine` is `AutoCloseable`; we hold it in an
 * [AtomicReference] and use a [Mutex] to serialize concurrent calls to
 * [generateStream] (LiteRT-LM does not yet support concurrent
 * conversations on the same engine instance).
 */
class LiteRtLlmEngine(
    @Suppress("unused") private val context: Context,
    private val modelPathProvider: () -> File,
    private val cacheDirProvider: () -> File = { context.cacheDir },
    private val maxTokens: Int = DEFAULT_MAX_TOKENS,
    private val topK: Int = DEFAULT_TOP_K,
    private val topP: Double = DEFAULT_TOP_P,
    private val temperature: Double = DEFAULT_TEMPERATURE,
    private val seed: Int = DEFAULT_SEED,
    private val backend: Backend = Backend.GPU(),
) : LlmEngine {

    private val tag = "LiteRtLlmEngine"
    private val mutex = Mutex()
    private val engineRef = AtomicReference<Engine?>(null)

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
        try {
            val modelFile = modelPathProvider()
            require(modelFile.exists()) {
                "Model file does not exist at ${modelFile.absolutePath}"
            }
            require(modelFile.length() > MIN_MODEL_BYTES) {
                "Model file is suspiciously small (${modelFile.length()} bytes). " +
                    "Did the download complete?"
            }

            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                cacheDir = cacheDirProvider().absolutePath,
            )
            val engine = Engine(config)
            engine.initialize()
            engineRef.set(engine)
            Log.i(
                tag,
                "Engine ready (model=${modelFile.length() / 1024 / 1024} MB, " +
                    "backend=${backend::class.simpleName})",
            )
            Outcome.Success(Unit)
        } catch (t: Throwable) {
            Outcome.Failure(t)
        }
    }

    override fun generateStream(prompt: String): Flow<LlmToken> = flow {
        val engine = engineRef.get()
        if (engine == null) {
            emit(
                LlmToken.Error(
                    IllegalStateException("Engine not initialized — call initialize() first")
                )
            )
            return@flow
        }

        mutex.withLock {
            val conversationConfig = ConversationConfig(
                systemInstruction = null,
                samplerConfig = SamplerConfig(
                    topK = topK,
                    topP = topP,
                    temperature = temperature,
                    seed = seed,
                ),
            )

            val conversation: Conversation = try {
                engine.createConversation(conversationConfig)
            } catch (t: Throwable) {
                emit(LlmToken.Error(t))
                return@withLock
            }

            try {
                // Flow<Message> — one Message per streamed chunk. The
                // streaming engine emits the assistant's reply as a
                // sequence of partial Messages; we unwrap the text
                // payloads and surface them as Partial tokens.
                conversation.sendMessageAsync(Contents.of(prompt), emptyMap()).collect { message ->
                    val text = message.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString(separator = "") { it.text }
                    if (text.isNotEmpty()) {
                        emit(LlmToken.Partial(text))
                    }
                }
                emit(LlmToken.Done)
            } catch (t: Throwable) {
                emit(LlmToken.Error(t))
            } finally {
                runCatching { conversation.close() }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        // LiteRT-LM does not expose an explicit cancel API on Conversation;
        // the caller's coroutine cancellation will close the channel and
        // release the conversation.
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 1024
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.95
        const val DEFAULT_TEMPERATURE = 0.7
        const val DEFAULT_SEED = 0
        const val MIN_MODEL_BYTES = 1L * 1024 * 1024 // 1 MB sanity floor
    }
}
