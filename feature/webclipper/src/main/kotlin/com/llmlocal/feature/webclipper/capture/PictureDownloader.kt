package com.llmlocal.feature.webclipper.capture

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.web.ImageDownloader
import java.io.File

/**
 * Seam over `:core:web`'s final [ImageDownloader] so the extraction
 * ViewModel can be tested with a fake. Same contract: download [url] with
 * [referer] into [into], resized so the long edge is ≤ [maxLongEdgePx].
 */
fun interface PictureDownloader {
    suspend fun download(url: String, referer: String?, into: File, maxLongEdgePx: Int): Outcome<File>
}

/** Production [PictureDownloader] delegating to [ImageDownloader]. */
class ImageDownloaderPictureDownloader(
    private val delegate: ImageDownloader,
) : PictureDownloader {
    override suspend fun download(url: String, referer: String?, into: File, maxLongEdgePx: Int): Outcome<File> =
        delegate.download(url, referer, into, maxLongEdgePx)
}
