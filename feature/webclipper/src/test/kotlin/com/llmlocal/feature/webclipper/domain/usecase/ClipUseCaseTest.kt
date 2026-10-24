package com.llmlocal.feature.webclipper.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.data.repository.LlmTaskRepositoryImpl
import com.llmlocal.core.domain.usecase.GenerateLlmTaskUseCase
import com.llmlocal.core.llm.engine.LlmToken
import com.llmlocal.feature.webclipper.data.CacheDirTempPictureStore
import com.llmlocal.feature.webclipper.data.EngineLlmGenerationSource
import com.llmlocal.feature.webclipper.data.InMemoryClipSession
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.feature.webclipper.domain.IMAGES_CANDIDATE_ID
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.feature.webclipper.fakes.preparedImage
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.feature.webclipper.domain.ClipProgress
import com.llmlocal.feature.webclipper.domain.ClipRequest
import com.llmlocal.feature.webclipper.domain.ClipSaveOptions
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.ExtractionError
import com.llmlocal.feature.webclipper.domain.Phase
import com.llmlocal.feature.webclipper.domain.PhaseStatus
import com.llmlocal.feature.webclipper.domain.PicturePick
import com.llmlocal.feature.webclipper.domain.PictureDownloader
import com.llmlocal.feature.webclipper.domain.PictureStatus
import com.llmlocal.feature.webclipper.domain.TruncationHeuristic
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.fakes.FakeClipSink
import com.llmlocal.feature.webclipper.fakes.FakeLlmEngine
import com.llmlocal.feature.webclipper.fakes.FakePictureDownloader
import com.llmlocal.feature.webclipper.fakes.FakeWebClipConfig
import com.llmlocal.feature.webclipper.fakes.Parsed
import com.llmlocal.feature.webclipper.fakes.TestDispatchers
import com.llmlocal.feature.webclipper.fakes.candidate
import com.llmlocal.feature.webclipper.fakes.snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ClipUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val session: ClipSession = InMemoryClipSession()
    private val sink = FakeClipSink()
    private val config = FakeWebClipConfig()
    private val generateTask = GenerateLlmTaskUseCase(LlmTaskRepositoryImpl())

    private val goodOutput = "Title: Pasta\nIngredients:\n- 1 tomato\nSteps:\n1. Cook."

    private fun TestScope.clipUseCase(
        engine: FakeLlmEngine,
        downloader: PictureDownloader = FakePictureDownloader(),
    ): ClipUseCase<Any> = ClipUseCase(
        llm = EngineLlmGenerationSource(engine, generateTask),
        pictureDownloader = downloader,
        tempPictures = CacheDirTempPictureStore(tempDir),
        session = session,
        dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
    )

    private val tempDir get() = File(temp.root, "webclipper")

    private fun tempFiles(): List<File> = tempDir.listFiles()?.toList().orEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun TestScope.run(
        candidate: ClipCandidate,
        engine: FakeLlmEngine,
        downloader: PictureDownloader = FakePictureDownloader(),
        config: FakeWebClipConfig = this@ClipUseCaseTest.config,
        replaceId: String? = null,
        isPictureKept: (String) -> Boolean = { true },
    ) = clipUseCase(engine, downloader)(
        config as WebClipConfig<Any>,
        sink as ClipSink<Any>,
        ClipRequest(candidate, snapshot(), replaceId, isPictureKept),
    )

    private suspend fun kotlinx.coroutines.flow.Flow<ClipProgress>.events() = toList()

    private fun List<ClipProgress>.phase(phase: Phase) =
        filterIsInstance<ClipProgress.PhaseChanged>().last { it.phase == phase }.status

    @Test
    fun `happy path streams, saves and reports the request sent to the model`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)

        val events = run(candidate("a"), engine).events()

        assertThat(events.filterIsInstance<ClipProgress.Streamed>().joinToString("") { it.delta }).isEqualTo(goodOutput)
        assertThat(events.last()).isEqualTo(ClipProgress.Saved("saved-1"))
        Phase.entries.forEach { assertThat(events.phase(it)).isEqualTo(PhaseStatus.Done) }
        assertThat(events).contains(ClipProgress.Truncated(false))

        val request = engine.requests.single()
        assertThat(request.systemInstruction).isEqualTo("SYSTEM")
        assertThat(request.prompt).isEqualTo("INPUT:a")
        assertThat(request.maxOutputTokens).isEqualTo(1_024)
        assertThat(request.temperature).isEqualTo(EngineLlmGenerationSource.EXTRACTION_TEMPERATURE)

        assertThat(sink.saved.single().item).isEqualTo(Parsed(goodOutput))
        assertThat(sink.saved.single().options.replaceId).isNull()
        assertThat(session.current.value).isNull()
        assertThat(session.activeExtraction.value).isNull()
    }

    @Test
    fun `the extraction marker is set while running and the replace id reaches the sink`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        val engine = FakeLlmEngine(tokenDelayMs = 100) { FakeLlmEngine.chunks(goodOutput, 5) }
        val job = launch { run(candidate("a"), engine, replaceId = "old-id").events() }
        advanceTimeBy(150)
        assertThat(session.activeExtraction.value).isEqualTo("a")
        advanceUntilIdle()
        job.join()
        assertThat(sink.saved.single().options.replaceId).isEqualTo("old-id")
    }

    @Test
    fun `cancelling the collector deletes temp files and releases the marker`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        config.pictureUrls = listOf("https://img.example/1.jpg", "https://img.example/2.jpg")
        val engine = FakeLlmEngine(tokenDelayMs = 100) { FakeLlmEngine.chunks(goodOutput, 5) }
        val seen = mutableListOf<ClipProgress>()
        val job: Job = launch { run(candidate("a"), engine, FakePictureDownloader(delayMs = 50)).collect { seen += it } }

        advanceTimeBy(250)
        assertThat(seen.filterIsInstance<ClipProgress.Streamed>()).isNotEmpty()
        assertThat(session.activeExtraction.value).isEqualTo("a")
        assertThat(tempFiles()).isNotEmpty()

        job.cancel()
        advanceUntilIdle()
        assertThat(tempFiles()).isEmpty()
        assertThat(session.activeExtraction.value).isNull()
        assertThat(sink.saved).isEmpty()
    }

    @Test
    fun `parse failure on a STRUCTURED candidate uses the deterministic fallback`() = runTest {
        val structured = candidate("s", source = CandidateSource.STRUCTURED)
        session.set(snapshot(), listOf(structured))
        config.fallbackResult = Parsed("fallback")

        val events = run(structured, FakeLlmEngine.streaming("Sorry, no recipe here.", chunkSize = 6)).events()

        assertThat(events.last()).isEqualTo(ClipProgress.Saved("saved-1"))
        assertThat(events.filterIsInstance<ClipProgress.Failed>()).isEmpty()
        assertThat(sink.saved.single().item).isEqualTo(Parsed("fallback"))
    }

    @Test
    fun `parse failure on a TEXT candidate fails with the raw output`() = runTest {
        val text = candidate("t")
        session.set(snapshot(), listOf(text))
        config.fallbackResult = Parsed("should-not-be-used")

        val events = run(text, FakeLlmEngine.streaming("Sorry, no recipe here.", chunkSize = 6)).events()

        assertThat(events.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ParseFailed("Sorry, no recipe here.")))
        assertThat(sink.saved).isEmpty()
        assertThat(session.activeExtraction.value).isNull()
    }

    @Test
    fun `a parsed result the config rejects is treated as a parse failure`() = runTest {
        val text = candidate("t")
        val structured = candidate("s", source = CandidateSource.STRUCTURED)
        session.set(snapshot(), listOf(text, structured))
        config.rejected = setOf(Parsed(goodOutput))
        config.fallbackResult = Parsed("fallback")

        val textEvents = run(text, FakeLlmEngine.streaming(goodOutput, chunkSize = 5)).events()
        assertThat(textEvents.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ParseFailed(goodOutput)))
        assertThat(config.acceptCalls.single().second).isEqualTo(Parsed(goodOutput))
        assertThat(sink.saved).isEmpty()

        val structuredEvents = run(structured, FakeLlmEngine.streaming(goodOutput, chunkSize = 5)).events()
        assertThat(structuredEvents.last()).isEqualTo(ClipProgress.Saved("saved-1"))
        assertThat(sink.saved.single().item).isEqualTo(Parsed("fallback"))
    }

    @Test
    fun `switch off - a complete structured mapping still goes through the model`() = runTest {
        val structured = candidate("s", source = CandidateSource.STRUCTURED)
        session.set(snapshot(), listOf(structured))
        config.fallbackResult = Parsed("fallback")
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)

        val events = run(structured, engine).events()

        assertThat(engine.requests).hasSize(1)
        assertThat(config.fallbackCalls).isEmpty()
        assertThat(sink.saved.single().item).isEqualTo(Parsed(goodOutput))
        assertThat(events).doesNotContain(ClipProgress.ExtractedWithoutModel)
    }

    @Test
    fun `switch on - a complete structured mapping is saved without running the model`() = runTest {
        val structured = candidate("s", source = CandidateSource.STRUCTURED)
        session.set(snapshot(), listOf(structured))
        val skipping = FakeWebClipConfig(skipModelWhenStructuredComplete = true, fallbackResult = Parsed("fallback"))
        skipping.pictureUrls = listOf("https://img.example/1.jpg")
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)

        val events = run(structured, engine, config = skipping).events()

        assertThat(engine.requests).isEmpty()
        assertThat(skipping.renderedInputs).isEmpty()
        assertThat(skipping.fallbackCalls).hasSize(1)
        assertThat(events).contains(ClipProgress.ExtractedWithoutModel)
        assertThat(events.filterIsInstance<ClipProgress.Streamed>()).isEmpty()
        assertThat(events.filterIsInstance<ClipProgress.Truncated>()).isEmpty()
        Phase.entries.forEach { assertThat(events.phase(it)).isEqualTo(PhaseStatus.Done) }
        assertThat(events.filterIsInstance<ClipProgress.PictureChanged>().last().status).isEqualTo(PictureStatus.Done)
        val saved = sink.saved.single()
        assertThat(saved.item).isEqualTo(Parsed("fallback"))
        assertThat(saved.pictures).hasSize(1)
        assertThat(saved.truncated).isFalse()
        assertThat(session.current.value).isNull()
    }

    @Test
    fun `switch on - an incomplete or missing mapping and TEXT candidates still run the model`() = runTest {
        val structured = candidate("s", source = CandidateSource.STRUCTURED)
        val text = candidate("t")
        val thin = FakeWebClipConfig(skipModelWhenStructuredComplete = true, fallbackResult = Parsed("thin"), complete = { it != Parsed("thin") })
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        run(structured, engine, config = thin).events()
        assertThat(engine.requests).hasSize(1)
        assertThat(thin.fallbackCalls).hasSize(1)
        assertThat(sink.saved.single().item).isEqualTo(Parsed(goodOutput))

        val none = FakeWebClipConfig(skipModelWhenStructuredComplete = true, fallbackResult = null)
        val engine2 = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        run(structured, engine2, config = none).events()
        assertThat(engine2.requests).hasSize(1)

        val textConfig = FakeWebClipConfig(skipModelWhenStructuredComplete = true, fallbackResult = Parsed("fallback"))
        val engine3 = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        run(text, engine3, config = textConfig).events()
        assertThat(engine3.requests).hasSize(1)
        assertThat(textConfig.fallbackCalls).isEmpty()
        assertThat(sink.saved).hasSize(3)
    }

    @Test
    fun `picture failure is non-blocking and only kept successful files are saved`() = runTest {
        config.pictureUrls = listOf("https://img.example/1.jpg", "https://img.example/bad.jpg", "https://img.example/3.jpg", "https://img.example/4.jpg")
        val downloader = FakePictureDownloader(failing = setOf("https://img.example/bad.jpg"))
        // The use case asks the *current* checkbox state: pick the three initially-checked ones.
        val checked = setOf("https://img.example/1.jpg", "https://img.example/bad.jpg", "https://img.example/3.jpg")

        val events = run(candidate("a"), FakeLlmEngine.streaming(goodOutput, chunkSize = 5), downloader, isPictureKept = { it in checked }).events()

        assertThat(events.phase(Phase.Pictures)).isEqualTo(PhaseStatus.Warning)
        assertThat(events.phase(Phase.Saving)).isEqualTo(PhaseStatus.Done)
        val proposed = events.filterIsInstance<ClipProgress.PicturesProposed>().single().pictures
        assertThat(proposed.map { it.keep }).containsExactly(true, true, true, false).inOrder()
        val saved = sink.saved.single()
        assertThat(saved.pictures).hasSize(2)
        assertThat(saved.pictures.all { it.exists() }).isTrue()
        assertThat(tempFiles()).hasSize(2) // the fourth, unkept download was removed
        assertThat(saved.options.picturesFailed).isFalse()
        assertThat(saved.options.pictureCandidates).isEqualTo(config.pictureUrls)
    }

    @Test
    fun `when every download fails the save is flagged picturesFailed with the picks`() = runTest {
        config.pictureUrls = listOf("https://img.example/1.jpg", "https://img.example/2.jpg")
        val offline = FakePictureDownloader(failing = config.pictureUrls.toSet())

        val events = run(candidate("a"), FakeLlmEngine.streaming(goodOutput, chunkSize = 5), offline).events()

        assertThat(events.phase(Phase.Pictures)).isEqualTo(PhaseStatus.Warning)
        assertThat(events.last()).isEqualTo(ClipProgress.Saved("saved-1"))
        val saved = sink.saved.single()
        assertThat(saved.pictures).isEmpty()
        assertThat(saved.options.picturesFailed).isTrue()
        assertThat(saved.options.pictureCandidates).containsExactly("https://img.example/1.jpg", "https://img.example/2.jpg").inOrder()
    }

    @Test
    fun `pictures phase settles as soon as the downloads finish, while the model is still running`() = runTest {
        config.pictureUrls = listOf("https://img.example/1.jpg", "https://img.example/bad.jpg")
        val slowEngine = FakeLlmEngine(tokenDelayMs = 1_000) { FakeLlmEngine.chunks(goodOutput, 5) }
        val seen = mutableListOf<ClipProgress>()
        launch {
            run(candidate("a"), slowEngine, FakePictureDownloader(failing = setOf("https://img.example/bad.jpg"), delayMs = 50))
                .collect { seen += it }
        }

        advanceTimeBy(200)
        // Prefill is still running: Reading stays Active until the first delta (V11).
        assertThat(seen.phase(Phase.Reading)).isEqualTo(PhaseStatus.Active)
        assertThat(seen.phase(Phase.Pictures)).isEqualTo(PhaseStatus.Warning)
        assertThat(seen.filterIsInstance<ClipProgress.Saved>()).isEmpty()

        advanceUntilIdle()
        assertThat(seen.last()).isEqualTo(ClipProgress.Saved("saved-1"))
    }

    @Test
    fun `no proposed pictures is not a failure`() = runTest {
        val events = run(candidate("a"), FakeLlmEngine.streaming(goodOutput, chunkSize = 5)).events()
        assertThat(events.phase(Phase.Pictures)).isEqualTo(PhaseStatus.Done)
        assertThat(sink.saved.single().options).isEqualTo(ClipSaveOptions())
    }

    @Test
    fun `only preferred picks start checked, at most three, non-preferred stay unchecked`() = runTest {
        config.picturePicks = listOf(
            PicturePick("https://img.example/own-1.jpg", preferred = true),
            PicturePick("https://img.example/neighbour.jpg", preferred = false),
            PicturePick("https://img.example/own-2.jpg", preferred = true),
            PicturePick("https://img.example/own-3.jpg", preferred = true),
            PicturePick("https://img.example/own-4.jpg", preferred = true),
            PicturePick("https://img.example/own-1.jpg", preferred = false), // duplicate url, dropped
        )
        val events = run(candidate("a"), FakeLlmEngine.streaming(goodOutput, chunkSize = 5)).events()

        val proposed = events.filterIsInstance<ClipProgress.PicturesProposed>().single().pictures
        assertThat(proposed.map { it.url }).containsExactly(
            "https://img.example/own-1.jpg", "https://img.example/neighbour.jpg", "https://img.example/own-2.jpg",
            "https://img.example/own-3.jpg", "https://img.example/own-4.jpg",
        ).inOrder()
        assertThat(proposed.map { it.keep }).containsExactly(true, false, true, true, false).inOrder()
    }

    @Test
    fun `a config without preferred information keeps the first three checked`() = runTest {
        config.pictureUrls = listOf("https://img.example/1.jpg", "https://img.example/2.jpg", "https://img.example/3.jpg", "https://img.example/4.jpg")
        val events = run(candidate("a"), FakeLlmEngine.streaming(goodOutput, chunkSize = 5)).events()
        val proposed = events.filterIsInstance<ClipProgress.PicturesProposed>().single().pictures
        assertThat(proposed.map { it.keep }).containsExactly(true, true, true, false).inOrder()
    }

    @Test
    fun `an unticked picture is excluded from the save and its temp file removed`() = runTest {
        config.pictureUrls = listOf("https://img.example/1.jpg")
        run(candidate("a"), FakeLlmEngine.streaming(goodOutput, chunkSize = 5), isPictureKept = { false }).events()
        assertThat(sink.saved.single().pictures).isEmpty()
        assertThat(tempFiles()).isEmpty()
    }

    @Test
    fun `output near the cap without a clean ending is flagged truncated`() = runTest {
        val smallConfig = FakeWebClipConfig(maxOutputTokens = 32)
        val text = "Title: Pasta\nIngredients:\n- 400 g spaghetti\n- 4 ripe tomatoes\nSteps:\n1. Cook the pasta in plenty of salted water until al dente, then add the"
        assertThat(text.length / TruncationHeuristic.CHARS_PER_TOKEN).isAtLeast((32 * TruncationHeuristic.NEAR_CAP_RATIO).toInt())

        val events = run(candidate("a"), FakeLlmEngine.streaming(text, chunkSize = 7), config = smallConfig).events()

        assertThat(events).contains(ClipProgress.Truncated(true))
        assertThat(events.phase(Phase.Extracting)).isEqualTo(PhaseStatus.Warning)
        assertThat(sink.saved.single().truncated).isTrue()
    }

    @Test
    fun `engine error yields ModelFailed and a missing model file yields ModelMissing`() = runTest {
        val failing = FakeLlmEngine { FakeLlmEngine.failing(IllegalStateException("boom"), text = "Title") }
        val events = run(candidate("a"), failing).events()
        assertThat(events.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ModelFailed("boom")))

        val missing = FakeLlmEngine(initializeOutcome = Outcome.Failure(IllegalArgumentException("Model file does not exist at /x")))
        val events2 = run(candidate("a"), missing).events()
        assertThat(events2.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ModelMissing))
        assertThat(missing.requests).isEmpty()
    }

    @Test
    fun `a sink that throws fails the run and cleans up`() = runTest {
        config.pictureUrls = listOf("https://img.example/1.jpg")
        sink.failWith = IllegalStateException("disk full")
        val events = run(candidate("a"), FakeLlmEngine.streaming(goodOutput, chunkSize = 5)).events()
        assertThat(events.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ModelFailed("disk full")))
        assertThat(tempFiles()).isEmpty()
        assertThat(session.activeExtraction.value).isNull()
    }

    @Test
    fun `stream completing without a terminal token is treated as a parse attempt`() = runTest {
        val engine = FakeLlmEngine { listOf(LlmToken.Partial("Title: x")) } // no Done
        val events = run(candidate("a"), engine).events()
        assertThat(events.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ParseFailed("Title: x")))
    }

    @Test
    fun `web - Reading stays Active until the first streamed delta, then Extracting takes over`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        val events = run(candidate("a"), FakeLlmEngine.streaming(goodOutput, chunkSize = 5)).events()

        val firstDelta = events.indexOfFirst { it is ClipProgress.Streamed }
        val readingActive = events.indexOf(ClipProgress.PhaseChanged(Phase.Reading, PhaseStatus.Active))
        val readingDone = events.indexOf(ClipProgress.PhaseChanged(Phase.Reading, PhaseStatus.Done))
        val extractingActive = events.indexOf(ClipProgress.PhaseChanged(Phase.Extracting, PhaseStatus.Active))
        assertThat(readingActive).isAtLeast(0)
        assertThat(readingDone).isGreaterThan(readingActive)
        assertThat(readingDone).isLessThan(firstDelta)
        assertThat(extractingActive).isGreaterThan(readingDone)
        assertThat(extractingActive).isLessThan(firstDelta)
    }

    @Test
    fun `a model run that streams nothing still settles Reading and fails in Extracting`() = runTest {
        session.set(snapshot(), listOf(candidate("a")))
        val events = run(candidate("a"), FakeLlmEngine { listOf(LlmToken.Done) }).events()

        assertThat(events.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ParseFailed("")))
        assertThat(events.phase(Phase.Reading)).isEqualTo(PhaseStatus.Done)
        assertThat(events.phase(Phase.Extracting)).isEqualTo(PhaseStatus.Active)
    }

    // -- images ----------------------------------------------------------------

    private fun TestScope.runImages(
        images: List<com.llmlocal.feature.webclipper.domain.input.ClipImage>,
        engine: FakeLlmEngine,
        config: FakeWebClipConfig = this@ClipUseCaseTest.config,
        downloader: FakePictureDownloader = FakePictureDownloader(),
        isPictureKept: (String) -> Boolean = { true },
    ): kotlinx.coroutines.flow.Flow<ClipProgress> {
        val input = ClipInput.Images(images)
        val candidate = config.candidatesFor(input, "Clip from images").single()
        session.set(input, listOf(candidate))
        @Suppress("UNCHECKED_CAST")
        return clipUseCase(engine, downloader)(
            config as WebClipConfig<Any>,
            sink as ClipSink<Any>,
            ClipRequest(candidate, input, null, isPictureKept),
        )
    }

    private fun images(n: Int) = List(n) { preparedImage(tempDir, it) }

    @Test
    fun `images - the model gets the pictures in order before the prompt, at the policy budget`() = runTest {
        val pictures = images(3)
        val engine = FakeLlmEngine.streaming(goodOutput, chunkSize = 5)
        val downloader = FakePictureDownloader()

        val events = runImages(pictures, engine, downloader = downloader).events()

        val request = engine.requests.single()
        assertThat(request.images.map { it.bytes.first().toInt() }).containsExactly(0, 1, 2).inOrder()
        assertThat(request.images.first().mimeType).isEqualTo("image/jpeg")
        assertThat(request.images.first().width).isEqualTo(100)
        assertThat(request.visualTokenBudget).isEqualTo(280)
        assertThat(request.prompt).isEqualTo(com.llmlocal.feature.webclipper.domain.DEFAULT_IMAGES_PROMPT)
        assertThat(downloader.requests).isEmpty()
        assertThat(events.last()).isEqualTo(ClipProgress.Saved("saved-1"))
        Phase.entries.forEach { assertThat(events.phase(it)).isEqualTo(PhaseStatus.Done) }
    }

    @Test
    fun `images - Reading stays Active until the first token and the pictures are proposed as local Done files`() = runTest {
        val pictures = images(2)
        val events = runImages(pictures, FakeLlmEngine.streaming(goodOutput, chunkSize = 5)).events()

        val firstDelta = events.indexOfFirst { it is ClipProgress.Streamed }
        assertThat(events.indexOf(ClipProgress.PhaseChanged(Phase.Reading, PhaseStatus.Done))).isLessThan(firstDelta)
        assertThat(events.indexOf(ClipProgress.PhaseChanged(Phase.Reading, PhaseStatus.Active))).isLessThan(
            events.indexOf(ClipProgress.PhaseChanged(Phase.Reading, PhaseStatus.Done)),
        )

        val proposed = events.filterIsInstance<ClipProgress.PicturesProposed>().single()
        assertThat(proposed.pictures.map { it.keep }).containsExactly(true, true)
        val done = events.filterIsInstance<ClipProgress.PictureChanged>()
        assertThat(done.map { it.status }).containsExactly(PictureStatus.Done, PictureStatus.Done)
        assertThat(done.map { it.localFile }).containsExactlyElementsIn(pictures.map { it.file }).inOrder()
        assertThat(proposed.pictures.map { it.url }).containsExactlyElementsIn(done.map { it.url }).inOrder()
    }

    @Test
    fun `images - save receives the Images input and every kept file, and clears the session`() = runTest {
        val pictures = images(2)
        val events = runImages(pictures, FakeLlmEngine.streaming(goodOutput)).events()

        assertThat(events.last()).isEqualTo(ClipProgress.Saved("saved-1"))
        assertThat(sink.savedInputs.single()).isEqualTo(ClipInput.Images(pictures))
        assertThat(sink.saved.single().pictures).containsExactlyElementsIn(pictures.map { it.file }).inOrder()
        assertThat(sink.saved.single().options.pictureCandidates).hasSize(2)
        assertThat(session.current.value).isNull()
        assertThat(session.activeExtraction.value).isNull()
        pictures.forEach { assertThat(it.file.exists()).isTrue() } // moved by the sink, not deleted
    }

    @Test
    fun `images - an unticked picture is not saved and its file is removed`() = runTest {
        val pictures = images(2)
        val dropped = pictures[1].file.toURI().toString()

        runImages(pictures, FakeLlmEngine.streaming(goodOutput), isPictureKept = { it != dropped }).events()

        assertThat(sink.saved.single().pictures).containsExactly(pictures[0].file)
        assertThat(pictures[1].file.exists()).isFalse()
    }

    @Test
    fun `images - an unparseable answer is ParseFailed with no fallback and the pictures stay on disk`() = runTest {
        config.fallbackResult = Parsed("must not be used")
        val pictures = images(1)

        val events = runImages(pictures, FakeLlmEngine.streaming("NO RECIPE")).events()

        assertThat(events.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ParseFailed("NO RECIPE")))
        assertThat(config.fallbackCalls).isEmpty()
        assertThat(sink.saved).isEmpty()
        assertThat(pictures.single().file.exists()).isTrue()
        assertThat(session.activeExtraction.value).isNull()
    }

    @Test
    fun `images - a result the config rejects is ParseFailed`() = runTest {
        config.rejected = setOf(Parsed(goodOutput))
        val events = runImages(images(1), FakeLlmEngine.streaming(goodOutput)).events()
        assertThat(events.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ParseFailed(goodOutput)))
    }

    @Test
    fun `images - an engine without vision fails with ImagesUnsupported during Reading`() = runTest {
        val engine = FakeLlmEngine { FakeLlmEngine.failing(com.llmlocal.core.llm.engine.UnsupportedImagesException("text only")) }
        val pictures = images(2)

        val events = runImages(pictures, engine).events()

        assertThat(events.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ImagesUnsupported))
        assertThat(events.phase(Phase.Reading)).isEqualTo(PhaseStatus.Active)
        assertThat(engine.requests).hasSize(1) // no retry for an engine error
        pictures.forEach { assertThat(it.file.exists()).isTrue() }
    }

    @Test
    fun `images - cancelling the collector deletes the pictures and releases the marker`() = runTest {
        val pictures = images(2)
        val slow = FakeLlmEngine(tokenDelayMs = 1_000) { FakeLlmEngine.chunks(goodOutput, 5) }
        val job = launch { runImages(pictures, slow).collect { } }

        advanceTimeBy(500)
        assertThat(session.activeExtraction.value).isEqualTo(IMAGES_CANDIDATE_ID)
        job.cancel()
        advanceUntilIdle()

        pictures.forEach { assertThat(it.file.exists()).isFalse() }
        assertThat(session.activeExtraction.value).isNull()
        assertThat(sink.saved).isEmpty()
    }

    @Test
    fun `images guardrail - NO RECIPE over three pictures retries once with the first two`() = runTest {
        val pictures = images(3)
        val engine = FakeLlmEngine { request ->
            if (request.images.size == 3) FakeLlmEngine.chunks("NO RECIPE") else FakeLlmEngine.chunks(goodOutput)
        }

        val events = runImages(pictures, engine).events()

        assertThat(engine.requests.map { it.images.size }).containsExactly(3, 2).inOrder()
        assertThat(engine.requests[1].images.map { it.bytes.first().toInt() }).containsExactly(0, 1).inOrder()
        assertThat(events).contains(ClipProgress.Retrying(2))
        assertThat(events.last()).isEqualTo(ClipProgress.Saved("saved-1"))
        // the retry restarts at Reading and flips again on its first delta
        assertThat(events.filter { it == ClipProgress.PhaseChanged(Phase.Reading, PhaseStatus.Active) }).hasSize(2)
        // all three pictures are still the item's pictures
        assertThat(sink.saved.single().pictures).hasSize(3)
    }

    @Test
    fun `images guardrail - two pictures retry with one, an empty template counts as a refusal`() = runTest {
        config.rejected = setOf(Parsed("Title: \n"))
        val engine = FakeLlmEngine { request ->
            if (request.images.size == 2) FakeLlmEngine.chunks("Title: \n") else FakeLlmEngine.chunks(goodOutput)
        }

        val events = runImages(images(2), engine).events()

        assertThat(engine.requests.map { it.images.size }).containsExactly(2, 1).inOrder()
        assertThat(events.last()).isEqualTo(ClipProgress.Saved("saved-1"))
    }

    @Test
    fun `images guardrail - retries only once and then reports the second answer`() = runTest {
        val engine = FakeLlmEngine.streaming("NO RECIPE")

        val events = runImages(images(3), engine).events()

        assertThat(engine.requests.map { it.images.size }).containsExactly(3, 2).inOrder()
        assertThat(events.last()).isEqualTo(ClipProgress.Failed(ExtractionError.ParseFailed("NO RECIPE")))
        assertThat(events.filterIsInstance<ClipProgress.Retrying>()).hasSize(1)
    }

    @Test
    fun `images guardrail - a single picture is never retried`() = runTest {
        val engine = FakeLlmEngine.streaming("NO RECIPE")

        runImages(images(1), engine).events()

        assertThat(engine.requests).hasSize(1)
    }
}
