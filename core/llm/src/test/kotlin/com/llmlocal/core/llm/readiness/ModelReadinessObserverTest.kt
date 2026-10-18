package com.llmlocal.core.llm.readiness

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.engine.LlmToken
import com.llmlocal.core.llm.model.LlmModelCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behavioural tests for [ModelReadinessObserver], driven through its
 * internal constructor with in-memory fakes for the selection flow, the
 * per-model progress flow, the installed set and the engine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelReadinessObserverTest {

    private val defaultModel = LlmModelCatalog.DEFAULT_MODEL
    private val otherModel = LlmModelCatalog.ALL.first { it.id != defaultModel.id }

    // ---------------------------------------------------------------------
    // Fakes
    // ---------------------------------------------------------------------

    private class FakeEngine : LlmEngine {
        /** Results handed out by successive [initialize] calls; the last one repeats. */
        val results = ArrayDeque<Outcome<Unit>>()
        /** When set, [initialize] suspends until it completes. */
        var gate: CompletableDeferred<Unit>? = null
        var initializeCount = 0

        override suspend fun initialize(): Outcome<Unit> {
            initializeCount++
            gate?.await()
            return if (results.size > 1) results.removeFirst() else results.first()
        }

        override fun generateStream(prompt: String): Flow<LlmToken> = emptyFlow()
        override fun cancel() = Unit
    }

    /**
     * Collaborator fakes plus a scope on the test scheduler for the observer.
     * `runTest`'s `backgroundScope` is not used because `advanceUntilIdle`
     * stops once only background tasks remain, which would leave the
     * observer's collector parked.
     */
    private class Fixture(scheduler: TestCoroutineScheduler) {
        val selection = MutableStateFlow<String?>(LlmModelCatalog.DEFAULT_MODEL.id)
        val installed = mutableSetOf<String>()
        val engine = FakeEngine().apply { results += Outcome.Success(Unit) }
        private val progress = mutableMapOf<String, MutableStateFlow<PerModelProgress?>>()

        private val dispatcher: CoroutineDispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val dispatchers = object : DispatcherProvider {
            override val io = dispatcher
            override val default = dispatcher
            override val main = dispatcher
        }

        fun progressFlow(id: String): MutableStateFlow<PerModelProgress?> =
            progress.getOrPut(id) { MutableStateFlow(null) }

        fun setProgress(id: String, state: DownloadState, bytes: Long = 0L) {
            progressFlow(id).value = PerModelProgress(
                modelId = id,
                state = state,
                bytesRead = bytes,
                totalBytes = TOTAL_BYTES,
                speedBytesPerSec = 0L,
                etaSeconds = null,
                updatedAt = System.nanoTime(),
            )
        }

        fun observer() = ModelReadinessObserver(
            selectedModelId = selection,
            progressFor = { id -> progressFlow(id) },
            isInstalled = { descriptor -> descriptor.id in installed },
            engine = engine,
            dispatchers = dispatchers,
        )

        private companion object {
            const val TOTAL_BYTES = 2_580L * 1024 * 1024
        }
    }

    /** Runs [block] against a fresh [Fixture] and cancels its scope afterwards. */
    private fun observerTest(block: suspend kotlinx.coroutines.test.TestScope.(Fixture) -> Unit) = runTest {
        val f = Fixture(testScheduler)
        try {
            block(f)
        } finally {
            f.scope.cancel()
        }
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    @Test
    fun `fresh install with default selected and no file yields NoModel`() = observerTest { f ->
        val readiness = f.observer().observe(f.scope)

        readiness.test {
            assertThat(awaitItem()).isEqualTo(ModelReadiness.Checking)
            advanceUntilIdle()
            assertThat(expectMostRecentItem()).isEqualTo(ModelReadiness.NoModel)
        }
        assertThat(f.engine.initializeCount).isEqualTo(0)
    }

    @Test
    fun `download SUCCEEDED for the already-selected default model yields Ready`() = observerTest { f ->
        val readiness = f.observer().observe(f.scope)

        readiness.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem()).isEqualTo(ModelReadiness.NoModel)

            f.setProgress(defaultModel.id, DownloadState.RUNNING, bytes = 1_000_000L)
            advanceUntilIdle()
            val downloading = expectMostRecentItem()
            assertThat(downloading).isInstanceOf(ModelReadiness.Downloading::class.java)
            assertThat((downloading as ModelReadiness.Downloading).progress.bytesRead)
                .isEqualTo(1_000_000L)
            assertThat(f.engine.initializeCount).isEqualTo(0)

            // The worker renames the .part file and writes SUCCEEDED; the
            // selection never re-emits, so this is the only trigger.
            f.installed += defaultModel.id
            f.setProgress(defaultModel.id, DownloadState.SUCCEEDED)
            advanceUntilIdle()
            assertThat(expectMostRecentItem()).isEqualTo(ModelReadiness.Ready(defaultModel))
        }
        assertThat(f.engine.initializeCount).isEqualTo(1)
    }

    @Test
    fun `installed model passes through Initializing before Ready`() = observerTest { f ->
        f.installed += defaultModel.id
        f.setProgress(defaultModel.id, DownloadState.SUCCEEDED)
        f.engine.gate = CompletableDeferred()
        val readiness = f.observer().observe(f.scope)

        advanceUntilIdle()
        assertThat(readiness.value).isEqualTo(ModelReadiness.Initializing)

        f.engine.gate!!.complete(Unit)
        advanceUntilIdle()
        assertThat(readiness.value).isEqualTo(ModelReadiness.Ready(defaultModel))
    }

    @Test
    fun `selection change re-checks the newly selected model`() = observerTest { f ->
        f.installed += defaultModel.id
        f.setProgress(defaultModel.id, DownloadState.SUCCEEDED)
        val readiness = f.observer().observe(f.scope)

        readiness.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem()).isEqualTo(ModelReadiness.Ready(defaultModel))

            f.selection.value = otherModel.id
            advanceUntilIdle()
            assertThat(expectMostRecentItem()).isEqualTo(ModelReadiness.NoModel)

            f.installed += otherModel.id
            f.setProgress(otherModel.id, DownloadState.SUCCEEDED)
            advanceUntilIdle()
            assertThat(expectMostRecentItem()).isEqualTo(ModelReadiness.Ready(otherModel))
        }
        assertThat(f.engine.initializeCount).isEqualTo(2)
    }

    @Test
    fun `unknown selection id yields NoModel without touching the engine`() = observerTest { f ->
        f.selection.value = "no-such-model"
        val readiness = f.observer().observe(f.scope)

        advanceUntilIdle()
        assertThat(readiness.value).isEqualTo(ModelReadiness.NoModel)
        assertThat(f.engine.initializeCount).isEqualTo(0)
    }

    @Test
    fun `engine init failure yields Failed with reason and descriptor`() = observerTest { f ->
        f.installed += defaultModel.id
        f.setProgress(defaultModel.id, DownloadState.SUCCEEDED)
        f.engine.results.clear()
        f.engine.results += Outcome.Failure(IllegalStateException("model file corrupted"))
        val readiness = f.observer().observe(f.scope)

        advanceUntilIdle()
        assertThat(readiness.value).isEqualTo(
            ModelReadiness.Failed(reason = "model file corrupted", descriptor = defaultModel),
        )
    }

    @Test
    fun `engine init failure without a message still carries a reason`() = observerTest { f ->
        f.installed += defaultModel.id
        f.setProgress(defaultModel.id, DownloadState.SUCCEEDED)
        f.engine.results.clear()
        f.engine.results += Outcome.Failure(RuntimeException())
        val readiness = f.observer().observe(f.scope)

        advanceUntilIdle()
        val failed = readiness.value as ModelReadiness.Failed
        assertThat(failed.reason).isNotEmpty()
    }

    @Test
    fun `engine init throwing is reported as Failed, not a crash`() = observerTest { f ->
        f.installed += defaultModel.id
        f.setProgress(defaultModel.id, DownloadState.SUCCEEDED)
        val throwingEngine = object : LlmEngine {
            override suspend fun initialize(): Outcome<Unit> = throw UnsatisfiedLinkError("libllm.so")
            override fun generateStream(prompt: String): Flow<LlmToken> = emptyFlow()
            override fun cancel() = Unit
        }
        val observer = ModelReadinessObserver(
            selectedModelId = f.selection,
            progressFor = { id -> f.progressFlow(id) },
            isInstalled = { descriptor -> descriptor.id in f.installed },
            engine = throwingEngine,
            dispatchers = f.dispatchers,
        )
        val readiness = observer.observe(f.scope)

        advanceUntilIdle()
        val failed = readiness.value as ModelReadiness.Failed
        assertThat(failed.reason).isEqualTo("libllm.so")
        assertThat(failed.descriptor).isEqualTo(defaultModel)
    }

    @Test
    fun `recheck after Failed yields Ready once the engine recovers`() = observerTest { f ->
        f.installed += defaultModel.id
        f.setProgress(defaultModel.id, DownloadState.SUCCEEDED)
        f.engine.results.clear()
        f.engine.results += Outcome.Failure(IllegalStateException("transient"))
        f.engine.results += Outcome.Success(Unit)
        val observer = f.observer()
        val readiness = observer.observe(f.scope)

        advanceUntilIdle()
        assertThat(readiness.value).isInstanceOf(ModelReadiness.Failed::class.java)

        observer.recheck()
        advanceUntilIdle()
        assertThat(readiness.value).isEqualTo(ModelReadiness.Ready(defaultModel))
        assertThat(f.engine.initializeCount).isEqualTo(2)
    }

    @Test
    fun `model removed from disk yields NoModel on the next progress write`() = observerTest { f ->
        f.installed += defaultModel.id
        f.setProgress(defaultModel.id, DownloadState.SUCCEEDED)
        val readiness = f.observer().observe(f.scope)

        advanceUntilIdle()
        assertThat(readiness.value).isEqualTo(ModelReadiness.Ready(defaultModel))

        // Model management removes the file and clears its progress entry.
        f.installed -= defaultModel.id
        f.progressFlow(defaultModel.id).value = null
        advanceUntilIdle()
        assertThat(readiness.value).isEqualTo(ModelReadiness.NoModel)
    }

    @Test
    fun `model removed from disk yields NoModel on recheck`() = observerTest { f ->
        f.installed += defaultModel.id
        f.setProgress(defaultModel.id, DownloadState.SUCCEEDED)
        val observer = f.observer()
        val readiness = observer.observe(f.scope)

        advanceUntilIdle()
        assertThat(readiness.value).isEqualTo(ModelReadiness.Ready(defaultModel))

        f.installed -= defaultModel.id
        observer.recheck()
        advanceUntilIdle()
        assertThat(readiness.value).isEqualTo(ModelReadiness.NoModel)
    }

    @Test
    fun `cancelled download with no file yields NoModel`() = observerTest { f ->
        f.setProgress(defaultModel.id, DownloadState.RUNNING, bytes = 10L)
        val readiness = f.observer().observe(f.scope)

        advanceUntilIdle()
        assertThat(readiness.value).isInstanceOf(ModelReadiness.Downloading::class.java)

        f.setProgress(defaultModel.id, DownloadState.CANCELLED)
        advanceUntilIdle()
        assertThat(readiness.value).isEqualTo(ModelReadiness.NoModel)
        assertThat(f.engine.initializeCount).isEqualTo(0)
    }

    @Test
    fun `observe is idempotent and initialises the engine once`() = observerTest { f ->
        f.installed += defaultModel.id
        f.setProgress(defaultModel.id, DownloadState.SUCCEEDED)
        val observer = f.observer()

        val a = observer.observe(f.scope)
        val b = observer.observe(f.scope)
        advanceUntilIdle()

        assertThat(a).isSameInstanceAs(b)
        assertThat(a.value).isEqualTo(ModelReadiness.Ready(defaultModel))
        assertThat(f.engine.initializeCount).isEqualTo(1)
    }

}
