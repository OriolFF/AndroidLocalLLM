package com.llmlocal.core.llm.model

import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelFamily

/**
 * Static catalog of supported on-device LLM models.
 *
 * `ALL` is the single source of truth for "which models does this app know
 * about?" — the model-management screen lists every entry, the selection
 * store keys selections by `id`, and the download worker looks up the URL
 * from here.
 *
 * The catalog is curated (hardcoded) for v1; future revisions can layer a
 * `RemoteLlmModelCatalog` on top without changing the UI: the only thing
 * the feature consumes is `ALL` + `findById(...)`.
 */
object LlmModelCatalog {

    /**
     * Default model — **Gemma 4 E2B IT** in `.litertlm` format.
     *
     * For Snapdragon 8 Elite devices the
     * `gemma-4-E2B-it_qualcomm_sm8750.litertlm` variant runs ~6× faster via
     * NPU. For Google Tensor G5 devices (Pixel 9+), use
     * `gemma-4-E2B-it_Google_Tensor_G5.litertlm`. The generic
     * `gemma-4-E2B-it.litertlm` runs cross-platform via CPU.
     */
    val DEFAULT_MODEL: LlmModelDescriptor = LlmModelDescriptor(
        id = "gemma-4-E2B-it",
        displayName = "Gemma 4 E2B IT",
        description = "Google's compact instruction-tuned model in LiteRT-LM " +
            "format. Cross-platform (CPU) — works on every device but slower " +
            "than the device-optimised NPU variants. A good baseline.",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_580L * 1024 * 1024, // ~2.58 GB
        filename = "gemma-4-E2B-it.litertlm",
        tags = listOf("instruction-tuned", "google", "cross-platform"),
        license = "Gemma Terms",
        author = "Google",
        minRamMb = 4_096,
        family = ModelFamily.INSTRUCT,
    )

    /** Smaller, quantised variant of the default — same family, faster download. */
    private val GEMMA_Q4: LlmModelDescriptor = LlmModelDescriptor(
        id = "gemma-4-E2B-it-q4",
        displayName = "Gemma 4 E2B IT (q4 quantised)",
        description = "4-bit weight-quantised version of Gemma 4 E2B IT. " +
            "Smaller footprint and faster download at the cost of some " +
            "output quality. Best for low-storage devices.",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-q4.litertlm",
        sizeBytes = 980L * 1024 * 1024, // ~980 MB
        filename = "gemma-4-E2B-it-q4.litertlm",
        tags = listOf("instruction-tuned", "quantised", "compact", "google"),
        license = "Gemma Terms",
        author = "Google",
        minRamMb = 2_048,
        family = ModelFamily.INSTRUCT,
    )

    /** Phi-3.5 Mini IT — Microsoft's compact instruction-tuned model. */
    private val PHI_3_5_MINI: LlmModelDescriptor = LlmModelDescriptor(
        id = "phi-3.5-mini-it",
        displayName = "Phi-3.5 Mini IT",
        description = "Microsoft's compact 3.8B-parameter instruction-tuned " +
            "model. Punches above its weight on reasoning and structured " +
            "tasks; useful alternative to Gemma for varied prompts.",
        url = "https://huggingface.co/litert-community/Phi-3.5-mini-instruct-litert-lm/resolve/main/Phi-3.5-mini-instruct.litertlm",
        sizeBytes = 2_300L * 1024 * 1024, // ~2.30 GB
        filename = "Phi-3.5-mini-instruct.litertlm",
        tags = listOf("instruction-tuned", "microsoft", "reasoning"),
        license = "MIT",
        author = "Microsoft",
        minRamMb = 4_096,
        family = ModelFamily.INSTRUCT,
    )

    /** Llama 3.2 3B Instruct — Meta's chat-tuned model. */
    private val LLAMA_3_2_3B: LlmModelDescriptor = LlmModelDescriptor(
        id = "llama-3.2-3b-it",
        displayName = "Llama 3.2 3B Instruct",
        description = "Meta's 3B-parameter chat-tuned model — a strong " +
            "general-purpose baseline with broad community support. Good " +
            "default if you prefer the Llama family.",
        url = "https://huggingface.co/litert-community/Llama-3.2-3B-Instruct-litert-lm/resolve/main/Llama-3.2-3B-Instruct.litertlm",
        sizeBytes = 1_950L * 1024 * 1024, // ~1.95 GB
        filename = "Llama-3.2-3B-Instruct.litertlm",
        tags = listOf("chat", "meta", "general-purpose"),
        license = "Llama 3.2 Community License",
        author = "Meta",
        minRamMb = 4_096,
        family = ModelFamily.CHAT,
    )

    /** All models known to the app, in display order. */
    val ALL: List<LlmModelDescriptor> = listOf(
        DEFAULT_MODEL,
        GEMMA_Q4,
        PHI_3_5_MINI,
        LLAMA_3_2_3B,
    )

    /** Lookup by stable [LlmModelDescriptor.id]; null if no such model. */
    fun findById(id: String): LlmModelDescriptor? = ALL.firstOrNull { it.id == id }
}