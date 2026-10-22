package com.llmlocal.recipekeeper.clip.domain.detection

import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.CandidatePayload
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe

/** Persists the hints derived from a kept extraction. */
class LearnDetectionHintsUseCase(
    private val derive: DeriveDetectionHintsUseCase,
    private val store: DetectionHintsStore,
) {
    /**
     * Learns only from pages the fast detector missed or was weak on
     * (`Text` / `WholePage` candidates). [detectedByHints] true means the
     * learned hints found the page; deriving then reinforces them.
     */
    suspend operator fun invoke(snapshot: PageSnapshot, recipe: ClippedRecipe, candidate: ClipCandidate, detectedByHints: Boolean) {
        if (candidate.payload is CandidatePayload.Structured) return
        val hints = derive(snapshot, recipe, candidate)
        if (hints.isEmpty()) return
        store.reinforce(hints)
    }
}

/** Costs the hints that led to a rejected extraction some confidence. */
class PenaliseDetectionHintsUseCase(private val store: DetectionHintsStore) {
    suspend operator fun invoke(host: String?, usedHints: DetectionHints) {
        val all = usedHints.generic + usedHints.forHost
        if (all.isEmpty()) return
        // Generic hints are penalised under the null host, host hints under theirs.
        penalise(null, usedHints.generic)
        penalise(host, usedHints.forHost)
    }

    private suspend fun penalise(host: String?, hints: List<DetectionHint>) {
        if (hints.isEmpty()) return
        store.penalise(host, hints.map { it.kind }.toSet(), hints.map { it.token }.toSet())
    }
}

/** "Reset learned detection". */
class ResetDetectionHintsUseCase(private val store: DetectionHintsStore) {
    suspend operator fun invoke() = store.clear()
}
