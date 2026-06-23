package com.llmlocal.feature.modelmanagement.mvi

import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelFamily

/**
 * Coarse size buckets — relevant because phone storage is the #1 limit on
 * local model execution. The thresholds map to the three common classes of
 * 2024-era on-device LLMs: quantised sub-1 GB "compact" models, 1–2 GB
 * "small" instruct/chat models, and > 2 GB "full" models.
 */
enum class SizeBucket(val displayName: String) {
    /** < 1 GB — fits on practically any device; quantised models live here. */
    COMPACT("Compact (< 1 GB)"),
    /** 1–2 GB — typical 3 B-parameter model. */
    SMALL("Small (1–2 GB)"),
    /** > 2 GB — needs >= 3 GB free storage and >= 4 GB RAM. */
    FULL("Full (> 2 GB)"),
}

enum class StatusFilter(val displayName: String) {
    ALL("All"),
    INSTALLED("Installed"),
    AVAILABLE("Available"),
    DOWNLOADING("Downloading"),
}

enum class QuantFilter(val displayName: String) {
    ALL("All"),
    QUANTISED("Quantised"),
    FULL_PRECISION("Full precision"),
}

/**
 * User-controlled filter set for the catalog. `null` on a field means
 * "no filter applied for that dimension". The view model combines
 * [LlmModelManagementState.catalog] with these filters to produce the
 * visible list.
 */
data class ModelFilters(
    val status: StatusFilter = StatusFilter.ALL,
    val family: ModelFamily? = null,
    val sizeBucket: SizeBucket? = null,
    val quant: QuantFilter = QuantFilter.ALL,
) {
    val isDefault: Boolean
        get() = status == StatusFilter.ALL && family == null &&
            sizeBucket == null && quant == QuantFilter.ALL

    companion object {
        val DEFAULT = ModelFilters()

        /**
         * Buckets a model's byte size into the [SizeBucket] taxonomy. The
         * thresholds are tuned for the on-device LLM space: < 1 GB for
         * 4-bit quantised models, 1–2 GB for "small" instruct/chat
         * checkpoints, > 2 GB for full-precision 7 B+ models.
         */
        fun bucketFor(sizeBytes: Long): SizeBucket = when {
            sizeBytes < 1L * 1024 * 1024 * 1024 -> SizeBucket.COMPACT
            sizeBytes < 2L * 1024 * 1024 * 1024 -> SizeBucket.SMALL
            else -> SizeBucket.FULL
        }

        /**
         * `true` if the descriptor carries the `quantised` tag — the
         * catalog uses a free-form tag convention rather than a dedicated
         * field, so we read it from there.
         */
        fun isQuantised(descriptor: LlmModelDescriptor): Boolean =
            descriptor.tags.any { it.equals("quantised", ignoreCase = true) }
    }
}
