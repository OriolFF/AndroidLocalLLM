package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.input.ClipInput

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

    /**
     * Called by [com.llmlocal.feature.webclipper.domain.usecase.ScanPageUseCase]
     * with the captured page's host just before [detect], so an app can load
     * anything host-specific (learned hints, …) that the synchronous [detect]
     * then uses. Default: nothing to load.
     */
    suspend fun onPageLoaded(host: String?) {}

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
     * Same ranking as [pickPictures], with a [PicturePick.preferred] flag per
     * entry. The extraction screen starts only *preferred* picks checked
     * (the first few of them); the rest are offered unchecked so pictures of
     * neighbouring recipes on a round-up page are never saved by default
     * (WS10 bug 4). The default wraps [pickPictures] with every pick
     * preferred, which is the pre-WS11 behaviour.
     */
    fun rankPictures(candidate: ClipCandidate, snapshot: PageSnapshot, max: Int): List<PicturePick> =
        pickPictures(candidate, snapshot, max).map { PicturePick(it) }

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

    /** Upper bound for [pickPictures] / [rankPictures]. */
    val maxPictures: Int get() = 8

    /** Limits and switches of the "From images" source; disabled by default. */
    val imagePolicy: ImagePolicy get() = ImagePolicy()

    /** The user turn of an images clip; the pictures travel on the request, not in the text. */
    fun renderImagesPrompt(input: ClipInput.Images): String = DEFAULT_IMAGES_PROMPT

    /** Candidates of an images clip: one synthetic candidate, no detection. */
    fun candidatesFor(input: ClipInput.Images, title: String): List<ClipCandidate> =
        listOf(imagesCandidate(input, title))

    /** Dispatches on the input kind; web pages go to the snapshot overload. */
    fun renderLlmInput(candidate: ClipCandidate, input: ClipInput, budgetChars: Int): String = when (input) {
        is ClipInput.WebPage -> renderLlmInput(candidate, input.snapshot, budgetChars)
        is ClipInput.Images -> renderImagesPrompt(input)
        is ClipInput.Voice -> input.transcript.take(budgetChars)
    }
}

/** Model-facing user turn of an images clip (not user-visible). */
const val DEFAULT_IMAGES_PROMPT = "Extract the recipe from these pictures."

/**
 * Limits of the images source.
 *
 * @property enabled off hides the source everywhere
 * @property maxImages cap after tiling; copy says "up to N pictures"
 * @property visualTokenBudget tokens per image the vision encoder may spend
 *   (the shipped bundle caps it at 280)
 * @property maxLongEdgePx long-edge bound of a prepared picture
 * @property tallAspect height/width above which a picture counts as a tall strip
 * @property tileImages split tall strips into overlapping viewport-shaped tiles
 */
data class ImagePolicy(
    val enabled: Boolean = false,
    val maxImages: Int = 3,
    val visualTokenBudget: Int = 280,
    val maxLongEdgePx: Int = 2_400,
    val tallAspect: Double = 2.5,
    val tileImages: Boolean = true,
)

/**
 * One picture proposed by [WebClipConfig.rankPictures].
 *
 * @property preferred the picture belongs to the item itself (its own
 *   structured-data images, the section's inline images); only preferred
 *   picks start checked on the extraction screen
 */
data class PicturePick(
    val url: String,
    val preferred: Boolean = true,
)
