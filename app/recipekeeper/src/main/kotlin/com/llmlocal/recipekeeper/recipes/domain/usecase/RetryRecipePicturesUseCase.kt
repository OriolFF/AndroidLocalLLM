package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.PictureFetcher
import com.llmlocal.recipekeeper.recipes.domain.RecipePictureStore
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import kotlinx.coroutines.withContext

/**
 * "Retry pictures" after an offline clip: downloads the first
 * [RETRY_PICTURES] stored [ClippedRecipe.pictureCandidates] — which the clip
 * sink limits to the clipper's pre-checked (preferred) picks, so a retry never
 * brings in related-recipe or page images the clip left out — moves them into
 * the recipe's folder and replaces the picture list, clearing
 * `picturesFailed` when at least one arrived.
 */
class RetryRecipePicturesUseCase(
    private val repository: RecipeRepository,
    private val fetcher: PictureFetcher,
    private val store: RecipePictureStore,
    private val dispatchers: DispatcherProvider,
) {
    /** @return `true` when at least one picture was stored */
    suspend operator fun invoke(recipe: ClippedRecipe): Boolean {
        val urls = recipe.pictureCandidates.take(RETRY_PICTURES)
        if (urls.isEmpty()) return false
        val paths = withContext(dispatchers.io) {
            val files = fetcher.fetch(urls, referer = recipe.sourceUrl, tag = "retry-${recipe.id}")
            store.moveIn(recipe.id, files)
        }
        repository.replacePictures(recipe.id, paths, picturesFailed = paths.isEmpty())
        return paths.isNotEmpty()
    }

    companion object {
        /** How many stored candidates a retry downloads (mirrors the extraction screen's default keep). */
        const val RETRY_PICTURES = 3
    }
}
