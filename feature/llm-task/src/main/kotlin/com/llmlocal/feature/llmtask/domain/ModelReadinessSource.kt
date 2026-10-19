package com.llmlocal.feature.llmtask.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Source of the selected model's readiness (selection → download → disk → engine). */
interface ModelReadinessSource {
    /** Starts observing under [scope] and returns the hot readiness state. */
    fun observe(scope: CoroutineScope): StateFlow<ModelAvailability>

    /** Re-runs the disk + engine check for the current selection. */
    suspend fun recheck()
}
