package com.llmlocal.feature.webclipper.data

import android.webkit.CookieManager
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.web.ImageDownloader
import com.llmlocal.feature.webclipper.domain.PictureDownloader
import java.io.File

/**
 * Production [PictureDownloader] delegating to `:core:web`'s final
 * [ImageDownloader], forwarding the WebView's cookies for the image host
 * (hot-link / bot protection on CDNs such as Chefkoch's).
 */
class ImageDownloaderPictureDownloader(
    private val delegate: ImageDownloader,
) : PictureDownloader {
    override suspend fun download(url: String, referer: String?, into: File, maxLongEdgePx: Int): Outcome<File> =
        delegate.download(url, referer, into, maxLongEdgePx, cookiesFor(url))

    private fun cookiesFor(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
}
