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

    /**
     * [save] with the extra facts in [options] (WS11). The ViewModel always
     * calls this overload; the default forwards to the five-argument [save]
     * so sinks that do not care keep compiling unchanged.
     */
    suspend fun save(
        item: T,
        snapshot: PageSnapshot,
        candidate: ClipCandidate,
        pictures: List<File>,
        truncated: Boolean,
        options: ClipSaveOptions,
    ): String = save(item, snapshot, candidate, pictures, truncated)
}

/**
 * Extra facts handed to [ClipSink.save].
 *
 * @property pictureCandidates every picture URL the config proposed, in
 *   rank order, so the app can offer "Retry pictures" later without the page
 * @property picturesFailed pictures were proposed but none could be
 *   downloaded (offline, blocked CDN); the app surfaces this on the saved item
 */
data class ClipSaveOptions(
    val pictureCandidates: List<String> = emptyList(),
    val picturesFailed: Boolean = false,
)
