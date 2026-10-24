package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.web.JsonLdNode
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.core.web.TextSection

/**
 * One thing on the page that could be clipped. Produced by
 * [WebClipConfig.detect], shown in the candidate picker sheet and handed
 * back to [WebClipConfig.renderLlmInput] / [WebClipConfig.pickPictures].
 *
 * @property id stable within one [ClipSession]; used as the navigation argument
 * @property title display title (JSON-LD `name` or section heading)
 * @property thumbnailUrl first picture URL, if any
 * @property snippet short preview text (first ~100 chars) when no meta is known
 * @property source whether the candidate came from structured data or text heuristics
 * @property ingredientCount from structured data when available
 * @property totalMinutes from structured data when available
 * @property payload what the config needs to render the LLM input
 * @property detectedByHints the candidate was found only thanks to hints the
 *   app learned earlier (never by the built-in rules); lets the save and
 *   penalty paths know whether those hints were used
 */
data class ClipCandidate(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val snippet: String?,
    val source: CandidateSource,
    val ingredientCount: Int?,
    val totalMinutes: Int?,
    val payload: CandidatePayload,
    val detectedByHints: Boolean = false,
)

/** Tier the candidate was detected by. */
enum class CandidateSource { STRUCTURED, TEXT, IMAGES, TRANSCRIPT }

/** The page region a [ClipCandidate] points at. */
sealed interface CandidatePayload {
    /** Tier A: one schema.org node. */
    data class Structured(val node: JsonLdNode) : CandidatePayload

    /** Tier B: one heading + the text under it. */
    data class Text(val section: TextSection) : CandidatePayload

    /** "Extract anyway": the whole readable article. */
    data object WholePage : CandidatePayload

    /** Prepared pictures; [count] of them. */
    data class Images(val count: Int) : CandidatePayload

    /** A voice transcript (placeholder, unused). */
    data object Transcript : CandidatePayload
}

/** Id of the single synthetic candidate of an images clip. */
const val IMAGES_CANDIDATE_ID = "images"

/**
 * The one candidate of an images clip; there is nothing to detect in a
 * picture without running the model. [title] comes from labels (rule 0).
 */
fun imagesCandidate(input: ClipInput.Images, title: String): ClipCandidate = ClipCandidate(
    id = IMAGES_CANDIDATE_ID,
    title = title,
    thumbnailUrl = null,
    snippet = null,
    source = CandidateSource.IMAGES,
    ingredientCount = null,
    totalMinutes = null,
    payload = CandidatePayload.Images(input.images.size),
)
