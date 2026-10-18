package com.llmlocal.feature.webclipper

import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.web.PageSnapshot

/**
 * The seam between the generic web-clipper feature and an app's domain.
 *
 * Each white-label app supplies one implementation (e.g.
 * `RecipeKeeperClipConfig : WebClipConfig<ClippedRecipe>`): how to find
 * candidates on a captured page, how to turn one into LLM input, how to
 * map structured data deterministically when the model fails, and which
 * pictures to keep. The LLM only ever sees text; pictures are stored with
 * the saved item.
 */
interface WebClipConfig<T : Any> {

    /** Prompt builder + parser; `promptBuilder.userPrompt(listOf(renderedInput))` is what runs. */
    val llmConfig: LlmTaskConfig<T>

    /** Tier A (structured) + Tier B (text) detection, ranked, at most 10. */
    fun detect(snapshot: PageSnapshot): List<ClipCandidate>

    /** Compact text for the model, trimmed to [budgetChars]. */
    fun renderLlmInput(candidate: ClipCandidate, snapshot: PageSnapshot, budgetChars: Int): String

    /** Deterministic mapping for STRUCTURED candidates; `null` otherwise. */
    fun fallback(candidate: ClipCandidate, snapshot: PageSnapshot): T?

    /**
     * Last check on a parsed model result before it is kept. `false` is
     * treated exactly like a parse failure: [fallback] for STRUCTURED
     * candidates, otherwise the parse-failed state. Apps use it to reject
     * results the grammar accepts but the page cannot have produced (e.g.
     * a whole-page extraction with no quantities in its ingredients).
     */
    fun accept(candidate: ClipCandidate, item: T): Boolean = true

    /** Ranked picture URLs to download alongside the item, at most [max]. */
    fun pickPictures(candidate: ClipCandidate, snapshot: PageSnapshot, max: Int): List<String>

    /**
     * When `true`, a STRUCTURED candidate whose [fallback] passes
     * [isComplete] is saved straight away and the model is never run for
     * it (plan decision #3). `false` keeps today's behaviour: the model
     * always runs and [fallback] is only a parse-failure net.
     */
    val skipModelWhenStructuredComplete: Boolean get() = false

    /** Whether a deterministic [fallback] result is good enough to skip the model. */
    fun isComplete(item: T): Boolean = true

    /** Prompt budget in characters (~2,500 tokens). */
    val budgetChars: Int get() = 9_000

    /** Passed to the engine as `maxOutputTokens`. */
    val maxOutputTokens: Int get() = 1_024

    /** Upper bound for [pickPictures]. */
    val maxPictures: Int get() = 8
}
