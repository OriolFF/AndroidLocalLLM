package com.llmlocal.medical.medical.domain

/**
 * A structured medical-information response parsed from the LLM's output.
 *
 * This is **general wellness information, not a diagnosis**. The model
 * is prompted to be conservative and to always escalate red-flag symptoms
 * to a qualified clinician — see [MedicalPromptBuilder] for the exact
 * instruction. The disclaimer is carried through the parser so the UI
 * can render it alongside the structured advice.
 *
 * @property condition a short label naming the most likely general category
 *   the symptoms fall into (e.g. "Mild seasonal allergies"). Not a diagnosis.
 * @property likelyCauses bulleted factors the LLM considered when forming
 *   the suggestion (e.g. "Pollen exposure", "Dry air").
 * @property selfCare bulleted lifestyle suggestions the user can try at
 *   home. These are not prescriptions; they're general wellness habits.
 * @property whenToSeekCare a paragraph that names the red-flag symptoms
 *   that warrant urgent professional attention. Always present.
 * @property disclaimer a short reminder that the output is informational
 *   and not a substitute for a qualified clinician.
 */
data class MedicalAdvice(
    val condition: String,
    val likelyCauses: List<String>,
    val selfCare: List<String>,
    val whenToSeekCare: String,
    val disclaimer: String,
)