package com.llmlocal.feature.webclipper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.repository.LlmRequestOptions
import com.llmlocal.core.domain.repository.LlmTaskParseException
import com.llmlocal.core.domain.usecase.GenerateLlmTaskUseCase
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.capture.PictureDownloader
import com.llmlocal.feature.webclipper.mvi.ExtractionEffect
import com.llmlocal.feature.webclipper.mvi.ExtractionError
import com.llmlocal.feature.webclipper.mvi.ExtractionIntent
import com.llmlocal.feature.webclipper.mvi.ExtractionState
import com.llmlocal.feature.webclipper.mvi.Phase
import com.llmlocal.feature.webclipper.mvi.PhaseStatus
import com.llmlocal.feature.webclipper.mvi.PictureDraft
import com.llmlocal.feature.webclipper.mvi.PictureStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/**
 * MVI ViewModel for the extraction screen: runs the four phases
 * Reading → Extracting → Pictures → Saving for one [ClipCandidate].
 *
 *  1. **Reading** — `config.renderLlmInput` on the captured snapshot.
 *  2. **Extracting** — [GenerateLlmTaskUseCase] with a separate system
 *     instruction, `maxOutputTokens = config.maxOutputTokens` and
 *     temperature [EXTRACTION_TEMPERATURE]; raw deltas are appended to
 *     [ExtractionState.streamedText] for the app's draft slot. A parser
 *     `null` on a STRUCTURED candidate falls back to `config.fallback`.
 *  3. **Pictures** — `config.pickPictures` downloaded in parallel with the
 *     model (never sent to it); failures only turn the phase to `Warning`.
 *  4. **Saving** — `sink.save` with the kept picture files, then
 *     [ExtractionEffect.NavigateToSaved] after a short beat so the last
 *     tick is visible.
 *
 * Cancel stops the job **and** the engine, deletes the temp pictures and
 * pops to the browser. The candidate is read once from [ClipSession]; if
 * the session is empty (process death) the screen pops immediately.
 *
 * The app's result type is erased to `Any` here, exactly like
 * `LlmTaskViewModel` does with `LlmTaskConfig<*>`.
 */
