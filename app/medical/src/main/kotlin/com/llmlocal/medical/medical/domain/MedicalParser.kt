package com.llmlocal.medical.medical.domain

import com.llmlocal.core.domain.task.TaskParser
import com.llmlocal.medical.medical.domain.MedicalAdvice

/**
 * Parses the streamed model output into a structured [MedicalAdvice].
 *
 * Streaming-friendly: called repeatedly with the cumulative text so the
 * UI can render a partial result. Returns `null` when the cumulative
 * text doesn't yet contain the minimum sections to be useful.
 *
 * Expected format (see [com.llmlocal.medical.medical.domain.MedicalPromptBuilder]):
 *
 * ```
 * Condition: <general category>
 *
 * Likely Causes:
 * - a
 * - b
 *
 * Self-Care:
 * - x
 * - y
 *
 * When to Seek Care:
 * <paragraph>
 *
 * Disclaimer:
 * <one short line>
 * ```
 *
 * Lenient: if a section is missing we fall back to the raw text so the
 * UI can show *something*, but the parser only emits a non-null result
 * when at least the condition + one of (likelyCauses, selfCare) are
 * present. The `whenToSeekCare` section is intentionally treated as
 * required — without it, the user has no safety net.
 */
class MedicalParser : TaskParser<MedicalAdvice> {

    override fun parse(text: String): MedicalAdvice? {
        if (text.isBlank()) return null

        val condition = extractLine(text, "Condition")
        val causes = extractListSection(text, "Likely Causes")
        val selfCare = extractListSection(text, "Self-Care")
        val whenToSeek = extractParagraph(text, "When to Seek Care")
        val disclaimer = extractParagraph(text, "Disclaimer")

        // Need at least a condition and either causes or self-care to
        // consider it a useful response. The whenToSeek + disclaimer
        // may not have arrived yet mid-stream — that's fine.
        if (condition == null && causes.isEmpty() && selfCare.isEmpty()) {
            return null
        }
        if (whenToSeek == null) {
            // Hard requirement: never return advice without a
            // "when to seek care" block, even partial.
            return null
        }

        return MedicalAdvice(
            condition = condition ?: "General information",
            likelyCauses = causes,
            selfCare = selfCare,
            whenToSeekCare = whenToSeek,
            disclaimer = disclaimer ?: DISCLAIMER_FALLBACK,
        )
    }

    private fun extractLine(text: String, header: String): String? {
        // Match "Header: ..." up to end of line. The first match wins.
        val regex = Regex("(?im)^\\s*${Regex.escape(header)}\\s*:\\s*(.+)$")
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractListSection(text: String, header: String): List<String> {
        // Find the section by header (case-insensitive), then collect lines
        // starting with "-" or "*" until the next header or end of text.
        val pattern = Regex(
            "(?ims)^\\s*${Regex.escape(header)}\\s*:\\s*\\n(.*?)(?=^\\s*(?:Condition|Likely Causes|Self-Care|When to Seek Care|Disclaimer)\\s*:|\\z)"
        )
        val match = pattern.find(text) ?: return emptyList()
        val body = match.groupValues[1]
        return body.lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                    trimmed.removePrefix("-").removePrefix("*").trim().takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
    }

    private fun extractParagraph(text: String, header: String): String? {
        // Paragraphs are everything between the header line and the next
        // known header (or end of text).
        val pattern = Regex(
            "(?ims)^\\s*${Regex.escape(header)}\\s*:\\s*\\n(.*?)(?=^\\s*(?:Condition|Likely Causes|Self-Care|When to Seek Care|Disclaimer)\\s*:|\\z)"
        )
        val match = pattern.find(text) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
    }

    private companion object {
        // Used only if the LLM produced everything else but truncated the
        // Disclaimer section. Better to ship *some* disclaimer than none.
        const val DISCLAIMER_FALLBACK =
            "This is general information, not a substitute for a qualified clinician."
    }
}