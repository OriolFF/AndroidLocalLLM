package com.llmlocal.recipekeeper.clip.domain.detection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/*
 * Learning-detection contracts (docs/plans/recipekeeper-learning-detection.md §1).
 *
 * STAND-IN: stream L1 owns this package and ships the real store and the
 * derive/learn/penalise use cases. This file carries only the contract types
 * L2 (detector integration + reset action) compiles against, with the exact
 * signatures of the plan; at merge keep L1's version of every file in this
 * package and drop this one if L1 declares the same types.
 */

enum class HintKind { UNIT, INGREDIENT_HEADING, INSTRUCTION_HEADING, QUANTITY_SHAPE, SECTION_LEVEL }

enum class QuantityShape { NUMBER_FIRST, NUMBER_AFTER, NUMBER_WORDS, NO_UNITS }

data class DetectionHint(
    /** `null` = generic (any site). */
    val host: String?,
    val kind: HintKind,
    /** Lower-cased unit/word, shape name, or heading level ("4"). */
    val token: String,
    /** 0..100. */
    val confidence: Int,
    val successes: Int,
    val failures: Int,
    val lastSeenAt: Long,
)

data class DetectionHints(val generic: List<DetectionHint>, val forHost: List<DetectionHint>) {
    private fun tokens(kind: HintKind): Set<String> =
        (generic + forHost).filter { it.kind == kind }.map { it.token }.toSet()

    fun units(): Set<String> = tokens(HintKind.UNIT)
    fun ingredientHeadings(): Set<String> = tokens(HintKind.INGREDIENT_HEADING)
    fun instructionHeadings(): Set<String> = tokens(HintKind.INSTRUCTION_HEADING)
    fun quantityShapes(): Set<QuantityShape> =
        tokens(HintKind.QUANTITY_SHAPE).mapNotNull { name -> QuantityShape.entries.firstOrNull { it.name == name } }.toSet()
    fun sectionLevels(): Set<Int> = tokens(HintKind.SECTION_LEVEL).mapNotNull { it.toIntOrNull() }.toSet()

    val isEmpty: Boolean get() = generic.isEmpty() && forHost.isEmpty()

    companion object {
        val EMPTY = DetectionHints(emptyList(), emptyList())
    }
}

/** Read side (detector). */
interface DetectionHintsSource {
    suspend fun hintsFor(host: String?): DetectionHints

    companion object {
        /** Until a store is wired: nothing learned. */
        val NONE: DetectionHintsSource = object : DetectionHintsSource {
            override suspend fun hintsFor(host: String?): DetectionHints = DetectionHints.EMPTY
        }
    }
}

/** Write side (learning). */
interface DetectionHintsStore {
    /** Upsert; successes + 1, confidence = min(100, confidence + 25). */
    suspend fun reinforce(hints: List<DetectionHint>)

    /** failures + 1, confidence − 40, delete when < 20. */
    suspend fun penalise(host: String?, kinds: Set<HintKind>, tokens: Set<String>)

    suspend fun clear()

    fun count(): Flow<Int>
}

/** [DetectionHintsStore] that remembers nothing; the fallback while no real store is bound. */
object NoOpDetectionHintsStore : DetectionHintsStore {
    override suspend fun reinforce(hints: List<DetectionHint>) = Unit
    override suspend fun penalise(host: String?, kinds: Set<HintKind>, tokens: Set<String>) = Unit
    override suspend fun clear() = Unit
    override fun count(): Flow<Int> = flowOf(0)
}

class ResetDetectionHintsUseCase(private val store: DetectionHintsStore) {
    suspend operator fun invoke() = store.clear()
}
