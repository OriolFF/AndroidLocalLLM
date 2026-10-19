package com.llmlocal.medical.medical.domain

import com.llmlocal.core.domain.task.PromptBuilder

/**
 * Builds the system + user prompts that drive the medical-advice flow.
 *
 * The prompt is intentionally conservative:
 *
 *  - It frames the model as a *medical-information* assistant, not a
 *    doctor. We never want confident diagnostic language.
 *  - It forces a strict output format with five sections, so a small
 *    on-device model can produce something the parser can extract
 *    reliably (and so the UI can render it consistently).
 *  - It explicitly tells the model to surface red-flag symptoms in
 *    the `When to Seek Care` block — that's the highest-value output
 *    of this kind of tool.
 *  - It always ends with a one-line disclaimer reminding the user that
 *    the output is informational only.
 *
 * The format mirrors what [com.llmlocal.medical.medical.domain.MedicalParser]
 * knows how to extract. If you change the format, update the parser.
 */
class MedicalPromptBuilder : PromptBuilder {

    override fun systemPrompt(): String = """
        You are a careful medical information assistant. When given a list of
        symptoms a person is experiencing, you provide general wellness
        suggestions and indicate when they should consult a healthcare
        professional. You are NOT a doctor and your output is for
        informational purposes only — never a diagnosis.

        You respond in the following strict format and nothing else:

        Condition: <most likely general category, written conservatively>

        Likely Causes:
        - <cause 1>
        - <cause 2>

        Self-Care:
        - <suggestion 1>
        - <suggestion 2>

        When to Seek Care:
        <clear guidance naming the red-flag symptoms that warrant
        immediate professional care; be specific about severity
        thresholds and time frames>

        Disclaimer:
        <one short line reminding the user that this is general
        information, not a substitute for a qualified clinician>
    """.trimIndent()

    override fun userPrompt(inputs: List<String>): String {
        val list = inputs.joinToString(separator = "\n") { "- $it" }
        return """
            I'm experiencing the following symptoms. Based on these alone,
            what general category might they fall into, what could be
            causing them, what self-care can I try at home, and when
            should I seek professional care?

            Symptoms:
            $list
        """.trimIndent()
    }
}