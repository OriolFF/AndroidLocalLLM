package com.llmlocal.core.model

/**
 * Describes a downloadable on-device LLM model.
 *
 * The template ships with a single default model — Gemma 3 1B IT in MediaPipe
 * `.task` format — but this descriptor abstracts the URL, size, and filename
 * so swapping in another model is a one-line change.
 *
 * @property id Human-readable model identifier, e.g. "gemma3-1b-it-int4".
 * @property url Direct download URL (HuggingFace resolve endpoint or CDN).
 * @property sizeBytes Approximate size of the model on disk, used for progress
 *   reporting during the download.
 * @property filename The filename to save the model as, inside
 *   `Context.filesDir/llm/`.
 * @property sha256 Optional checksum; if present, the downloaded file is
 *   verified against it after the download completes.
 */
data class LlmModelDescriptor(
    val id: String,
    val url: String,
    val sizeBytes: Long,
    val filename: String,
    val sha256: String? = null,
)
