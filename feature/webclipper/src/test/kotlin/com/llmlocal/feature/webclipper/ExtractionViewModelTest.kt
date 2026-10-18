package com.llmlocal.feature.webclipper

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.data.repository.LlmTaskRepositoryImpl
import com.llmlocal.core.domain.usecase.GenerateLlmTaskUseCase
import com.llmlocal.core.llm.engine.LlmToken
import com.llmlocal.feature.webclipper.fakes.FakeClipSink
import com.llmlocal.feature.webclipper.fakes.FakeLlmEngine
import com.llmlocal.feature.webclipper.fakes.FakePictureDownloader
import com.llmlocal.feature.webclipper.fakes.FakeWebClipConfig
import com.llmlocal.feature.webclipper.fakes.MainDispatcherRule
import com.llmlocal.feature.webclipper.fakes.Parsed
import com.llmlocal.feature.webclipper.fakes.TestDispatchers
import com.llmlocal.feature.webclipper.fakes.candidate
import com.llmlocal.feature.webclipper.fakes.snapshot
import com.llmlocal.feature.webclipper.mvi.ExtractionEffect
import com.llmlocal.feature.webclipper.mvi.ExtractionError
import com.llmlocal.feature.webclipper.mvi.ExtractionIntent
import com.llmlocal.feature.webclipper.mvi.Phase
import com.llmlocal.feature.webclipper.mvi.PhaseStatus
import com.llmlocal.feature.webclipper.mvi.PictureStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val session = ClipSession()
    private val sink = FakeClipSink()
    private val config = FakeWebClipConfig()
    private val useCase = GenerateLlmTaskUseCase(LlmTaskRepositoryImpl())

    private val goodOutput = "Title: Pasta\nIngredients:\n- 1 tomato\nSteps:\n1. Cook."

    @Suppress("UNCHECKED_CAST")
    private fun viewModel(
        candidateId: String,
        engine: FakeLlmEngine,
        downloader: FakePictureDownloader = FakePictureDownloader(),
        config: FakeWebClipConfig = this.config,
    ) = ExtractionViewModel(
        candidateId = candidateId,
        config = config as WebClipConfig<Any>,
        sink = sink as ClipSink<Any>,
        labels = labels,
        session = session,
        generateTask = useCase,
        engine = engine,
        pictureDownloader = downloader,
        tempDir = File(temp.root, "webclipper"),
        dispatchers = TestDispatchers(mainDispatcherRule.dispatcher),
    )

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
        assertThat(request.temperature).isEqualTo(ExtractionViewModel.EXTRACTION_TEMPERATURE)

        assertThat(sink.saved.single().item).isEqualTo(Parsed(goodOutput))
        assertThat(sink.saved.single().truncated).isFalse()
        assertThat(session.current.value).isNull()
        assertThat(session.activeExtraction.value).isNull()
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
    fun `parse failure on a STRUCTURED candidate uses the deterministic fallback`() = runTest {
        session.set(snapshot(), listOf(candidate("s", source = CandidateSource.STRUCTURED)))
        config.fallbackResult = Parsed("fallback")
        val engine = FakeLlmEngine.streaming("Sorry, no recipe here.", chunkSize = 6)
        val vm = viewModel("s", engine)

        vm.effects.test {
            advanceUntilIdle()
            awaitItem() // saved snackbar
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.NavigateToSaved("saved-1"))
        }
        assertThat(sink.saved.single().item).isEqualTo(Parsed("fallback"))
        assertThat(vm.state.value.error).isNull()
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
    fun `a parsed result the config rejects is treated as a parse failure`() = runTest {
        session.set(snapshot(), listOf(candidate("t"), candidate("s", source = CandidateSource.STRUCTURED)))
        config.rejected = setOf(Parsed(goodOutput))
        config.fallbackResult = Parsed("fallback")

        val textVm = viewModel("t", FakeLlmEngine.streaming(goodOutput, chunkSize = 5))
        advanceUntilIdle()
        assertThat(textVm.state.value.error).isEqualTo(ExtractionError.ParseFailed(goodOutput))
        assertThat(textVm.state.value.phases[Phase.Extracting]).isEqualTo(PhaseStatus.Failed)
        assertThat(config.acceptCalls.single().second).isEqualTo(Parsed(goodOutput))
        assertThat(sink.saved).isEmpty()

        val structuredVm = viewModel("s", FakeLlmEngine.streaming(goodOutput, chunkSize = 5))
        advanceUntilIdle()
        assertThat(structuredVm.state.value.error).isNull()
        assertThat(structuredVm.state.value.savedId).isEqualTo("saved-1")
        assertThat(sink.saved.single().item).isEqualTo(Parsed("fallback"))
    }

    @Test
    fun `switch off - a complete structured mapping still goes through the model`() = runTest {
        session.set(snapshot(), listOf(candidate("s", source = CandidateSource.STRUCTURED)))
        config.fallbackResult = Parsed("fallback")
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        val vm = viewModel("s", engine)
        advanceUntilIdle()

        assertThat(engine.requests).hasSize(1)
        assertThat(config.fallbackCalls).isEmpty()
        assertThat(sink.saved.single().item).isEqualTo(Parsed(goodOutput))
        assertThat(vm.state.value.extractedWithoutModel).isFalse()
    }

    @Test
    fun `switch on - a complete structured mapping is saved without running the model`() = runTest {
        session.set(snapshot(), listOf(candidate("s", source = CandidateSource.STRUCTURED)))
        val skipping = FakeWebClipConfig(skipModelWhenStructuredComplete = true, fallbackResult = Parsed("fallback"))
        skipping.pictureUrls = listOf("https://img.example/1.jpg")
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        val vm = viewModel("s", engine, config = skipping)

        vm.effects.test {
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.ShowSnackbar(labels.recipeSaved))
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.NavigateToSaved("saved-1"))
        }

        val state = vm.state.value
        assertThat(engine.requests).isEmpty()
        assertThat(skipping.renderedInputs).isEmpty()
        assertThat(skipping.fallbackCalls).hasSize(1)
        assertThat(state.extractedWithoutModel).isTrue()
        assertThat(state.streamedText).isEmpty()
        assertThat(state.truncated).isFalse()
        assertThat(state.phases.values).containsExactly(PhaseStatus.Done, PhaseStatus.Done, PhaseStatus.Done, PhaseStatus.Done)
        assertThat(state.pictures.single().status).isEqualTo(PictureStatus.Done)
        val saved = sink.saved.single()
        assertThat(saved.item).isEqualTo(Parsed("fallback"))
        assertThat(saved.pictures).hasSize(1)
        assertThat(saved.truncated).isFalse()
        assertThat(session.current.value).isNull()
    }

    @Test
    fun `switch on - an incomplete or missing mapping and TEXT candidates still run the model`() = runTest {
        session.set(snapshot(), listOf(candidate("s", source = CandidateSource.STRUCTURED), candidate("t")))
        val thin = FakeWebClipConfig(skipModelWhenStructuredComplete = true, fallbackResult = Parsed("thin"), complete = { it != Parsed("thin") })
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        val vm = viewModel("s", engine, config = thin)
        advanceUntilIdle()
        assertThat(engine.requests).hasSize(1)
        assertThat(thin.fallbackCalls).hasSize(1)
        assertThat(vm.state.value.extractedWithoutModel).isFalse()
        assertThat(sink.saved.single().item).isEqualTo(Parsed(goodOutput))

        // Saving clears the session; seed it again for each further case.
        session.set(snapshot(), listOf(candidate("s", source = CandidateSource.STRUCTURED), candidate("t")))
        val none = FakeWebClipConfig(skipModelWhenStructuredComplete = true, fallbackResult = null)
        val engine2 = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        viewModel("s", engine2, config = none)
        advanceUntilIdle()
        assertThat(engine2.requests).hasSize(1)

        session.set(snapshot(), listOf(candidate("s", source = CandidateSource.STRUCTURED), candidate("t")))
        val text = FakeWebClipConfig(skipModelWhenStructuredComplete = true, fallbackResult = Parsed("fallback"))
        val engine3 = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        viewModel("t", engine3, config = text)
        advanceUntilIdle()
        assertThat(engine3.requests).hasSize(1)
        assertThat(text.fallbackCalls).isEmpty()
        assertThat(sink.saved).hasSize(3)
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
    fun `picture failure is non-blocking and only kept successful files are saved`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        config.pictureUrls = listOf("https://img.example/1.jpg", "https://img.example/bad.jpg", "https://img.example/3.jpg", "https://img.example/4.jpg")
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        val vm = viewModel("a", engine, FakePictureDownloader(failing = setOf("https://img.example/bad.jpg")))
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.phases[Phase.Pictures]).isEqualTo(PhaseStatus.Warning)
        assertThat(state.phases[Phase.Saving]).isEqualTo(PhaseStatus.Done)
        assertThat(state.pictures.map { it.status }).containsExactly(
            PictureStatus.Done, PictureStatus.Failed, PictureStatus.Done, PictureStatus.Done,
        ).inOrder()
        // First three default to kept; the failed one has no file; the fourth is unchecked.
        assertThat(state.pictures.map { it.keep }).containsExactly(true, true, true, false).inOrder()
        val saved = sink.saved.single()
        assertThat(saved.pictures).hasSize(2)
        assertThat(saved.pictures.all { it.exists() }).isTrue()
        // Unkept temp file was removed.
        assertThat(tempFiles()).hasSize(2)
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
    fun `output near the cap without a clean ending is flagged truncated`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        val smallConfig = FakeWebClipConfig(maxOutputTokens = 32)
        val text = "Title: Pasta\nIngredients:\n- 400 g spaghetti\n- 4 ripe tomatoes\nSteps:\n1. Cook the pasta in plenty of salted water until al dente, then add the"
        assertThat(text.length / TruncationHeuristic.CHARS_PER_TOKEN).isAtLeast((32 * TruncationHeuristic.NEAR_CAP_RATIO).toInt())
        val engine = FakeLlmEngine.streaming(text, chunkSize = 7)
        @Suppress("UNCHECKED_CAST")
        val vm = ExtractionViewModel(
            candidateId = "a",
            config = smallConfig as WebClipConfig<Any>,
            sink = sink as ClipSink<Any>,
            labels = labels,
            session = session,
            generateTask = useCase,
            engine = engine,
            pictureDownloader = FakePictureDownloader(),
            tempDir = File(temp.root, "webclipper"),
            dispatchers = TestDispatchers(mainDispatcherRule.dispatcher),
        )
        advanceUntilIdle()

        assertThat(vm.state.value.truncated).isTrue()
        assertThat(vm.state.value.phases[Phase.Extracting]).isEqualTo(PhaseStatus.Warning)
        assertThat(vm.state.value.savedId).isEqualTo("saved-1")
        assertThat(sink.saved.single().truncated).isTrue()
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
    fun `empty session pops straight back`() = runTest {
        val engine = FakeLlmEngine.streaming(goodOutput)
        val vm = viewModel("ghost", engine)
        vm.effects.test {
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ExtractionEffect.PopToBrowser)
        }
        assertThat(engine.requests).isEmpty()
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

    @Test
    fun `stream completing without a terminal token is treated as a parse attempt`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        val engine = FakeLlmEngine { listOf(LlmToken.Partial("Title: x")) } // no Done
        val vm = viewModel("a", engine)
        advanceUntilIdle()
        assertThat(vm.state.value.error).isEqualTo(ExtractionError.ParseFailed("Title: x"))
    }
}
