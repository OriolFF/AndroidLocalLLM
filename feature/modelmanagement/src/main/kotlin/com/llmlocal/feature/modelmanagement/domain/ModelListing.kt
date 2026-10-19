package com.llmlocal.feature.modelmanagement.domain

import com.llmlocal.core.model.LlmModelDescriptor

/** Lifecycle of a single model download, mirroring what the data layer persists. */
enum class DownloadStatus { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED }

/** Snapshot of one model's download at a point in time. */
data class DownloadProgress(
    val modelId: String,
    val status: DownloadStatus,
    val bytesRead: Long,
    val totalBytes: Long?,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
    val failureReason: String? = null,
)

/** Terminal / live status of a scheduled download job. */
enum class DownloadWorkStatus { ENQUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

/**
 * One catalog entry with everything the screen needs to render it:
 * the descriptor plus whether it is installed / selected and its latest
 * download snapshot, if any.
 */
data class ModelListing(
    val descriptor: LlmModelDescriptor,
    val installed: Boolean,
    val selected: Boolean,
    val progress: DownloadProgress?,
) {
    val id: String get() = descriptor.id

    /** `true` only while bytes are actively transferring. */
    val downloading: Boolean get() = progress?.status == DownloadStatus.RUNNING
}
