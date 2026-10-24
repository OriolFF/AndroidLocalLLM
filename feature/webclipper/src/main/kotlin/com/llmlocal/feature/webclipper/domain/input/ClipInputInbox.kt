package com.llmlocal.feature.webclipper.domain.input

import kotlinx.coroutines.flow.StateFlow

/** What the share receiver hands over to the navigation owner. */
sealed interface PendingClipInput {
    data class Images(val uris: List<String>) : PendingClipInput
    data class Url(val url: String) : PendingClipInput
}

/**
 * Hand-off between the Activity that received a share and the NavHost that
 * navigates. Bound as a Koin `single`.
 */
interface ClipInputInbox {

    /** The input waiting to be consumed, or `null`. */
    val pending: StateFlow<PendingClipInput?>

    /** Stores [input], replacing anything still pending. */
    fun offer(input: PendingClipInput)

    /** Returns the pending input and clears it. */
    fun consume(): PendingClipInput?
}
