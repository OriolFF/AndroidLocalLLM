package com.llmlocal.feature.webclipper.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.data.repository.LlmTaskRepositoryImpl
import com.llmlocal.core.domain.usecase.GenerateLlmTaskUseCase
import com.llmlocal.core.llm.engine.LlmToken
import com.llmlocal.feature.webclipper.data.CacheDirTempPictureStore
import com.llmlocal.feature.webclipper.data.EngineLlmGenerationSource
import com.llmlocal.feature.webclipper.data.InMemoryClipSession
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.WHOLE_PAGE_CANDIDATE_ID
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.domain.usecase.CancelClipUseCase
import com.llmlocal.feature.webclipper.domain.usecase.CheckDuplicateUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ClipUseCase
import com.llmlocal.feature.webclipper.fakes.FakeClipSink
import com.llmlocal.feature.webclipper.fakes.FakeLlmEngine
import com.llmlocal.feature.webclipper.fakes.FakePictureDownloader
import com.llmlocal.feature.webclipper.fakes.FakeWebClipConfig
import com.llmlocal.feature.webclipper.fakes.MainDispatcherRule
import com.llmlocal.feature.webclipper.fakes.Parsed
import com.llmlocal.feature.webclipper.fakes.TestDispatchers
import com.llmlocal.feature.webclipper.fakes.candidate
import com.llmlocal.feature.webclipper.fakes.snapshot
import com.llmlocal.feature.webclipper.presentation.mvi.ExtractionEffect
import com.llmlocal.feature.webclipper.domain.ExtractionError
import com.llmlocal.feature.webclipper.presentation.mvi.ExtractionIntent
import com.llmlocal.feature.webclipper.domain.Phase
import com.llmlocal.feature.webclipper.domain.PhaseStatus
import com.llmlocal.feature.webclipper.domain.PictureStatus
import com.llmlocal.feature.webclipper.domain.PicturePick
import com.llmlocal.feature.webclipper.domain.ClipSaveOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ExtractionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val labels = WebClipperLabels()
    private val session: ClipSession = InMemoryClipSession()
    private val sink = FakeClipSink()
    private val config = FakeWebClipConfig()
    private val useCase = GenerateLlmTaskUseCase(LlmTaskRepositoryImpl())

    private val goodOutput = "Title: Pasta\nIngredients:\n- 1 tomato\nSteps:\n1. Cook."

    @Suppress("UNCHECKED_CAST")
    private fun TestScope.viewModel(
        candidateId: String,
        engine: FakeLlmEngine,
        downloader: FakePictureDownloader = FakePictureDownloader(),
        config: FakeWebClipConfig = this@ExtractionViewModelTest.config,
    ): ExtractionViewModel {
        val dispatchers = TestDispatchers(mainDispatcherRule.dispatcher)
        val llm = EngineLlmGenerationSource(engine, useCase)
        return ExtractionViewModel(
            candidateId = candidateId,
            config = config as WebClipConfig<Any>,
            sink = sink as ClipSink<Any>,
            labels = labels,
            session = session,
            clip = ClipUseCase(llm, downloader, CacheDirTempPictureStore(File(temp.root, "webclipper")), session, dispatchers),
            checkDuplicate = CheckDuplicateUseCase(dispatchers),
            cancelClip = CancelClipUseCase(llm, session),
            dispatchers = dispatchers,
        )
    }

    private fun tempFiles(): List<File> = File(temp.root, "webclipper").listFiles()?.toList().orEmpty()

    @Test
    fun `happy path streams, saves and navigates with the right request`() = runTest {
        val candidates = listOf(candidate("a"), candidate("b"))
        session.set(snapshot(), candidates)
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        val vm = viewModel("a", engine)

        vm.effects.test {
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.ShowSnackbar(labels.recipeSaved))
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.NavigateToSaved("saved-1"))
        }

        val state = vm.state.value
        assertThat(state.streamedText).isEqualTo(goodOutput)
        assertThat(state.isRunning).isFalse()
        assertThat(state.savedId).isEqualTo("saved-1")
        assertThat(state.otherCandidates).isEqualTo(1)
        assertThat(state.sourceHost).isEqualTo("cooking.example")
        assertThat(state.phases.values).containsExactly(PhaseStatus.Done, PhaseStatus.Done, PhaseStatus.Done, PhaseStatus.Done)
        assertThat(state.truncated).isFalse()

        val request = engine.requests.single()
        assertThat(request.systemInstruction).isEqualTo("SYSTEM")
        assertThat(request.prompt).isEqualTo("INPUT:a")
        assertThat(request.maxOutputTokens).isEqualTo(1_024)
        assertThat(request.temperature).isEqualTo(EngineLlmGenerationSource.EXTRACTION_TEMPERATURE)

        assertThat(sink.saved.single().item).isEqualTo(Parsed(goodOutput))
        assertThat(sink.saved.single().truncated).isFalse()
        assertThat(session.current.value).isNull()
        assertThat(session.activeExtraction.value).isNull()

        // The duplicate check ran once, found nothing, and no dialog was shown.
        assertThat(sink.existingIdCalls).containsExactly(snapshot().url)
        assertThat(state.duplicateOf).isNull()
        assertThat(sink.saved.single().options.replaceId).isNull()
    }

    // -- duplicate warning (WS10 bug 6) ----------------------------------------

    @Test
    fun `an already clipped page shows the duplicate dialog before anything runs`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        sink.existing = "old-id"
        config.pictureUrls = listOf("https://img.example/1.jpg")
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        val vm = viewModel("a", engine)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.duplicateOf).isEqualTo("old-id")
        assertThat(state.isRunning).isFalse()
        assertThat(state.phases.values.toSet()).containsExactly(PhaseStatus.Pending)
        assertThat(engine.requests).isEmpty()
        assertThat(state.pictures).isEmpty()
        assertThat(session.activeExtraction.value).isNull()
    }

    @Test
    fun `Replace runs the extraction and saves under the existing id`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        sink.existing = "old-id"
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        val vm = viewModel("a", engine)
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(ExtractionIntent.ReplaceExisting)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.ShowSnackbar(labels.recipeSaved))
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.NavigateToSaved("saved-1"))
        }
        assertThat(vm.state.value.duplicateOf).isNull()
        assertThat(vm.state.value.replaceId).isEqualTo("old-id")
        assertThat(engine.requests).hasSize(1)
        assertThat(sink.saved.single().options.replaceId).isEqualTo("old-id")
    }

    @Test
    fun `Keep both runs the extraction as a new item`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        sink.existing = "old-id"
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        val vm = viewModel("a", engine)
        advanceUntilIdle()

        vm.onIntent(ExtractionIntent.KeepBoth)
        advanceUntilIdle()
        assertThat(vm.state.value.duplicateOf).isNull()
        assertThat(vm.state.value.replaceId).isNull()
        assertThat(vm.state.value.savedId).isEqualTo("saved-1")
        assertThat(sink.saved.single().options.replaceId).isNull()
    }

    @Test
    fun `Cancel on the duplicate dialog pops without running or a cancelled snackbar`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        sink.existing = "old-id"
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        val vm = viewModel("a", engine)
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(ExtractionIntent.Cancel)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.PopToBrowser)
            expectNoEvents()
        }
        assertThat(engine.requests).isEmpty()
        assertThat(sink.saved).isEmpty()
        assertThat(vm.state.value.isRunning).isFalse()
    }

    @Test
    fun `a failing duplicate check is treated as no duplicate`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        sink.existingIdFailure = IllegalStateException("db locked")
        val vm = viewModel("a", FakeLlmEngine.streaming(goodOutput, chunkSize = 5))
        advanceUntilIdle()
        assertThat(vm.state.value.duplicateOf).isNull()
        assertThat(vm.state.value.savedId).isEqualTo("saved-1")
    }

    @Test
    fun `cancel stops the engine, deletes temp files and pops`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        config.pictureUrls = listOf("https://img.example/1.jpg", "https://img.example/2.jpg")
        val engine = FakeLlmEngine(tokenDelayMs = 100) { FakeLlmEngine.chunks(goodOutput, 5) }
        val vm = viewModel("a", engine, FakePictureDownloader(delayMs = 50))

        advanceTimeBy(250)
        assertThat(vm.state.value.isRunning).isTrue()
        assertThat(vm.state.value.streamedText).isNotEmpty()
        assertThat(session.activeExtraction.value).isEqualTo("a")
        assertThat(tempFiles()).isNotEmpty()

        vm.effects.test {
            vm.onIntent(ExtractionIntent.Cancel)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.ShowSnackbar(labels.clipCancelled))
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.PopToBrowser)
        }
        assertThat(engine.cancelCalls).isEqualTo(1)
        assertThat(tempFiles()).isEmpty()
        assertThat(sink.saved).isEmpty()
        assertThat(vm.state.value.isRunning).isFalse()
        assertThat(session.activeExtraction.value).isNull()
        assertThat(vm.state.value.phases.values).doesNotContain(PhaseStatus.Active)
    }

    @Test
    fun `parse failure on a TEXT candidate shows ParseFailed with the raw output`() = runTest {
        session.set(snapshot(), listOf(candidate("t")))
        config.fallbackResult = Parsed("should-not-be-used")
        val engine = FakeLlmEngine.streaming("Sorry, no recipe here.", chunkSize = 6)
        val vm = viewModel("t", engine)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.error).isEqualTo(ExtractionError.ParseFailed("Sorry, no recipe here."))
        assertThat(state.phases[Phase.Extracting]).isEqualTo(PhaseStatus.Failed)
        assertThat(state.isRunning).isFalse()
        assertThat(sink.saved).isEmpty()
        assertThat(session.activeExtraction.value).isNull()
    }

    @Test
    fun `retry whole page after a parse failure re-runs with the synthetic candidate`() = runTest {
        session.set(snapshot(), listOf(candidate("t")))
        var calls = 0
        val engine = FakeLlmEngine { if (calls++ == 0) FakeLlmEngine.chunks("nope", 4) else FakeLlmEngine.chunks(goodOutput, 5) }
        val vm = viewModel("t", engine)
        advanceUntilIdle()
        assertThat(vm.state.value.error).isInstanceOf(ExtractionError.ParseFailed::class.java)

        vm.onIntent(ExtractionIntent.RetryWholePage)
        advanceUntilIdle()
        assertThat(vm.state.value.candidate.id).isEqualTo(WHOLE_PAGE_CANDIDATE_ID)
        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.savedId).isEqualTo("saved-1")
        assertThat(engine.requests.last().prompt).isEqualTo("INPUT:$WHOLE_PAGE_CANDIDATE_ID")
    }

    @Test
    fun `toggling a picture excludes it from the save`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        config.pictureUrls = listOf("https://img.example/1.jpg")
        val engine = FakeLlmEngine(tokenDelayMs = 100) { FakeLlmEngine.chunks(goodOutput, 5) }
        val vm = viewModel("a", engine)
        advanceTimeBy(150)
        vm.onIntent(ExtractionIntent.TogglePicture("https://img.example/1.jpg"))
        advanceUntilIdle()
        assertThat(sink.saved.single().pictures).isEmpty()
        assertThat(tempFiles()).isEmpty()
    }

    @Test
    fun `engine error yields ModelFailed and a missing model file yields ModelMissing`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        val engine = FakeLlmEngine { FakeLlmEngine.failing(IllegalStateException("boom"), text = "Title") }
        val vm = viewModel("a", engine)
        advanceUntilIdle()
        assertThat(vm.state.value.error).isEqualTo(ExtractionError.ModelFailed("boom"))
        assertThat(vm.state.value.phases[Phase.Extracting]).isEqualTo(PhaseStatus.Failed)

        val missing = FakeLlmEngine(initializeOutcome = Outcome.Failure(IllegalArgumentException("Model file does not exist at /x")))
        val vm2 = viewModel("a", missing)
        advanceUntilIdle()
        assertThat(vm2.state.value.error).isEqualTo(ExtractionError.ModelMissing)
        assertThat(missing.requests).isEmpty()
    }

    @Test
    fun `empty session explains itself and pops straight back`() = runTest {
        val engine = FakeLlmEngine.streaming(goodOutput)
        val vm = viewModel("ghost", engine)
        vm.effects.test {
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.ShowSnackbar(labels.scanFailed))
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.PopToBrowser)
        }
        assertThat(engine.requests).isEmpty()
        assertThat(vm.state.value.isRunning).isFalse()
    }

    @Test
    fun `retry after the page was dropped from the session pops with the snackbar`() = runTest {
        session.set(snapshot(), listOf(candidate("t")))
        val vm = viewModel("t", FakeLlmEngine.streaming("nope", chunkSize = 4))
        advanceUntilIdle()
        assertThat(vm.state.value.error).isInstanceOf(ExtractionError.ParseFailed::class.java)

        session.clearPage()
        vm.effects.test {
            vm.onIntent(ExtractionIntent.Retry)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.ShowSnackbar(labels.scanFailed))
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.PopToBrowser)
        }
        assertThat(vm.state.value.isRunning).isFalse()
    }

    @Test
    fun `back to candidates requests the sheet and pops`() = runTest {
        session.set(snapshot(), listOf(candidate("a"), candidate("b")))
        val engine = FakeLlmEngine(tokenDelayMs = 100) { FakeLlmEngine.chunks(goodOutput, 5) }
        val vm = viewModel("a", engine)
        advanceTimeBy(150)
        vm.effects.test {
            vm.onIntent(ExtractionIntent.BackToCandidates)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.PopToBrowser)
        }
        assertThat(session.reopenCandidates.value).isTrue()
        assertThat(session.activeExtraction.value).isNull()
        assertThat(engine.cancelCalls).isEqualTo(1)
    }
}
