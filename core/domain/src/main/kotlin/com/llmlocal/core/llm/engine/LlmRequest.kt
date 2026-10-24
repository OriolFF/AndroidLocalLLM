package com.llmlocal.core.llm.engine

/**
 * Everything a single generation call needs beyond the raw prompt.
 *
 * `LlmRequest(prompt)` with all defaults is exactly what the legacy
 * [LlmEngine.generateStream] String overload sends, so existing callers
 * keep their behaviour. The optional fields map 1:1 onto LiteRT-LM
 * conversation settings:
 *
 *  - [systemInstruction] — a separate system turn. When `null` the prompt
 *    is sent as a single user turn (the legacy behaviour, where callers
 *    concatenate system + user text themselves).
 *  - [maxOutputTokens] — hard cap on generated tokens for this call. `null`
 *    lets the engine run until EOS or the context window is exhausted.
 *  - [jsonSchema] — a JSON Schema string. When set, decoding is constrained
 *    so the output is valid JSON matching the schema; when `null` the
 *    model emits free text.
 *  - [temperature] — overrides the engine's default sampling temperature
 *    for this call only. `null` keeps the engine default.
 *  - [images] — pictures the model should read, in reading order, sent
 *    before the prompt text. Empty (the default) keeps the request text only.
 *    An engine without vision answers with [UnsupportedImagesException].
 *  - [visualTokenBudget] — tokens the vision encoder may spend per image;
 *    `null` keeps the runtime default.
 */
data class LlmRequest(
    val prompt: String,
    val systemInstruction: String? = null,
    val maxOutputTokens: Int? = null,
    val jsonSchema: String? = null,
    val temperature: Double? = null,
    val images: List<LlmImage> = emptyList(),
    val visualTokenBudget: Int? = null,
)

/** One picture for the model. Not a data class: [ByteArray] equality is identity and that is fine here. */
class LlmImage(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
)

/** What a loaded engine can take; meaningful after `initialize()`. */
data class LlmCapabilities(
    val vision: Boolean = false,
    val audio: Boolean = false,
    val maxImages: Int = 0,
)

/** Carried by [LlmToken.Error] when [LlmRequest.images] is non-empty and the engine has no vision. */
class UnsupportedImagesException(message: String) : IllegalStateException(message)
