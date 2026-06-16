package com.llmlocal.core.llm.model

/**
 * Reported during an in-progress model download.
 *
 * @property bytesRead bytes written to disk so far.
 * @property totalBytes total expected bytes (`null` if the server didn't
 *   send `Content-Length`).
 * @property speedBytesPerSec rolling average throughput, bytes/sec.
 *   `0` if not enough samples have been collected yet.
 * @property elapsedMs time since the download started, in milliseconds.
 */
data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long?,
    val speedBytesPerSec: Long = 0,
    val elapsedMs: Long = 0,
) {
    /** Percent in 0..100. Returns 0 when the total is unknown. */
    val percent: Int
        get() = totalBytes?.takeIf { it > 0 }
            ?.let { ((bytesRead * 100) / it).toInt().coerceIn(0, 100) }
            ?: 0

    /** Estimated time remaining, in seconds. Null when not computable. */
    val etaSeconds: Long?
        get() = totalBytes
            ?.takeIf { it > 0 }
            ?.takeIf { bytesRead > 0 }
            ?.let { total ->
                val remaining = total - bytesRead
                if (speedBytesPerSec > 0) remaining / speedBytesPerSec else null
            }
}
