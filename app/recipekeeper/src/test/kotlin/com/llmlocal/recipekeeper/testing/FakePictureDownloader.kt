package com.llmlocal.recipekeeper.testing

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.feature.webclipper.domain.PictureDownloader
import java.io.File

/** Writes a tiny file for every URL not in [failing]; mirrors the feature's test fake. */
class FakePictureDownloader(
    private val failing: Set<String> = emptySet(),
) : PictureDownloader {
    val requests = mutableListOf<String>()

    override suspend fun download(url: String, referer: String?, into: File, maxLongEdgePx: Int): Outcome<File> {
        requests += url
        if (url in failing) return Outcome.Failure(IllegalStateException("404 $url"))
        into.parentFile?.mkdirs()
        into.writeBytes(byteArrayOf(1, 2, 3))
        return Outcome.Success(into)
    }
}
