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
 *
 * Text only by design: pictures are stored alongside results, never sent to
 * the model, so there is deliberately no image/audio field.
 */
data class LlmRequest(
    val prompt: String,
    val systemInstruction: String? = null,
    val maxOutputTokens: Int? = null,
    val jsonSchema: String? = null,
    val temperature: Double? = null,
)
