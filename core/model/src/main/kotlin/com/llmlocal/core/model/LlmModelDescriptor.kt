package com.llmlocal.core.model

/**
 * High-level grouping for models in the catalog — drives the UI's filter
 * chips (chat vs. instruction-tuned vs. code, etc.).
 */
enum class ModelFamily { GENERAL, CHAT, INSTRUCT, CODE, MULTIMODAL }

/**
 * Describes a downloadable on-device LLM model.
 *
 * The catalog (see `LlmModelCatalog`) is the single source of truth for
 * "which models does this app know about?" — every model in
 * `LlmModelCatalog.ALL` must be present here. The descriptor is also the
 * key that the [LlmModelManager][com.llmlocal.core.llm.download.LlmModelManager]
 * uses to download / locate / remove files on disk.
 *
 * @property id Stable identifier — primary key for selection, downloads,
 *   installed-set membership. Don't reuse an `id` across different files.
 * @property displayName User-facing name shown in the model management list.
 * @property description One-paragraph summary shown in the detail sheet.
 * @property url Direct download URL (HuggingFace resolve endpoint or CDN).
 * @property sizeBytes Approximate size of the model on disk, drives progress
 *   reporting and the size label.
 * @property filename Filename inside `Context.filesDir/llm/`. Unique per id.
 * @property sha256 Optional checksum; if present, the downloaded file is
 *   verified against it after the download completes.
 * @property tags Free-form labels shown as chips (e.g. "instruction-tuned",
 *   "quantised", "google").
 * @property license SPDX-ish license name shown in the detail sheet.
 * @property author Provider (Google, Meta, Microsoft…). Shown in the detail sheet.
 * @property minRamMb Hint for the UI's "won't run on this device" warning
 *   (informational only — not enforced).
 * @property family Coarse grouping; drives filter chips in the catalog list.
 */
data class LlmModelDescriptor(
    val id: String,
    val displayName: String,
    val description: String,
    val url: String,
    val sizeBytes: Long,
    val filename: String,
    val sha256: String? = null,
    val tags: List<String> = emptyList(),
    val license: String = "Unknown",
    val author: String = "Unknown",
    val minRamMb: Int = 0,
    val family: ModelFamily = ModelFamily.GENERAL,
)