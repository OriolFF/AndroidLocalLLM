package com.llmlocal.feature.webclipper

import com.llmlocal.core.web.JsonLdNode
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
)

/** Tier the candidate was detected by. */
enum class CandidateSource { STRUCTURED, TEXT }

/** The page region a [ClipCandidate] points at. */
sealed interface CandidatePayload {
    /** Tier A: one schema.org node. */
    data class Structured(val node: JsonLdNode) : CandidatePayload

    /** Tier B: one heading + the text under it. */
    data class Text(val section: TextSection) : CandidatePayload

    /** "Extract anyway": the whole readable article. */
    data object WholePage : CandidatePayload
}
