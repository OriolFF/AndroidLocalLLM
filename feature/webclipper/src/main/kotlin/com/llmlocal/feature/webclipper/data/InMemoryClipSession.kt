package com.llmlocal.feature.webclipper.data

import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.feature.webclipper.domain.ClipSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-lifetime [ClipSession] backed by `MutableStateFlow`s. */
class InMemoryClipSession : ClipSession {

    private val _current = MutableStateFlow<ClipSession.Current?>(null)
    override val current: StateFlow<ClipSession.Current?> = _current

    private val _activeExtraction = MutableStateFlow<String?>(null)
    override val activeExtraction: StateFlow<String?> = _activeExtraction

    private val _reopenCandidates = MutableStateFlow(false)
    override val reopenCandidates: StateFlow<Boolean> = _reopenCandidates

    override fun set(snapshot: PageSnapshot, candidates: List<ClipCandidate>) {
        _current.value = ClipSession.Current(snapshot, candidates)
    }

    override fun candidate(id: String): ClipCandidate? =
        _current.value?.candidates?.firstOrNull { it.id == id }

    override fun addCandidate(candidate: ClipCandidate) {
        val now = _current.value ?: return
        if (now.candidates.any { it.id == candidate.id }) return
        _current.value = now.copy(candidates = now.candidates + candidate)
    }

    override fun setActiveExtraction(candidateId: String?) {
        _activeExtraction.value = candidateId
    }

    override fun requestReopenCandidates() {
        _reopenCandidates.value = true
    }

    override fun consumeReopenCandidates() {
        _reopenCandidates.value = false
    }

    override fun clearPage() {
        _current.value = null
        _reopenCandidates.value = false
    }

    override fun clear() {
        _current.value = null
        _activeExtraction.value = null
        _reopenCandidates.value = false
    }
}
