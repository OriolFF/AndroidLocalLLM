package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.feature.webclipper.domain.input.snapshotOrNull
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the capture + candidates for the page the user is currently on so
 * the browser and extraction routes (two ViewModels) share them without
 * re-capturing. Bound as a Koin `single` to `InMemoryClipSession`.
 *
 * Besides the captured page it carries two small cross-route signals:
 *
 *  - [activeExtraction]: the candidate id an extraction is currently
 *    running for, so the browser can show `ClipFabState.Extracting` and
 *    route a tap back to that screen.
 *  - [reopenCandidates]: set by the extraction screen's "Not this one?" so
 *    the browser re-opens the candidate sheet when the user comes back.
 */
interface ClipSession {

    /** One clip input (page, pictures, …) and the candidates detected on it. */
    data class Current(
        val input: ClipInput,
        val candidates: List<ClipCandidate>,
    ) {
        /** Web-only convenience; `null` for images and voice. */
        val snapshot: PageSnapshot? get() = input.snapshotOrNull
    }

    /** `null` until the first capture, and again after [clear]. */
    val current: StateFlow<Current?>

    /** Candidate id of the extraction in flight, or `null`. */
    val activeExtraction: StateFlow<String?>

    /** One-shot request from the extraction screen to show the picker again. */
    val reopenCandidates: StateFlow<Boolean>

    fun set(input: ClipInput, candidates: List<ClipCandidate>)

    fun set(snapshot: PageSnapshot, candidates: List<ClipCandidate>) =
        set(ClipInput.WebPage(snapshot), candidates)

    /** Candidate by id in the current page, or `null` when unknown / cleared. */
    fun candidate(id: String): ClipCandidate?

    /** Adds [candidate] to the current page (no-op without a capture). */
    fun addCandidate(candidate: ClipCandidate)

    fun setActiveExtraction(candidateId: String?)

    fun requestReopenCandidates()

    /** Clears the reopen request once the browser has acted on it. */
    fun consumeReopenCandidates()

    /** Drops the captured page (navigation) but keeps an in-flight extraction's marker. */
    fun clearPage()

    fun clear()
}
