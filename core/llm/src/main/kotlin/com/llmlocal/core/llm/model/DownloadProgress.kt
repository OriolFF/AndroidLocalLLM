package com.llmlocal.core.llm.model

/**
 * Reported during an in-progress model download. `percent` is in 0..100.
 */
data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long?,
) {
    val percent: Int
        get() = totalBytes?.takeIf { it > 0 }?.let { ((bytesRead * 100) / it).toInt() } ?: 0
}
