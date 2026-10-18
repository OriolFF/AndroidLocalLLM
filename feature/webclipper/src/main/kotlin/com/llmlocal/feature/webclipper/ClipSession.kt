package com.llmlocal.feature.webclipper

import com.llmlocal.core.web.PageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the capture + candidates for the page the user is currently on so
 * the browser and extraction routes (two ViewModels) share them without
 * re-capturing. Registered as a Koin `single`.
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

    fun set(snapshot: PageSnapshot, candidates: List<ClipCandidate>) {
        _current.value = Current(snapshot, candidates)
    }

    /** Candidate by id in the current page, or `null` when unknown / cleared. */
    fun candidate(id: String): ClipCandidate? =
        _current.value?.candidates?.firstOrNull { it.id == id }

    fun clear() {
        _current.value = null
    }
}
