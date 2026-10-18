package com.llmlocal.core.web

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import okhttp3.OkHttpClient
import java.io.File

/**
 * Downloads and resizes one page image for local storage.
 *
 * Sends `Referer` (the page URL) and a browser `User-Agent` since many
 * CDNs require them, streams into a temp file and renames on success,
 * then decodes with `inSampleSize` so the long edge is at most
 * [maxLongEdgePx] and re-encodes as JPEG q85. Rejects responses whose
 * `Content-Length` exceeds 15 MB.
 *
 * Pictures are storage only: nothing produced here is ever sent to the
 * LLM. Implementation lands in WS2.
 */
class ImageDownloader(
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * @param url absolute image URL
     * @param referer page URL to send as `Referer`, or `null`
     * @param into destination file (created/overwritten on success)
     * @param maxLongEdgePx resize bound for the longer edge
     */
    suspend fun download(
        url: String,
        referer: String?,
        into: File,
        maxLongEdgePx: Int,
    ): Outcome<File> = TODO("WS2")
}
