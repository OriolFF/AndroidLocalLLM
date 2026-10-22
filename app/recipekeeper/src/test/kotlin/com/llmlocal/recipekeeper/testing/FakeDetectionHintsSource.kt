package com.llmlocal.recipekeeper.testing

import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHint
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHints
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHintsSource
import com.llmlocal.recipekeeper.clip.domain.detection.HintKind
import com.llmlocal.recipekeeper.clip.domain.detection.QuantityShape

/** Returns whatever [hints] holds, whatever the host; records the hosts asked for. */
class FakeDetectionHintsSource(var hints: DetectionHints = DetectionHints.EMPTY) : DetectionHintsSource {
    val requestedHosts = mutableListOf<String?>()

    override suspend fun hintsFor(host: String?): DetectionHints {
        requestedHosts += host
        return hints
    }
}

fun hint(kind: HintKind, token: String, host: String? = null, confidence: Int = 75) =
    DetectionHint(host = host, kind = kind, token = token, confidence = confidence, successes = 1, failures = 0, lastSeenAt = 1L)

fun hintsOf(vararg hints: DetectionHint) = DetectionHints(
    generic = hints.filter { it.host == null },
    forHost = hints.filter { it.host != null },
)

fun shapeHint(shape: QuantityShape, host: String = "example.com") = hint(HintKind.QUANTITY_SHAPE, shape.name, host)

/** Store whose only observable state is a hint count; [clear] zeroes it. */
class FakeDetectionHintsStore(initialCount: Int = 0) : com.llmlocal.recipekeeper.clip.domain.detection.DetectionHintsStore {
    private val count = kotlinx.coroutines.flow.MutableStateFlow(initialCount)
    var clearCalls = 0

    fun setCount(value: Int) { count.value = value }

    override suspend fun reinforce(hints: List<DetectionHint>) { count.value += hints.size }
    override suspend fun penalise(host: String?, kinds: Set<HintKind>, tokens: Set<String>) = Unit
    override suspend fun clear() { clearCalls++; count.value = 0 }
    override fun count(): kotlinx.coroutines.flow.Flow<Int> = count
}
