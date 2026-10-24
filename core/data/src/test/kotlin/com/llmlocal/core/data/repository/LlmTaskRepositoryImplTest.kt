package com.llmlocal.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.repository.LlmRequestOptions
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.domain.task.PromptBuilder
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.engine.LlmImage
import com.llmlocal.core.llm.engine.LlmRequest
import com.llmlocal.core.llm.engine.LlmToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LlmTaskRepositoryImplTest {

    private class RecordingEngine : LlmEngine {
        val requests = mutableListOf<LlmRequest>()
        override suspend fun initialize(): Outcome<Unit> = Outcome.Success(Unit)
        override fun generateStream(request: LlmRequest): Flow<LlmToken> {
            requests += request
            return flowOf(LlmToken.Partial("ok"), LlmToken.Done)
        }
        override fun cancel() = Unit
    }

    private val config = LlmTaskConfig(
        promptBuilder = object : PromptBuilder {
            override fun systemPrompt() = "SYS"
            override fun userPrompt(inputs: List<String>) = inputs.joinToString()
        },
        parser = { text: String -> text.takeIf { it.isNotEmpty() } },
    )

    @Test
    fun `options carry images and budget onto the request in order`() = runTest {
        val engine = RecordingEngine()
        val a = LlmImage(byteArrayOf(1))
        val b = LlmImage(byteArrayOf(2))
        val options = LlmRequestOptions(
            systemInstruction = true,
            images = listOf(a, b),
            visualTokenBudget = 280,
        )

        LlmTaskRepositoryImpl().generateStream(engine, config, listOf("x"), options).test {
            assertThat(awaitItem()).isInstanceOf(LlmTaskEvent.Token::class.java)
            assertThat(awaitItem()).isEqualTo(LlmTaskEvent.Complete("ok"))
            awaitComplete()
        }

        val request = engine.requests.single()
        assertThat(request.systemInstruction).isEqualTo("SYS")
        assertThat(request.prompt).isEqualTo("x")
        assertThat(request.images).containsExactly(a, b).inOrder()
        assertThat(request.visualTokenBudget).isEqualTo(280)
    }

    @Test
    fun `legacy call sends no images and no budget`() = runTest {
        val engine = RecordingEngine()

        LlmTaskRepositoryImpl().generateStream(engine, config, listOf("x")).test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        val request = engine.requests.single()
        assertThat(request.prompt).isEqualTo("SYS\n\nx")
        assertThat(request.images).isEmpty()
        assertThat(request.visualTokenBudget).isNull()
    }
}
