package com.llmlocal.feature.webclipper.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.feature.webclipper.domain.CandidatePayload
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.feature.webclipper.domain.ClipProgress
import com.llmlocal.feature.webclipper.domain.ClipRequest
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.ExtractionError
import com.llmlocal.feature.webclipper.domain.Phase
import com.llmlocal.feature.webclipper.domain.PhaseStatus
import com.llmlocal.feature.webclipper.domain.WHOLE_PAGE_CANDIDATE_ID
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.domain.hostOf
import com.llmlocal.feature.webclipper.domain.usecase.CancelClipUseCase
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.feature.webclipper.domain.input.ClipInputKind
import com.llmlocal.feature.webclipper.domain.input.kind
import com.llmlocal.feature.webclipper.domain.usecase.CheckDuplicateUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ClipUseCase
import com.llmlocal.feature.webclipper.domain.wholePageCandidate
import com.llmlocal.feature.webclipper.presentation.mvi.ExtractionEffect
import com.llmlocal.feature.webclipper.presentation.mvi.ExtractionIntent
import com.llmlocal.feature.webclipper.presentation.mvi.ExtractionState
import com.llmlocal.feature.webclipper.presentation.mvi.PictureDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the extraction screen. It maps the [ClipProgress] events
 * of [ClipUseCase] (Reading → Extracting → Pictures → Saving for one
 * [ClipCandidate]) to [ExtractionState] / [ExtractionEffect]; the pipeline
 * itself (direct path, model run, fallback, pictures, truncation, saving)
 * lives in the use case.
 *
 * Before anything runs, [CheckDuplicateUseCase] is asked whether the page was
 * clipped before; if so [ExtractionState.duplicateOf] puts the
 * "Already in your library" dialog up and the pipeline waits for
 * [ExtractionIntent.ReplaceExisting] (save under the old id),
 * [ExtractionIntent.KeepBoth] or Cancel (WS10 bug 6).
 *
 * Cancel stops the collection **and** the engine ([CancelClipUseCase]) and
 * pops to the browser. The candidate is read once from [ClipSession]; if the
 * session is empty (process death, or a stale "Recipe found" button after a
 * save) the screen says so with [WebClipperLabels.scanFailed] and pops
 * immediately — never silently.
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
    private val clip: ClipUseCase<Any>,
    private val checkDuplicate: CheckDuplicateUseCase,
    private val cancelClip: CancelClipUseCase,
    @Suppress("unused") private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state: MutableStateFlow<ExtractionState>
    val state: StateFlow<ExtractionState>

    private val _effects = Channel<ExtractionEffect>(Channel.BUFFERED)
    val effects: Flow<ExtractionEffect> = _effects.receiveAsFlow()

    private var job: Job? = null

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
                inputKind = page?.input?.kind ?: ClipInputKind.WEB,
                imageCount = (page?.input as? ClipInput.Images)?.images?.size ?: 0,
            ),
        )
        state = _state.asStateFlow()
        if (candidate == null) {
            popWithoutPage()
        } else {
            checkDuplicateThenStart()
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
            ExtractionIntent.ReplaceExisting -> decideDuplicate(replace = true)
            ExtractionIntent.KeepBoth -> decideDuplicate(replace = false)
        }
    }

    // -- pipeline ------------------------------------------------------------

    /** Shows the duplicate dialog if the page was clipped before, otherwise starts right away. */
    private fun checkDuplicateThenStart() {
        val snapshot = session.current.value?.snapshot ?: run {
            popWithoutPage()
            return
        }
        viewModelScope.launch {
            val existing = checkDuplicate(sink, snapshot)
            if (existing != null) {
                _state.update { it.copy(duplicateOf = existing) }
            } else {
                start()
            }
        }
    }

    private fun decideDuplicate(replace: Boolean) {
        val existing = _state.value.duplicateOf ?: return
        _state.update { it.copy(duplicateOf = null, replaceId = if (replace) existing else null) }
        start()
    }

    private fun start() {
        if (job?.isActive == true || _state.value.savedId != null) return
        val snapshot = session.current.value?.snapshot
        if (snapshot == null) {
            popWithoutPage()
            return
        }
        val candidate = _state.value.candidate
        _state.update {
            it.copy(
                phases = pendingPhases() + (Phase.Reading to PhaseStatus.Active),
                streamedText = "",
                pictures = emptyList(),
                isRunning = true,
                error = null,
                truncated = false,
                extractedWithoutModel = false,
                savedId = null,
            )
        }
        val request = ClipRequest(
            candidate = candidate,
            snapshot = snapshot,
            replaceId = _state.value.replaceId,
            isPictureKept = { url -> _state.value.pictures.firstOrNull { it.url == url }?.keep == true },
        )
        job = viewModelScope.launch {
            try {
                clip(config, sink, request).collect { onProgress(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(ExtractionError.ModelFailed(e.message.orEmpty().ifBlank { e::class.java.simpleName }))
            }
        }
    }

    private suspend fun onProgress(event: ClipProgress) {
        when (event) {
            is ClipProgress.PhaseChanged -> setPhase(event.phase, event.status)
            is ClipProgress.Streamed -> _state.update { it.copy(streamedText = it.streamedText + event.delta) }
            ClipProgress.ExtractedWithoutModel -> _state.update { it.copy(extractedWithoutModel = true) }
            is ClipProgress.PicturesProposed -> _state.update { state ->
                state.copy(pictures = event.pictures.map { PictureDraft(url = it.url, keep = it.keep) })
            }
            is ClipProgress.PictureChanged -> updatePicture(event.url) {
                it.copy(status = event.status, localFile = event.localFile ?: it.localFile)
            }
            is ClipProgress.Truncated -> _state.update { it.copy(truncated = event.truncated) }
            is ClipProgress.Failed -> fail(event.error)
            is ClipProgress.Saved -> {
                _state.update { it.copy(isRunning = false, savedId = event.id) }
                delay(NAVIGATE_DELAY_MS)
                send(ExtractionEffect.ShowSnackbar(labels.recipeSaved))
                send(ExtractionEffect.NavigateToSaved(event.id))
            }
        }
    }

    // -- user actions --------------------------------------------------------

    private fun cancel() {
        val wasRunning = job?.isActive == true
        stopRun(reopenCandidates = false)
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
        stopRun(reopenCandidates = true)
        send(ExtractionEffect.PopToBrowser)
    }

    private fun stopRun(reopenCandidates: Boolean) {
        job?.cancel()
        job = null
        cancelClip(reopenCandidates)
        _state.update { state ->
            state.copy(
                isRunning = false,
                phases = state.phases.mapValues { (_, s) -> if (s == PhaseStatus.Active) PhaseStatus.Pending else s },
            )
        }
    }

    // -- helpers -------------------------------------------------------------

    /** No captured page to work on: say why (snackbar) and leave. */
    private fun popWithoutPage() {
        send(ExtractionEffect.ShowSnackbar(labels.scanFailed))
        send(ExtractionEffect.PopToBrowser)
    }

    private fun fail(error: ExtractionError) {
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

    private fun setPhase(phase: Phase, status: PhaseStatus) {
        _state.update { it.copy(phases = it.phases + (phase to status)) }
    }

    private fun updatePicture(url: String, transform: (PictureDraft) -> PictureDraft) {
        _state.update { state ->
            state.copy(pictures = state.pictures.map { if (it.url == url) transform(it) else it })
        }
    }

    private fun send(effect: ExtractionEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        /** Beat between the last phase ticking and leaving the screen. */
        const val NAVIGATE_DELAY_MS = 600L

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
