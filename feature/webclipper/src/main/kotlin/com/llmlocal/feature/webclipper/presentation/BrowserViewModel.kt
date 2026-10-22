package com.llmlocal.feature.webclipper.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.domain.hostOf
import com.llmlocal.feature.webclipper.domain.wholePageCandidate
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserEffect
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserIntent
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserSheet
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserState
import com.llmlocal.feature.webclipper.presentation.mvi.ClipFabState
import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.feature.webclipper.domain.StartPage
import com.llmlocal.feature.webclipper.domain.usecase.ObserveReadinessUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ObserveStartPageUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ScanPageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the in-app browser: address bar, page progress, the
 * clip button state machine and the candidate / nothing-detected sheets.
 *
 *  intent -> handler -> state update (+ optional effect)
 *
 * The WebView itself lives in `WebViewHolder` (data); this class never touches
 * `android.webkit`. Page events arrive as intents from the screen, WebView
 * commands leave as [BrowserEffect]s, and capture goes through
 * [ScanPageUseCase] so the whole thing is unit-testable on the JVM.
 *
 * Clip button derivation (see UX spec §2), highest priority first:
 * model not ready → extraction in flight → page loading → scanning →
 * found (full scan, else proactive quick scan) → idle.
 *
 * @param initialUrl page to open on creation, or `null` to keep whatever
 *   the shared WebView already shows; when it shows nothing the route sends
 *   [BrowserIntent.OpenStartPage] and the saved [StartPage] is loaded
 */
