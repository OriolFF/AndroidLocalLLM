package com.llmlocal.feature.webclipper

import com.llmlocal.core.web.PageSnapshot
import java.io.File

/**
 * App-owned persistence for a clipped item. The extraction ViewModel calls
 * [save] once the model output has been parsed (or the deterministic
 * fallback applied) and the picture downloads have settled.
 */
interface ClipSink<T : Any> {

    /**
     * @param item parsed result
     * @param snapshot the captured page it came from
     * @param candidate the candidate the user picked
     * @param pictures downloaded picture files (temporary; move or copy them)
     * @param truncated whether the model output looked cut off
     * @return the saved item's id, used for navigation
     */
    suspend fun save(
        item: T,
        snapshot: PageSnapshot,
        candidate: ClipCandidate,
        pictures: List<File>,
        truncated: Boolean,
    ): String
}
