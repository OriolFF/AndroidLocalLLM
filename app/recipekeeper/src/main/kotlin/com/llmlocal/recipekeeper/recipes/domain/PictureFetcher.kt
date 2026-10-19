package com.llmlocal.recipekeeper.recipes.domain

import java.io.File

/** Downloads remote pictures to temporary files; the caller moves them into a [RecipePictureStore]. */
fun interface PictureFetcher {

    /**
     * Downloads [urls] concurrently, sending [referer] with each request.
     * A URL that fails is skipped; cancellation propagates. [tag] makes the
     * temp file names unique per caller (e.g. the recipe id).
     *
     * @return the downloaded files, in [urls] order, without the failures
     */
    suspend fun fetch(urls: List<String>, referer: String, tag: String): List<File>
}
