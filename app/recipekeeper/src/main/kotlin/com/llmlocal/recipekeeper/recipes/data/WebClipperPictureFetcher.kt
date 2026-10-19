package com.llmlocal.recipekeeper.recipes.data

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.feature.webclipper.domain.PictureDownloader
import com.llmlocal.recipekeeper.recipes.domain.PictureFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * [PictureFetcher] over the clipper's [PictureDownloader], writing into the
 * clipper's cache dir ([tempDir]) so "Retry pictures" behaves exactly like
 * the pictures step of an extraction.
 */
class WebClipperPictureFetcher(
    private val downloader: PictureDownloader,
    private val tempDir: File,
    private val maxLongEdgePx: Int = MAX_LONG_EDGE_PX,
) : PictureFetcher {

    override suspend fun fetch(urls: List<String>, referer: String, tag: String): List<File> = coroutineScope {
        tempDir.mkdirs()
        urls.mapIndexed { index, url ->
            async {
                val into = File(tempDir, "$tag-$index.jpg")
                val outcome = try {
                    downloader.download(url, referer, into, maxLongEdgePx)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Outcome.Failure(e)
                }
                (outcome as? Outcome.Success)?.value
            }
        }.awaitAll().filterNotNull()
    }

    companion object {
        /** Same long-edge bound as `ExtractionViewModel.MAX_LONG_EDGE_PX`. */
        const val MAX_LONG_EDGE_PX = 1_280
    }
}