class BrowserViewModel(
    private val config: WebClipConfig<*>,
    private val labels: WebClipperLabels,
    private val scanPage: ScanPageUseCase,
    observeReadiness: ObserveReadinessUseCase,
    private val observeStartPage: ObserveStartPageUseCase,
    private val session: ClipSession,
    private val dispatchers: DispatcherProvider,
    initialUrl: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _effects = Channel<BrowserEffect>(Channel.BUFFERED)
    val effects: Flow<BrowserEffect> = _effects.receiveAsFlow()

    private var quickScanJob: Job? = null
    private var scanJob: Job? = null

    /** Candidates found by the cheap structured scan, before a full capture. */
    private var quickScanCount = 0
    private var scanning = false
    private var activeExtraction: String? = null

    init {
        observeReadiness(viewModelScope)
            .onEach { readiness ->
                _state.update { it.copy(readiness = readiness) }
                recomputeFab()
            }
            .launchIn(viewModelScope)

        session.activeExtraction
            .onEach { id ->
                activeExtraction = id
                recomputeFab()
            }
            .launchIn(viewModelScope)

        // A save (`ClipSession.clear()`) or a navigation elsewhere drops the
        // captured page; the cached candidates then point at nothing, so the
        // button goes back to "Clip recipe" and the next tap re-scans.
        session.current
            .onEach { current ->
                if (current == null && (_state.value.candidates.isNotEmpty() || quickScanCount > 0)) {
                    quickScanCount = 0
                    _state.update { it.copy(candidates = emptyList(), sheet = BrowserSheet.None) }
                    recomputeFab()
                }
            }
            .launchIn(viewModelScope)

        session.reopenCandidates
            .onEach { reopen ->
                if (reopen) {
                    session.consumeReopenCandidates()
                    val candidates = session.current.value?.candidates.orEmpty()
                    if (candidates.size > 1) {
                        _state.update { it.copy(candidates = candidates, sheet = BrowserSheet.Candidates) }
                        recomputeFab()
                    }
                }
            }
            .launchIn(viewModelScope)

        initialUrl?.takeIf { it.isNotBlank() }?.let { load(it) }
    }

    /** Single dispatch point for all user + WebView events. */
    fun onIntent(intent: BrowserIntent) {
        when (intent) {
            is BrowserIntent.UpdateDraftUrl -> _state.update { it.copy(draftUrl = intent.value) }
            is BrowserIntent.UrlFocusChanged -> onUrlFocusChanged(intent.focused)
            BrowserIntent.SubmitUrl -> submitUrl()
            BrowserIntent.Reload -> send(BrowserEffect.ReloadPage)
            BrowserIntent.Stop -> {
                _state.update { it.copy(isLoading = false) }
                recomputeFab()
                send(BrowserEffect.StopLoading)
            }
            BrowserIntent.GoForward -> send(BrowserEffect.GoForward)
            is BrowserIntent.PageStarted -> onPageStarted(intent.url)
            is BrowserIntent.PageProgress -> onPageProgress(intent.progress)
            is BrowserIntent.PageFinished -> onPageFinished(intent.url, intent.title)
            is BrowserIntent.HistoryChanged -> _state.update {
                it.copy(canGoBack = intent.canGoBack, canGoForward = intent.canGoForward)
            }
            BrowserIntent.PageLoadFailed -> {
                _state.update { it.copy(isLoading = false) }
                recomputeFab()
                send(BrowserEffect.ShowSnackbar(labels.pageLoadError))
            }
            is BrowserIntent.Scrolled -> onScrolled(intent.deltaY)
            BrowserIntent.Scan -> onScan()
            is BrowserIntent.PickCandidate -> {
                _state.update { it.copy(sheet = BrowserSheet.None) }
                send(BrowserEffect.NavigateToExtraction(intent.candidateId))
            }
            BrowserIntent.ExtractWholePage -> extractWholePage()
            BrowserIntent.DismissSheet -> _state.update { it.copy(sheet = BrowserSheet.None) }
            BrowserIntent.OpenModelManagement -> send(BrowserEffect.NavigateToModelManagement)
            BrowserIntent.OpenStartPage -> openStartPage()
            BrowserIntent.HideFabForPage -> setFabHidden(hidden = true)
            BrowserIntent.ShowFab -> setFabHidden(hidden = false)
            BrowserIntent.SharePage -> currentUrl()?.let { url ->
                val title = _state.value.pageTitle?.takeIf { it.isNotBlank() }
                send(BrowserEffect.ShareText(listOfNotNull(title, url).joinToString("\n")))
            }
            BrowserIntent.CopyLink -> currentUrl()?.let { url ->
                send(BrowserEffect.CopyToClipboard(url))
                send(BrowserEffect.ShowSnackbar(labels.linkCopied))
            }
            BrowserIntent.OpenExternal -> currentUrl()?.let { send(BrowserEffect.OpenExternal(it)) }
        }
    }

    // -- address bar ---------------------------------------------------------

    private fun onUrlFocusChanged(focused: Boolean) {
        _state.update { state ->
            state.copy(
                urlFocused = focused,
                draftUrl = if (focused) state.url else state.draftUrl,
            )
        }
    }

    private fun submitUrl() {
        val target = resolveInput(_state.value.draftUrl, labels.searchUrlTemplate) ?: return
        _state.update { it.copy(urlFocused = false) }
        load(target)
    }

    private fun load(url: String) {
        onPageStarted(url)
        send(BrowserEffect.LoadUrl(url))
    }

    /**
     * Blank start: load the saved start page, or focus the address bar when
     * it is [StartPage.Blank]. A no-op once a page is known, so a late
     * preference read never navigates away from something the user opened.
     */
    private fun openStartPage() {
        if (_state.value.url.isNotBlank()) return
        viewModelScope.launch {
            val page = observeStartPage().first()
            if (_state.value.url.isNotBlank()) return@launch
            when (val url = page.url) {
                null -> if (!_state.value.urlFocused) onUrlFocusChanged(focused = true)
                else -> load(url)
            }
        }
    }

    // -- page lifecycle ------------------------------------------------------

    private fun onPageStarted(url: String) {
        quickScanJob?.cancel()
        scanJob?.cancel()
        scanning = false
        quickScanCount = 0
        session.clearPage()
        _state.update {
            it.copy(
                url = url,
                draftUrl = if (it.urlFocused) it.draftUrl else url,
                isLoading = true,
                pageProgress = 0,
                candidates = emptyList(),
                sheet = BrowserSheet.None,
                fabExpanded = true,
            )
        }
        recomputeFab()
    }

    private fun onPageProgress(progress: Int) {
        val clamped = progress.coerceIn(0, 100)
        _state.update { it.copy(pageProgress = clamped, isLoading = clamped < 100) }
        recomputeFab()
    }

    private fun onPageFinished(url: String, title: String?) {
        _state.update {
            it.copy(
                url = url,
                draftUrl = if (it.urlFocused) it.draftUrl else url,
                pageTitle = title,
                isLoading = false,
                pageProgress = 100,
            )
        }
        recomputeFab()
        scheduleQuickScan(url)
    }

    /**
     * Proactive structured-data count ~1.5 s after load so late-hydrating
     * pages have settled. Only the cheap detector runs here; the full
     * capture waits for a tap.
     */
    private fun scheduleQuickScan(url: String) {
        quickScanJob?.cancel()
        quickScanJob = viewModelScope.launch {
            delay(QUICK_SCAN_DEBOUNCE_MS)
            if (_state.value.candidates.isNotEmpty()) return@launch
            val count = scanPage.quick()
            if (_state.value.url == url && _state.value.candidates.isEmpty()) {
                quickScanCount = count
                recomputeFab()
            }
        }
    }

    private fun onScrolled(deltaY: Int) {
        val expanded = when {
            deltaY > SCROLL_THRESHOLD_PX -> false
            deltaY < -SCROLL_THRESHOLD_PX -> true
            else -> return
        }
        if (_state.value.fabExpanded != expanded) _state.update { it.copy(fabExpanded = expanded) }
    }

    // -- clip button ---------------------------------------------------------

    /**
     * Remembers the hide choice per host for the life of this ViewModel:
     * another host shows the button again, coming back to this one keeps
     * it hidden until the user taps the docked "Show clip button" chip.
     */
    private fun setFabHidden(hidden: Boolean) {
        val host = hostOf(_state.value.url) ?: return
        _state.update {
            it.copy(hiddenFabHosts = if (hidden) it.hiddenFabHosts + host else it.hiddenFabHosts - host)
        }
    }

    private fun onScan() {
        val current = _state.value
        when {
            current.readiness !is ModelReadiness.Ready -> {
                // Say why, then go to model management (the snackbar host is
                // app-wide, so it survives the navigation). A running
                // download already shows its percentage on the button, and
                // "choose and download a model first" would be wrong there.
                if (current.readiness !is ModelReadiness.Downloading) {
                    send(BrowserEffect.ShowSnackbar(labels.modelNotReadySnackbar))
                }
                send(BrowserEffect.NavigateToModelManagement)
            }
            activeExtraction != null -> send(BrowserEffect.NavigateToExtraction(activeExtraction!!))
            current.isLoading -> send(BrowserEffect.ShowSnackbar(labels.waitForPage))
            scanning -> Unit
            // Cached candidates are only valid while the session still holds
            // the page they were detected on (bug: "Recipe found" did nothing
            // after coming back from a saved recipe).
            current.candidates.isNotEmpty() && session.current.value?.snapshot != null ->
                routeCandidates(current.candidates)
            else -> runFullScan { candidates -> routeCandidates(candidates) }
        }
    }

    private fun runFullScan(onDone: (List<ClipCandidate>) -> Unit) {
        quickScanJob?.cancel()
        scanning = true
        recomputeFab()
        val url = _state.value.url
        scanJob = viewModelScope.launch {
            try {
                when (val outcome = scanPage(config)) {
                    is Outcome.Success -> {
                        val candidates = outcome.value
                        if (_state.value.url == url) {
                            _state.update { it.copy(candidates = candidates) }
                            onDone(candidates)
                        }
                    }
                    is Outcome.Failure -> send(BrowserEffect.ShowSnackbar(labels.scanFailed))
                }
            } finally {
                scanning = false
                recomputeFab()
            }
        }
    }

    private fun routeCandidates(candidates: List<ClipCandidate>) {
        when (candidates.size) {
            0 -> _state.update { it.copy(sheet = BrowserSheet.NothingDetected) }
            1 -> {
                _state.update { it.copy(sheet = BrowserSheet.None) }
                send(BrowserEffect.NavigateToExtraction(candidates.single().id))
            }
            else -> _state.update { it.copy(sheet = BrowserSheet.Candidates) }
        }
    }

    private fun extractWholePage() {
        _state.update { it.copy(sheet = BrowserSheet.None) }
        val snapshot = session.current.value?.snapshot
        if (snapshot != null) {
            navigateToWholePage(snapshot)
        } else {
            runFullScan { navigateToWholePage(session.current.value?.snapshot ?: return@runFullScan) }
        }
    }

    private fun navigateToWholePage(snapshot: PageSnapshot) {
        val candidate = wholePageCandidate(snapshot)
        session.addCandidate(candidate)
        send(BrowserEffect.NavigateToExtraction(candidate.id))
    }

    private fun recomputeFab() {
        _state.update { current ->
            val readiness = current.readiness
            val fab = when {
                readiness is ModelReadiness.NoModel || readiness is ModelReadiness.Failed ->
                    ClipFabState.ModelNotReady()
                readiness is ModelReadiness.Downloading -> ClipFabState.ModelNotReady(readiness.progress)
                readiness !is ModelReadiness.Ready -> ClipFabState.PageLoading // Checking / Initializing
                activeExtraction != null -> ClipFabState.Extracting
                current.isLoading -> ClipFabState.PageLoading
                scanning -> ClipFabState.Scanning
                current.candidates.isNotEmpty() -> ClipFabState.Found(current.candidates.size)
                quickScanCount > 0 -> ClipFabState.Found(quickScanCount)
                else -> ClipFabState.Idle
            }
            if (fab == current.fab) current else current.copy(fab = fab)
        }
    }

    private fun currentUrl(): String? = _state.value.url.takeIf { it.isNotBlank() }

    private fun send(effect: BrowserEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    override fun onCleared() {
        quickScanJob?.cancel()
        scanJob?.cancel()
    }

    companion object {
        /** Settle time after `onPageFinished` before the proactive scan. */
        const val QUICK_SCAN_DEBOUNCE_MS = 1_500L
        private const val SCROLL_THRESHOLD_PX = 8

        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

        /**
         * Address-bar input → URL: keeps explicit schemes, prefixes `https://`
         * for host-like text (no spaces, contains a dot or is `localhost`),
         * otherwise searches via [searchTemplate]. `null` for blank input.
         */
        fun resolveInput(raw: String, searchTemplate: (String) -> String): String? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            if (SCHEME.containsMatchIn(text)) return text
            val hostLike = !text.contains(' ') &&
                (text.contains('.') || text.startsWith("localhost"))
            return if (hostLike) "https://$text" else searchTemplate(text)
        }
    }
}
