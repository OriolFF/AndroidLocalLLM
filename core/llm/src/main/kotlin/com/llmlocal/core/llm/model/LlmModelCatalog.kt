package com.llmlocal.core.llm.model

import com.llmlocal.core.model.LlmModelDescriptor

/**
 * Static catalog of supported on-device LLM models. The template ships with
 * **Gemma 4 E2B IT** as the default model — Google's current small,
 * quality-focused model in `.litertlm` format, served by LiteRT-LM
 * (the recommended successor to MediaPipe LLM Inference).
 *
 * To switch models, change [DEFAULT_MODEL] to point at another
 * `.litertlm` file. The downloader is generic and will fetch any URL.
 */
object LlmModelCatalog {

    /**
     * Default model used by the recipe app — **Gemma 4 E2B IT** in
     * `.litertlm` format.
     *
     * For modern Snapdragon 8 Elite devices, swap to
     * `gemma-4-E2B-it_qualcomm_sm8750.litertlm` for ~6× faster inference
     * via NPU. For Google Tensor G5 devices (Pixel 9+), use
     * `gemma-4-E2B-it_Google_Tensor_G5.litertlm`. The generic
     * `gemma-4-E2B-it.litertlm` works cross-platform via CPU.
     */
    val DEFAULT_MODEL: LlmModelDescriptor = LlmModelDescriptor(
        id = "gemma-4-E2B-it",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_580L * 1024 * 1024, // ~2.58 GB
        filename = "gemma-4-E2B-it.litertlm",
    )

    val ALL: List<LlmModelDescriptor> = listOf(DEFAULT_MODEL)

    fun findById(id: String): LlmModelDescriptor? = ALL.firstOrNull { it.id == id }
}
