package com.llmlocal.recipekeeper.clip

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.ClipCandidate
import com.llmlocal.feature.webclipper.ClipSaveOptions
import com.llmlocal.feature.webclipper.ClipSink
import com.llmlocal.recipekeeper.data.RecipePictureStore
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.domain.RecipeRepository
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Persists an extracted recipe: attaches page metadata the parser could
 * not know (id, source URL/host, title fallback, timestamp), moves the
 * downloaded temp pictures into `filesDir/recipes/<id>/` and upserts.
 *
 * The parser's result is *detached* (empty id/source, maybe blank title);
 * the deterministic fallback already carries them. Either way [save]
 * normalises: an existing non-blank id is kept (fallback), otherwise a new
 * one is minted.
 */
class RecipeKeeperClipSink(
    private val repository: RecipeRepository,
    private val pictures: RecipePictureStore,
    private val dispatchers: DispatcherProvider,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
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
        val id = item.id.ifBlank { idFactory() }
        val paths = withContext(dispatchers.io) { this@RecipeKeeperClipSink.pictures.moveIn(id, pictures) }
        val recipe = item.copy(
            id = id,
            title = item.title.trim().ifBlank { candidate.title.trim() }.ifBlank { snapshot.title?.trim().orEmpty() }.ifBlank { "Untitled recipe" },
            sourceUrl = item.sourceUrl.ifBlank { snapshot.url },
            sourceHost = item.sourceHost.ifBlank { TextCleaner.host(snapshot.url).ifBlank { snapshot.siteName.orEmpty() } },
            pictures = paths,
            truncated = item.truncated || truncated,
            clippedAt = if (item.clippedAt > 0) item.clippedAt else clock(),
            picturesFailed = options.picturesFailed && paths.isEmpty(),
            pictureCandidates = options.pictureCandidates,
        )
        repository.upsert(recipe)
        return id
    }
}