class ExtractionViewModel(
    candidateId: String,
    private val config: WebClipConfig<Any>,
    private val sink: ClipSink<Any>,
    private val labels: WebClipperLabels,
    private val session: ClipSession,
    private val generateTask: GenerateLlmTaskUseCase,
    private val engine: LlmEngine,
    private val pictureDownloader: PictureDownloader,
    private val tempDir: File,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state: MutableStateFlow<ExtractionState>
    val state: StateFlow<ExtractionState>

    private val _effects = Channel<ExtractionEffect>(Channel.BUFFERED)
    val effects: Flow<ExtractionEffect> = _effects.receiveAsFlow()

    private var job: Job? = null
    private val tempFiles = mutableListOf<File>()

    init {
        val page = session.current.value
        val candidate = page?.candidates?.firstOrNull { it.id == candidateId }
        val others = page?.candidates.orEmpty().count { it.id != candidateId && it.id != WHOLE_PAGE_CANDIDATE_ID }
        _state = MutableStateFlow(
            ExtractionState(
                candidate = candidate ?: missingCandidate(candidateId),
                otherCandidates = others,
                phases = pendingPhases(),
                isRunning = false,
                sourceHost = hostOf(page?.snapshot?.url),
                pageTitle = page?.snapshot?.title,
            ),
        )
        state = _state.asStateFlow()
        if (candidate == null) {
            send(ExtractionEffect.PopToBrowser)
        } else {
            start()
        }
    }

    fun onIntent(intent: ExtractionIntent) {
        when (intent) {
            ExtractionIntent.Start -> start()
            ExtractionIntent.Cancel -> cancel()
            ExtractionIntent.Retry -> start()
            ExtractionIntent.RetryWholePage -> retryWholePage()
            is ExtractionIntent.TogglePicture -> _state.update { state ->
                state.copy(
                    pictures = state.pictures.map { p ->
                        if (p.url == intent.url) p.copy(keep = !p.keep) else p
                    },
                )
            }
            ExtractionIntent.ToggleRaw -> _state.update { it.copy(showRaw = !it.showRaw) }
            ExtractionIntent.BackToCandidates -> backToCandidates()
        }
    }

    // -- pipeline ------------------------------------------------------------

    private fun start() {
        if (job?.isActive == true || _state.value.savedId != null) return
        val snapshot = session.current.value?.snapshot
        if (snapshot == null) {
            send(ExtractionEffect.PopToBrowser)
            return
        }
        val candidate = _state.value.candidate
        deleteTempFiles()
        _state.update {
            it.copy(
                phases = pendingPhases() + (Phase.Reading to PhaseStatus.Active),
                streamedText = "",
                pictures = emptyList(),
                isRunning = true,
                error = null,
                truncated = false,
                savedId = null,
            )
        }
        session.setActiveExtraction(candidate.id)
        job = viewModelScope.launch {
            try {
                run(candidate, snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(classify(e))
            }
        }
    }

    private suspend fun CoroutineScope.run(candidate: ClipCandidate, snapshot: PageSnapshot) {
        val input = withContext(dispatchers.default) {
            config.renderLlmInput(candidate, snapshot, config.budgetChars)
        }
        setPhase(Phase.Reading, PhaseStatus.Done)
        setPhase(Phase.Extracting, PhaseStatus.Active)

        val pictures = startPictures(candidate, snapshot)

        when (val init = withContext(dispatchers.io) { engine.initialize() }) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> {
                pictures.cancel()
                fail(classify(init.error))
                return
            }
        }

        val buffer = StringBuilder()
        var result: Any? = null
        var failure: Throwable? = null
        generateTask(
            engine,
            config.llmConfig,
            listOf(input),
            LlmRequestOptions(
                systemInstruction = true,
                maxOutputTokens = config.maxOutputTokens,
                temperature = EXTRACTION_TEMPERATURE,
            ),
        ).collect { event ->
            when (event) {
                is LlmTaskEvent.Token -> {
                    buffer.append(event.delta)
                    _state.update { it.copy(streamedText = it.streamedText + event.delta) }
                }
                is LlmTaskEvent.Complete<*> -> result = event.result
                is LlmTaskEvent.Failed -> failure = event.error
            }
        }
        val rawText = buffer.toString()

        if (result == null) {
            val error = failure
            val parseFailed = error == null || error is LlmTaskParseException
            if (!parseFailed) {
                pictures.cancel()
                fail(classify(error))
                return
            }
            val fallback = if (candidate.source == CandidateSource.STRUCTURED) {
                withContext(dispatchers.default) { config.fallback(candidate, snapshot) }
            } else {
                null
            }
            if (fallback == null) {
                pictures.cancel()
                fail(ExtractionError.ParseFailed(rawText))
                return
            }
            result = fallback
        }

        val truncated = TruncationHeuristic.looksTruncated(rawText, config.maxOutputTokens)
        _state.update { it.copy(truncated = truncated) }
        setPhase(Phase.Extracting, if (truncated) PhaseStatus.Warning else PhaseStatus.Done)

        val anyPictureFailed = pictures.await()
        setPhase(Phase.Pictures, if (anyPictureFailed) PhaseStatus.Warning else PhaseStatus.Done)

        setPhase(Phase.Saving, PhaseStatus.Active)
        val kept = _state.value.pictures.filter { it.keep && it.status == PictureStatus.Done }
            .mapNotNull { it.localFile }
        val savedId = withContext(dispatchers.io) {
            sink.save(requireNotNull(result), snapshot, candidate, kept, truncated)
        }
        deleteTempFiles(except = kept)
        setPhase(Phase.Saving, PhaseStatus.Done)
        _state.update { it.copy(isRunning = false, savedId = savedId) }
        session.clear()
        delay(NAVIGATE_DELAY_MS)
        send(ExtractionEffect.ShowSnackbar(labels.recipeSaved))
        send(ExtractionEffect.NavigateToSaved(savedId))
    }

    /**
     * Downloads the config's picture picks concurrently, each into its own
     * temp file. Returns `true` when at least one download failed. Never
     * throws for a single failure; cancellation propagates.
     */
    private fun CoroutineScope.startPictures(candidate: ClipCandidate, snapshot: PageSnapshot): Deferred<Boolean> =
        async {
            val urls = withContext(dispatchers.default) {
                config.pickPictures(candidate, snapshot, config.maxPictures)
            }.distinct()
            if (urls.isEmpty()) return@async false
            _state.update { state ->
                state.copy(pictures = urls.mapIndexed { i, url -> PictureDraft(url = url, keep = i < DEFAULT_KEEP) })
            }
            setPhase(Phase.Pictures, PhaseStatus.Active)
            val runId = UUID.randomUUID().toString().take(8)
            val results = urls.mapIndexed { index, url ->
                async(dispatchers.io) { downloadOne(url, snapshot.url, File(tempDir, "$runId-$index.jpg")) }
            }.awaitAll()
            results.any { !it }
        }

    private suspend fun downloadOne(url: String, referer: String, into: File): Boolean {
        synchronized(tempFiles) { tempFiles += into }
        updatePicture(url) { it.copy(status = PictureStatus.Downloading) }
        val outcome = try {
            into.parentFile?.mkdirs()
            pictureDownloader.download(url, referer, into, MAX_LONG_EDGE_PX)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Failure(e)
        }
        return when (outcome) {
            is Outcome.Success -> {
                updatePicture(url) { it.copy(localFile = outcome.value, status = PictureStatus.Done) }
                true
            }
            is Outcome.Failure -> {
                updatePicture(url) { it.copy(status = PictureStatus.Failed) }
                false
            }
        }
    }

    // -- user actions --------------------------------------------------------

    private fun cancel() {
        val wasRunning = job?.isActive == true
        job?.cancel()
        job = null
        engine.cancel()
        deleteTempFiles()
        session.setActiveExtraction(null)
        _state.update { state ->
            state.copy(
                isRunning = false,
                phases = state.phases.mapValues { (_, s) -> if (s == PhaseStatus.Active) PhaseStatus.Pending else s },
            )
        }
        if (wasRunning) send(ExtractionEffect.ShowSnackbar(labels.clipCancelled))
        send(ExtractionEffect.PopToBrowser)
    }

    private fun retryWholePage() {
        if (job?.isActive == true) return
        val snapshot = session.current.value?.snapshot ?: run {
            send(ExtractionEffect.PopToBrowser)
            return
        }
        val candidate = wholePageCandidate(snapshot)
        session.addCandidate(candidate)
        _state.update { it.copy(candidate = candidate, error = null) }
        start()
    }

    private fun backToCandidates() {
        job?.cancel()
        job = null
        engine.cancel()
        deleteTempFiles()
        session.setActiveExtraction(null)
        session.requestReopenCandidates()
        _state.update { it.copy(isRunning = false) }
        send(ExtractionEffect.PopToBrowser)
    }

    // -- helpers -------------------------------------------------------------

    private fun fail(error: ExtractionError) {
        deleteTempFiles()
        session.setActiveExtraction(null)
        _state.update { state ->
            val failedPhase = state.phases.entries.firstOrNull { it.value == PhaseStatus.Active }?.key ?: Phase.Extracting
            state.copy(
                isRunning = false,
                error = error,
                phases = state.phases.mapValues { (phase, status) ->
                    when {
                        phase == failedPhase -> PhaseStatus.Failed
                        status == PhaseStatus.Active -> PhaseStatus.Pending
                        else -> status
                    }
                },
            )
        }
    }

    private fun classify(error: Throwable): ExtractionError {
        val message = error.message.orEmpty()
        val missing = error is FileNotFoundException ||
            message.contains("does not exist", ignoreCase = true) ||
            message.contains("no such file", ignoreCase = true)
        return if (missing) ExtractionError.ModelMissing else ExtractionError.ModelFailed(message.ifBlank { error::class.java.simpleName })
    }

    private fun setPhase(phase: Phase, status: PhaseStatus) {
        _state.update { it.copy(phases = it.phases + (phase to status)) }
    }

    private fun updatePicture(url: String, transform: (PictureDraft) -> PictureDraft) {
        _state.update { state ->
            state.copy(pictures = state.pictures.map { if (it.url == url) transform(it) else it })
        }
    }

    private fun deleteTempFiles(except: List<File> = emptyList()) {
        val doomed = synchronized(tempFiles) {
            val copy = tempFiles.filterNot { it in except }
            tempFiles.removeAll(copy)
            copy
        }
        doomed.forEach { runCatching { it.delete() } }
    }

    private fun send(effect: ExtractionEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    override fun onCleared() {
        job?.cancel()
        if (_state.value.savedId == null) deleteTempFiles()
        if (session.activeExtraction.value == _state.value.candidate.id && _state.value.isRunning) {
            session.setActiveExtraction(null)
        }
        super.onCleared()
    }

    companion object {
        /** Low temperature: extraction, not creativity. */
        const val EXTRACTION_TEMPERATURE = 0.2

        /** Beat between the last phase ticking and leaving the screen. */
        const val NAVIGATE_DELAY_MS = 600L

        /** How many pictures start checked. */
        const val DEFAULT_KEEP = 3

        /** Long-edge resize bound handed to the downloader. */
        const val MAX_LONG_EDGE_PX = 1_280

        private fun pendingPhases(): Map<Phase, PhaseStatus> =
            Phase.entries.associateWith { PhaseStatus.Pending }

        private fun missingCandidate(id: String) = ClipCandidate(
            id = id,
            title = "",
            thumbnailUrl = null,
            snippet = null,
            source = CandidateSource.TEXT,
            ingredientCount = null,
            totalMinutes = null,
            payload = CandidatePayload.WholePage,
        )
    }
}
