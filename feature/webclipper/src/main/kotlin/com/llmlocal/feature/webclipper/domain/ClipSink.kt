package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.feature.webclipper.domain.input.snapshotOrNull
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

    /**
     * Input-aware [save]. The default serves web pages and refuses the rest,
     * so an app opts in per input kind.
     */
    suspend fun save(
        item: T,
        input: ClipInput,
        candidate: ClipCandidate,
        pictures: List<File>,
        truncated: Boolean,
        options: ClipSaveOptions,
    ): String = when (input) {
        is ClipInput.WebPage -> save(item, input.snapshot, candidate, pictures, truncated, options)
        else -> throw UnsupportedOperationException("sink does not support ${input::class.simpleName}")
    }

    /** Input-aware [existingId]; `null` for inputs without a page. */
    suspend fun existingId(input: ClipInput): String? = input.snapshotOrNull?.let { existingId(it) }

    /**
     * Id of an item already saved from [snapshot]'s page, or `null`. Asked
     * once at extraction start, before the model runs, so the user can be
     * warned ("Already in your library — Replace / Keep both / Cancel")
     * without wasting compute. Default: never a duplicate.
     */
    suspend fun existingId(snapshot: PageSnapshot): String? = null
}

/**
 * Extra facts handed to [ClipSink.save].
 *
 * @property replaceId id of the previously saved item this clip replaces
 *   (the user chose "Replace" on the duplicate warning); the sink reuses
 *   that id and discards the old pictures. `null` = save as a new item.
 * @property pictureCandidates every picture URL the config proposed, in
 *   rank order, so the app can offer "Retry pictures" later without the page
 * @property picturesFailed pictures were proposed but none could be
 *   downloaded (offline, blocked CDN); the app surfaces this on the saved item
 * @property usedHints the candidate was found thanks to learned detection
 *   hints (`ClipCandidate.detectedByHints`), so the sink reinforces them
 *   instead of learning from scratch
 */
data class ClipSaveOptions(
    val replaceId: String? = null,
    val pictureCandidates: List<String> = emptyList(),
    val picturesFailed: Boolean = false,
    val usedHints: Boolean = false,
)
