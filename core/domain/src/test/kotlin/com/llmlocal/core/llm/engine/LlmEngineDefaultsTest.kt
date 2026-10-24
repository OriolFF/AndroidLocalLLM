package com.llmlocal.core.llm.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verifies the default-method contract on [LlmEngine]: an implementation
 * only overrides the [LlmRequest] overload and the String overload must
 * delegate to it with every optional field left at its default.
 */
class LlmEngineDefaultsTest {

    /** Minimal engine that records what it was asked and replays a fixed script. */
    private class RecordingEngine : LlmEngine {
        val requests = mutableListOf<LlmRequest>()
        var cancelled = 0

        override suspend fun initialize(): Outcome<Unit> = Outcome.Success(Unit)

        override fun generateStream(request: LlmRequest): Flow<LlmToken> {
            requests += request
            return flowOf(LlmToken.Partial("hi"), LlmToken.Done)
        }

        override fun cancel() {
            cancelled++
        }
    }

    @Test
    fun `String overload delegates to the request overload with defaults`() = runTest {
        val engine = RecordingEngine()

        engine.generateStream("ping").test {
            assertThat(awaitItem()).isEqualTo(LlmToken.Partial("hi"))
            assertThat(awaitItem()).isEqualTo(LlmToken.Done)
            awaitComplete()
        }

        assertThat(engine.requests).containsExactly(LlmRequest("ping"))
        val request = engine.requests.single()
        assertThat(request.prompt).isEqualTo("ping")
        assertThat(request.systemInstruction).isNull()
        assertThat(request.maxOutputTokens).isNull()
        assertThat(request.jsonSchema).isNull()
        assertThat(request.temperature).isNull()
    }

    @Test
    fun `request overload receives the request untouched`() = runTest {
        val engine = RecordingEngine()
        val request = LlmRequest(
            prompt = "extract",
            systemInstruction = "You are a parser.",
            maxOutputTokens = 256,
            jsonSchema = """{"type":"object"}""",
            temperature = 0.2,
        )

        engine.generateStream(request).test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        assertThat(engine.requests).containsExactly(request)
    }

    @Test
    fun `LlmRequest defaults every optional field to null`() {
        val request = LlmRequest("only prompt")

        assertThat(request).isEqualTo(
            LlmRequest(
                prompt = "only prompt",
                systemInstruction = null,
                maxOutputTokens = null,
                jsonSchema = null,
                temperature = null,
            ),
        )
    }

    @Test
    fun `capabilities default to text only`() {
        assertThat(RecordingEngine().capabilities()).isEqualTo(LlmCapabilities())
        assertThat(LlmCapabilities().vision).isFalse()
        assertThat(LlmCapabilities().maxImages).isEqualTo(0)
    }

    @Test
    fun `LlmRequest carries no images or budget by default and keeps image order`() {
        assertThat(LlmRequest("p").images).isEmpty()
        assertThat(LlmRequest("p").visualTokenBudget).isNull()

        val first = LlmImage(byteArrayOf(1))
        val second = LlmImage(byteArrayOf(2))
        val request = LlmRequest("p", images = listOf(first, second), visualTokenBudget = 280)
        assertThat(request.images).containsExactly(first, second).inOrder()
        assertThat(request.visualTokenBudget).isEqualTo(280)
    }
}
