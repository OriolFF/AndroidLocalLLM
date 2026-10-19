package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.web.PageSnapshot
import java.net.URI

/** Id of the synthetic "extract anyway" candidate. */
const val WHOLE_PAGE_CANDIDATE_ID = "whole-page"

/**
 * The synthetic candidate behind "Extract anyway" / "Try the whole page":
 * the readable article of [snapshot] as one TEXT candidate.
 */
fun wholePageCandidate(snapshot: PageSnapshot): ClipCandidate = ClipCandidate(
    id = WHOLE_PAGE_CANDIDATE_ID,
    title = snapshot.title?.takeIf { it.isNotBlank() }
        ?: snapshot.readable?.title?.takeIf { it.isNotBlank() }
        ?: hostOf(snapshot.url)
        ?: snapshot.url,
    thumbnailUrl = snapshot.openGraph["og:image"] ?: snapshot.openGraph["twitter:image"],
    snippet = snapshot.readable?.excerpt,
    source = CandidateSource.TEXT,
    ingredientCount = null,
    totalMinutes = null,
    payload = CandidatePayload.WholePage,
)

/** `www.`-stripped host of [url], or `null` when it cannot be parsed. */
fun hostOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val host = runCatching { URI(url).host }.getOrNull() ?: return null
    return host.removePrefix("www.").takeIf { it.isNotBlank() }
}
