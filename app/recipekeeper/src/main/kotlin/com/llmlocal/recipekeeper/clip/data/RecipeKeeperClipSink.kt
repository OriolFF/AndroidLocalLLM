package com.llmlocal.recipekeeper.clip.data

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.feature.webclipper.domain.ClipSaveOptions
import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.feature.webclipper.domain.input.snapshotOrNull
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.recipes.domain.RecipePictureStore
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import com.llmlocal.recipekeeper.clip.domain.TextCleaner
import com.llmlocal.recipekeeper.clip.domain.detection.LearnDetectionHintsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Persists an extracted recipe: attaches page metadata the parser could
 * not know (id, source URL/host, title fallback, timestamp), moves the
 * downloaded temp pictures into `filesDir/recipes/<id>/` and upserts.
 *
 * The parser's result is *detached* (empty id/source, maybe blank title);
 * the deterministic fallback already carries them. Either way [save]
 * normalises: `options.replaceId` wins (the user chose "Replace" on the
 * duplicate warning — the old row and its picture folder are replaced),
 * else an existing non-blank id is kept (fallback), else a new one is
 * minted. [existingId] answers the duplicate check by exact source URL.
 *
 * After the row is written the sink asks [learning] to learn detection
 * hints from the page, fire-and-forget on [learningScope]: a failure there
 * is logged and never reaches the save.
 */
class RecipeKeeperClipSink(
    private val repository: RecipeRepository,
    private val pictures: RecipePictureStore,
    private val dispatchers: DispatcherProvider,
    private val untitledTitle: String,
    /** The config's pre-checked picks among the proposed pictures (`rankPictures().filter { preferred }`). */
    private val preferredPictures: (ClipCandidate, PageSnapshot, Int) -> List<String> = { _, _, _ -> emptyList() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val learning: LearnDetectionHintsUseCase? = null,
    private val learningScope: CoroutineScope? = null,
) : ClipSink<ClippedRecipe> {

    override suspend fun save(
        item: ClippedRecipe,
        snapshot: PageSnapshot,
        candidate: ClipCandidate,
        pictures: List<File>,
        truncated: Boolean,
    ): String = save(item, snapshot, candidate, pictures, truncated, ClipSaveOptions())

    override suspend fun save(
        item: ClippedRecipe,
        snapshot: PageSnapshot,
        candidate: ClipCandidate,
        pictures: List<File>,
        truncated: Boolean,
        options: ClipSaveOptions,
    ): String {
        val id = options.replaceId ?: item.id.ifBlank { idFactory() }
        // Replace keeps the user's favourite flag; the re-clip is a pristine clip again.
        val wasFavourite = options.replaceId != null && repository.observe(id).first()?.isFavourite == true
        val paths = withContext(dispatchers.io) {
            if (options.replaceId != null) this@RecipeKeeperClipSink.pictures.deleteAll(id)
            this@RecipeKeeperClipSink.pictures.moveIn(id, pictures)
        }
        val recipe = item.copy(
            id = id,
            title = item.title.trim().ifBlank { candidate.title.trim() }.ifBlank { snapshot.title?.trim().orEmpty() }.ifBlank { untitledTitle },
            sourceUrl = item.sourceUrl.ifBlank { snapshot.url },
            sourceHost = item.sourceHost.ifBlank { TextCleaner.host(snapshot.url).ifBlank { snapshot.siteName.orEmpty() } },
            pictures = paths,
            truncated = item.truncated || truncated,
            clippedAt = if (item.clippedAt > 0) item.clippedAt else clock(),
            picturesFailed = options.picturesFailed && paths.isEmpty(),
            pictureCandidates = retryCandidates(candidate, snapshot, options),
            isFavourite = wasFavourite,
            origin = RecipeOrigin.CLIPPED,
            editedAt = null,
        )
        repository.upsert(recipe)
        learnFrom(snapshot, recipe, candidate, options.usedHints)
        return id
    }

    override suspend fun save(
        item: ClippedRecipe,
        input: ClipInput,
        candidate: ClipCandidate,
        pictures: List<File>,
        truncated: Boolean,
        options: ClipSaveOptions,
    ): String = when (input) {
        is ClipInput.WebPage -> save(item, input.snapshot, candidate, pictures, truncated, options)
        is ClipInput.Images -> saveFromImages(item, candidate, pictures, truncated)
        is ClipInput.Voice -> throw UnsupportedOperationException("Voice clips are not supported yet")
    }

    /**
     * Images path: no page, so no source URL/host, no duplicate replace, no
     * retry candidates and nothing to learn; the input pictures are the
     * recipe's pictures.
     */
    private suspend fun saveFromImages(
        item: ClippedRecipe,
        candidate: ClipCandidate,
        pictures: List<File>,
        truncated: Boolean,
    ): String {
        val id = item.id.ifBlank { idFactory() }
        val paths = withContext(dispatchers.io) { this@RecipeKeeperClipSink.pictures.moveIn(id, pictures) }
        val recipe = item.copy(
            id = id,
            title = item.title.trim().ifBlank { candidate.title.trim() }.ifBlank { untitledTitle },
            sourceUrl = "",
            sourceHost = "",
            pictures = paths,
            truncated = item.truncated || truncated,
            clippedAt = if (item.clippedAt > 0) item.clippedAt else clock(),
            picturesFailed = false,
            pictureCandidates = emptyList(),
            isFavourite = false,
            origin = RecipeOrigin.IMAGES,
            editedAt = null,
        )
        repository.upsert(recipe)
        return id
    }

    private fun learnFrom(snapshot: PageSnapshot, recipe: ClippedRecipe, candidate: ClipCandidate, usedHints: Boolean) {
        val learn = learning ?: return
        val scope = learningScope ?: return
        scope.launch(dispatchers.io) {
            try {
                learn(snapshot, recipe, candidate, usedHints)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.w(TAG, "learning detection hints failed", e) }
            }
        }
    }

    /**
     * What "Retry pictures" may download later: the pictures the clipper would
     * have pre-checked, not every proposal (the unchecked page / related-recipe
     * images made a retry bring in pictures the clip had left out — WS12 D).
     * Falls back to the whole list when none of the proposals is preferred.
     */
    private fun retryCandidates(candidate: ClipCandidate, snapshot: PageSnapshot, options: ClipSaveOptions): List<String> {
        val proposed = options.pictureCandidates
        if (proposed.isEmpty()) return proposed
        val preferred = preferredPictures(candidate, snapshot, proposed.size).toSet()
        return proposed.filter { it in preferred }.ifEmpty { proposed }
    }

    /** Images (and voice) have no URL to match: never a duplicate. */
    override suspend fun existingId(input: ClipInput): String? = input.snapshotOrNull?.let { existingId(it) }

    override suspend fun existingId(snapshot: PageSnapshot): String? =
        withContext(dispatchers.io) { repository.findIdBySourceUrl(snapshot.url) }

    private companion object {
        const val TAG = "RecipeKeeperClipSink"
    }
}
