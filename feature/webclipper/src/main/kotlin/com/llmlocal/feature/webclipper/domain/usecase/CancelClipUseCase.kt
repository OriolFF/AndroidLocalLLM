package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.LlmGenerationSource

/**
 * Stops the model and releases the extraction marker. The caller cancels
 * its collection of [ClipUseCase] (which deletes the temp pictures).
 */
class CancelClipUseCase(
    private val llm: LlmGenerationSource,
    private val session: ClipSession,
) {

    /** @param reopenCandidates also ask the browser to show the candidate picker again */
    operator fun invoke(reopenCandidates: Boolean = false) {
        llm.cancel()
        session.setActiveExtraction(null)
        if (reopenCandidates) session.requestReopenCandidates()
    }
}
