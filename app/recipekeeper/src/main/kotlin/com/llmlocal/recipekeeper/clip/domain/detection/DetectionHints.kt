package com.llmlocal.recipekeeper.clip.domain.detection

import kotlinx.coroutines.flow.Flow

/** What a learned hint describes. */
enum class HintKind { UNIT, INGREDIENT_HEADING, INSTRUCTION_HEADING, QUANTITY_SHAPE, SECTION_LEVEL }

/** How the quantity sits in an ingredient line of a site. */
enum class QuantityShape { NUMBER_FIRST, NUMBER_AFTER, NUMBER_WORDS, NO_UNITS }

/**
 * One thing the clipper learned about how recipes look.
 *
 * @property host `null` = generic (any site); otherwise the host as
 *   `TextCleaner.host(url)` returns it (no `www.`)
 * @property token lower-cased unit/word, [QuantityShape] name, or heading level ("4")
 * @property confidence 0..100; a hint below the floor is dropped
 */
data class DetectionHint(
    val host: String?,
    val kind: HintKind,
    val token: String,
    val confidence: Int,
    val successes: Int,
    val failures: Int,
    val lastSeenAt: Long,
)

/** The hints that apply to one page: generic ones plus the ones for its host. */
data class DetectionHints(val generic: List<DetectionHint>, val forHost: List<DetectionHint>) {
    private fun tokens(kind: HintKind): Set<String> =
        (generic.asSequence() + forHost.asSequence()).filter { it.kind == kind }.map { it.token }.toSet()

    fun units(): Set<String> = tokens(HintKind.UNIT)
    fun ingredientHeadings(): Set<String> = tokens(HintKind.INGREDIENT_HEADING)
    fun instructionHeadings(): Set<String> = tokens(HintKind.INSTRUCTION_HEADING)
    fun quantityShapes(): Set<QuantityShape> =
        tokens(HintKind.QUANTITY_SHAPE).mapNotNull { name -> QuantityShape.entries.firstOrNull { it.name == name } }.toSet()
    fun sectionLevels(): Set<Int> = tokens(HintKind.SECTION_LEVEL).mapNotNull { it.toIntOrNull() }.toSet()

    fun isEmpty(): Boolean = generic.isEmpty() && forHost.isEmpty()

    companion object {
        val EMPTY = DetectionHints(emptyList(), emptyList())
    }
}

/** Read side, used by the detector. Generic hints at confidence ≥ 40 plus every row of [host]. */
interface DetectionHintsSource {
    suspend fun hintsFor(host: String?): DetectionHints

    companion object {
        /** Source with no learned hints: the detector behaves exactly like the built-in lists. */
        val NONE: DetectionHintsSource = object : DetectionHintsSource {
            override suspend fun hintsFor(host: String?): DetectionHints = DetectionHints.EMPTY
        }
    }
}

/** Write side, used by learning, penalties and the reset action. */
interface DetectionHintsStore {
    /** Upsert; `successes + 1`, `confidence = min(100, confidence + 25)`. */
    suspend fun reinforce(hints: List<DetectionHint>)

    /** `failures + 1`, `confidence − 40` on the matching rows; rows below 20 are deleted. */
    suspend fun penalise(host: String?, kinds: Set<HintKind>, tokens: Set<String>)

    suspend fun clear()

    /** Number of stored hints (drives "12 learned hints"). */
    fun count(): Flow<Int>
}

/** Store that learns nothing; bound until the Room-backed store is available. */
object NoOpDetectionHintsStore : DetectionHintsStore, DetectionHintsSource {
    override suspend fun hintsFor(host: String?): DetectionHints = DetectionHints.EMPTY
    override suspend fun reinforce(hints: List<DetectionHint>) = Unit
    override suspend fun penalise(host: String?, kinds: Set<HintKind>, tokens: Set<String>) = Unit
    override suspend fun clear() = Unit
    override fun count(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(0)
}
