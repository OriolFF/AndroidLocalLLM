package com.llmlocal.feature.webclipper.fakes

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.domain.task.PromptBuilder
import com.llmlocal.core.domain.task.TaskParser
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.core.web.ReadableContent
import com.llmlocal.core.web.TextSection
import com.llmlocal.feature.webclipper.CandidatePayload
import com.llmlocal.feature.webclipper.CandidateSource
import com.llmlocal.feature.webclipper.ClipCandidate
import com.llmlocal.feature.webclipper.ClipSaveOptions
import com.llmlocal.feature.webclipper.ClipSink
import com.llmlocal.feature.webclipper.PicturePick
import com.llmlocal.feature.webclipper.WebClipConfig
import com.llmlocal.feature.webclipper.capture.PageScanner
import com.llmlocal.feature.webclipper.capture.PictureDownloader
import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.feature.webclipper.readiness.ReadinessSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

/** Routes `Dispatchers.Main` (hence `viewModelScope`) to a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class TestDispatchers(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

/** The app result type under test. */
data class Parsed(val raw: String)

/** Parses anything containing `Title:`; `null` otherwise. */
val TITLE_PARSER = TaskParser<Parsed> { text -> if (text.contains("Title:")) Parsed(text) else null }

class FakeWebClipConfig(
    var candidates: List<ClipCandidate> = emptyList(),
    var fallbackResult: Parsed? = null,
    var pictureUrls: List<String> = emptyList(),
    /** When set, overrides [pictureUrls] with explicit preferred flags. */
    var picturePicks: List<PicturePick>? = null,
    override val maxOutputTokens: Int = 1_024,
    override val skipModelWhenStructuredComplete: Boolean = false,
    /** Fallback results that count as complete (direct path); default: every one. */
    var complete: (Parsed) -> Boolean = { true },
) : WebClipConfig<Parsed> {

    val fallbackCalls = mutableListOf<ClipCandidate>()

    /** Results `accept` rejects (by raw text). */
    var rejected: Set<Parsed> = emptySet()
    val acceptCalls = mutableListOf<Pair<ClipCandidate, Parsed>>()

    val renderedInputs = mutableListOf<ClipCandidate>()

    override val llmConfig: LlmTaskConfig<Parsed> = LlmTaskConfig(
        promptBuilder = object : PromptBuilder {
            override fun systemPrompt(): String = "SYSTEM"
            override fun userPrompt(inputs: List<String>): String = inputs.joinToString("\n")
        },
        parser = TITLE_PARSER,
    )

    override fun detect(snapshot: PageSnapshot): List<ClipCandidate> = candidates

    override fun renderLlmInput(candidate: ClipCandidate, snapshot: PageSnapshot, budgetChars: Int): String {
        renderedInputs += candidate
        return "INPUT:${candidate.id}"
    }

    override fun fallback(candidate: ClipCandidate, snapshot: PageSnapshot): Parsed? {
        fallbackCalls += candidate
        return fallbackResult
    }

    override fun isComplete(item: Parsed): Boolean = complete(item)

    override fun accept(candidate: ClipCandidate, item: Parsed): Boolean {
        acceptCalls += candidate to item
        return item !in rejected
    }

    override fun pickPictures(candidate: ClipCandidate, snapshot: PageSnapshot, max: Int): List<String> =
        pictureUrls.take(max)

    override fun rankPictures(candidate: ClipCandidate, snapshot: PageSnapshot, max: Int): List<PicturePick> =
        picturePicks?.take(max) ?: super.rankPictures(candidate, snapshot, max)
}

class FakeClipSink : ClipSink<Parsed> {
    data class Saved(
        val item: Parsed,
        val candidate: ClipCandidate,
        val pictures: List<File>,
        val truncated: Boolean,
        val options: ClipSaveOptions = ClipSaveOptions(),
    )

    val saved = mutableListOf<Saved>()
    var failWith: Throwable? = null

    override suspend fun save(
        item: Parsed,
        snapshot: PageSnapshot,
        candidate: ClipCandidate,
        pictures: List<File>,
        truncated: Boolean,
    ): String = save(item, snapshot, candidate, pictures, truncated, ClipSaveOptions())

    override suspend fun save(
        item: Parsed,
        snapshot: PageSnapshot,
        candidate: ClipCandidate,
        pictures: List<File>,
        truncated: Boolean,
        options: ClipSaveOptions,
    ): String {
        failWith?.let { throw it }
        saved += Saved(item, candidate, pictures, truncated, options)
        return "saved-${saved.size}"
    }
}

class FakePageScanner(
    var quickCount: Int = 0,
    var captureOutcome: Outcome<PageSnapshot> = Outcome.Success(snapshot()),
) : PageScanner {
    var quickScans = 0
    var captures = 0

    override suspend fun quickScanStructured(): Int {
        quickScans++
        return quickCount
    }

    override suspend fun capture(): Outcome<PageSnapshot> {
        captures++
        return captureOutcome
    }
}

/** Writes a tiny file for every URL not in [failing]. */
class FakePictureDownloader(
    private val failing: Set<String> = emptySet(),
    private val delayMs: Long = 0,
) : PictureDownloader {
    val requests = mutableListOf<String>()

    override suspend fun download(url: String, referer: String?, into: File, maxLongEdgePx: Int): Outcome<File> {
        requests += url
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        if (url in failing) return Outcome.Failure(IllegalStateException("404 $url"))
        into.parentFile?.mkdirs()
        into.writeBytes(byteArrayOf(1, 2, 3))
        return Outcome.Success(into)
    }
}

class FakeReadinessSource(initial: ModelReadiness = ModelReadiness.Checking) : ReadinessSource {
    val readiness = MutableStateFlow(initial)
    override fun observe(scope: CoroutineScope): StateFlow<ModelReadiness> = readiness
}

fun snapshot(
    url: String = "https://www.cooking.example/recipes/pasta",
    title: String? = "Tomato pasta",
) = PageSnapshot(
    url = url,
    title = title,
    siteName = "Cooking Example",
    jsonLd = emptyList(),
    readable = ReadableContent(title, null, "A quick pasta.", "text ".repeat(200), 1_000),
    sections = listOf(TextSection("Pasta", 2, "Ingredients…", emptyList(), 0)),
    images = emptyList(),
    openGraph = mapOf("og:image" to "https://www.cooking.example/og.jpg"),
    capturedAt = 0L,
)

fun candidate(
    id: String,
    source: CandidateSource = CandidateSource.TEXT,
    title: String = "Candidate $id",
) = ClipCandidate(
    id = id,
    title = title,
    thumbnailUrl = null,
    snippet = null,
    source = source,
    ingredientCount = null,
    totalMinutes = null,
    payload = CandidatePayload.Text(TextSection(title, 2, "text", emptyList(), 0)),
)
