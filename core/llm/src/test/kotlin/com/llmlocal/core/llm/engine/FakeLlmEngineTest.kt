package com.llmlocal.core.llm.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.result.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakeLlmEngineTest {

    @Test
    fun `replays the scripted tokens and records the request`() = runTest {
        val engine = FakeLlmEngine.streaming("Title: Soup", chunkSize = 6)
        val request = LlmRequest(
            prompt = "soup please",
            systemInstruction = "Be brief.",
            maxOutputTokens = 64,
            jsonSchema = """{"type":"object"}""",
            temperature = 0.2,
        )

        engine.generateStream(request).test {
            assertThat(awaitItem()).isEqualTo(LlmToken.Partial("Title:"))
            assertThat(awaitItem()).isEqualTo(LlmToken.Partial(" Soup"))
            assertThat(awaitItem()).isEqualTo(LlmToken.Done)
            awaitComplete()
        }

        assertThat(engine.requests).containsExactly(request)
        assertThat(engine.lastRequest).isEqualTo(request)
    }

    @Test
    fun `String overload reaches the fake as a bare request`() = runTest {
        val engine = FakeLlmEngine.streaming("ok", chunkSize = 2)

        engine.generateStream("hello").test {
            assertThat(awaitItem()).isEqualTo(LlmToken.Partial("ok"))
            assertThat(awaitItem()).isEqualTo(LlmToken.Done)
            awaitComplete()
        }

        assertThat(engine.lastRequest).isEqualTo(LlmRequest("hello"))
    }

    @Test
    fun `token delay is honoured on virtual time`() = runTest {
        val engine = FakeLlmEngine(tokenDelayMs = 100) { FakeLlmEngine.chunks("abcd", chunkSize = 2) }

        val start = testScheduler.currentTime
        engine.generateStream("x").test {
            awaitItem()
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        assertThat(testScheduler.currentTime - start).isEqualTo(300)
    }

    @Test
    fun `failing script ends with an Error token`() = runTest {
        val boom = IllegalStateException("boom")
        val engine = FakeLlmEngine { FakeLlmEngine.failing(boom, text = "partial") }

        engine.generateStream("x").test {
            assertThat(awaitItem()).isEqualTo(LlmToken.Partial("partial"))
            assertThat(awaitItem()).isEqualTo(LlmToken.Error(boom))
            awaitComplete()
        }
    }

    @Test
    fun `initialize and cancel are counted`() = runTest {
        val engine = FakeLlmEngine(initializeOutcome = Outcome.Failure(RuntimeException("no model")))

        assertThat(engine.initialize().isFailure()).isTrue()
        assertThat(engine.initialize().isFailure()).isTrue()
        engine.cancel()

        assertThat(engine.initializeCalls).isEqualTo(2)
        assertThat(engine.cancelCalls).isEqualTo(1)
    }

    @Test
    fun `text-only fake rejects images with UnsupportedImagesException`() = runTest {
        val engine = FakeLlmEngine.streaming("x")
        val request = LlmRequest("p", images = listOf(LlmImage(byteArrayOf(1))))

        assertThat(engine.capabilities().vision).isFalse()
        engine.generateStream(request).test {
            val error = awaitItem() as LlmToken.Error
            assertThat(error.error).isInstanceOf(UnsupportedImagesException::class.java)
            awaitComplete()
        }
        assertThat(engine.lastRequest).isEqualTo(request)
    }

    @Test
    fun `vision fake accepts images and reports capabilities`() = runTest {
        val engine = FakeLlmEngine(vision = true) { FakeLlmEngine.chunks("ok") }
        val images = listOf(LlmImage(byteArrayOf(1)), LlmImage(byteArrayOf(2)))

        assertThat(engine.capabilities().vision).isTrue()
        engine.generateStream(LlmRequest("p", images = images)).test {
            assertThat(awaitItem()).isEqualTo(LlmToken.Partial("ok"))
            assertThat(awaitItem()).isEqualTo(LlmToken.Done)
            awaitComplete()
        }
        assertThat(engine.lastRequest!!.images).containsExactlyElementsIn(images).inOrder()
    }
}
