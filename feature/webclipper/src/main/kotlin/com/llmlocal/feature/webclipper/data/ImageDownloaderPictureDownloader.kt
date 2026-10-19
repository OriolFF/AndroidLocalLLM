package com.llmlocal.feature.webclipper.data

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.web.ImageDownloader
import com.llmlocal.feature.webclipper.domain.PictureDownloader
import java.io.File

/** Production [PictureDownloader] delegating to `:core:web`'s final [ImageDownloader]. */
class ImageDownloaderPictureDownloader(
    private val delegate: ImageDownloader,
) : PictureDownloader {
    override suspend fun download(url: String, referer: String?, into: File, maxLongEdgePx: Int): Outcome<File> =
        delegate.download(url, referer, into, maxLongEdgePx)
}
