package com.llmlocal.feature.webclipper

import com.llmlocal.core.web.PageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the capture + candidates for the page the user is currently on so
 * the browser and extraction routes (two ViewModels) share them without
 * re-capturing. Registered as a Koin `single`.
 *
 * Besides the captured page it carries two small cross-route signals:
 *
 *  - [activeExtraction]: the candidate id an extraction is currently
 *    running for, so the browser can show `ClipFabState.Extracting` and
 *    route a tap back to that screen.
 *  - [reopenCandidates]: set by the extraction screen's "Not this one?" so
 *    the browser re-opens the candidate sheet when the user comes back.
 */
class ClipSession {

    /** One captured page and the candidates detected on it. */
    data class Current(
        val snapshot: PageSnapshot,
        val candidates: List<ClipCandidate>,
    )

    private val _current = MutableStateFlow<Current?>(null)

    /** `null` until the first capture, and again after [clear]. */
    val current: StateFlow<Current?> = _current

    private val _activeExtraction = MutableStateFlow<String?>(null)

    /** Candidate id of the extraction in flight, or `null`. */
    val activeExtraction: StateFlow<String?> = _activeExtraction

    private val _reopenCandidates = MutableStateFlow(false)

    /** One-shot request from the extraction screen to show the picker again. */
    val reopenCandidates: StateFlow<Boolean> = _reopenCandidates

    fun set(snapshot: PageSnapshot, candidates: List<ClipCandidate>) {
        _current.value = Current(snapshot, candidates)
    }

    /** Candidate by id in the current page, or `null` when unknown / cleared. */
    fun candidate(id: String): ClipCandidate? =
        _current.value?.candidates?.firstOrNull { it.id == id }

    /** Adds [candidate] to the current page (no-op without a capture). */
    fun addCandidate(candidate: ClipCandidate) {
        val now = _current.value ?: return
        if (now.candidates.any { it.id == candidate.id }) return
        _current.value = now.copy(candidates = now.candidates + candidate)
    }

    fun setActiveExtraction(candidateId: String?) {
        _activeExtraction.value = candidateId
    }

    fun requestReopenCandidates() {
        _reopenCandidates.value = true
    }

    /** Clears the reopen request once the browser has acted on it. */
    fun consumeReopenCandidates() {
        _reopenCandidates.value = false
    }

    /** Drops the captured page (navigation) but keeps an in-flight extraction's marker. */
    fun clearPage() {
        _current.value = null
        _reopenCandidates.value = false
    }

    fun clear() {
        _current.value = null
        _activeExtraction.value = null
        _reopenCandidates.value = false
    }
}
