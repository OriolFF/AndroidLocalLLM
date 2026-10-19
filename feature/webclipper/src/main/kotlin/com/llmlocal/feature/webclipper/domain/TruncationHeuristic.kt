package com.llmlocal.feature.webclipper.domain

/**
 * Guess whether model output was cut off at the token cap.
 *
 * The engine does not report "stopped because of `maxOutputTokens`", so
 * this combines two cheap signals: the output is close to the cap
 * (≈ [CHARS_PER_TOKEN] characters per token) **and** its last line does
 * not end like a finished sentence or list item. Either alone is noisy —
 * short recipes end mid-line all the time, and long ones end cleanly —
 * but together they catch the common "Steps: 1… 2… 3. Add the" case.
 */
object TruncationHeuristic {
    const val CHARS_PER_TOKEN = 4
    const val NEAR_CAP_RATIO = 0.8

    private val TERMINAL = setOf('.', '!', '?', ')', '"', '”', '\'', ']', '}')

    fun looksTruncated(text: String, maxOutputTokens: Int?): Boolean {
        if (maxOutputTokens == null || maxOutputTokens <= 0) return false
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return false
        val estimatedTokens = trimmed.length / CHARS_PER_TOKEN
        val nearCap = estimatedTokens >= maxOutputTokens * NEAR_CAP_RATIO
        if (!nearCap) return false
        val lastLine = trimmed.lines().last().trim()
        val endsCleanly = lastLine.lastOrNull() in TERMINAL || lastLine.endsWith(":")
        return !endsCleanly
    }
}
