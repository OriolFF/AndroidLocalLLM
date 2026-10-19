package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.common.result.Outcome
import java.io.File

/**
 * Seam over the picture download so the clip pipeline can be tested with a
 * fake: download [url] with [referer] into [into], resized so the long edge
 * is ≤ [maxLongEdgePx].
 */
fun interface PictureDownloader {
    suspend fun download(url: String, referer: String?, into: File, maxLongEdgePx: Int): Outcome<File>
}
