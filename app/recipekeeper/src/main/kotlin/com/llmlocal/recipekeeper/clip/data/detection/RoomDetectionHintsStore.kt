package com.llmlocal.recipekeeper.clip.data.detection

import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHint
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHints
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHintsSource
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHintsStore
import com.llmlocal.recipekeeper.clip.domain.detection.HintKind
import com.llmlocal.recipekeeper.recipes.data.DetectionHintDao
import com.llmlocal.recipekeeper.recipes.data.DetectionHintEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room-backed detection hints. Writes are serialised by a [Mutex] (the
 * read-modify-write of [reinforce] / [penalise] must not interleave).
 * Rows per kind are capped at [MAX_ROWS_PER_KIND], oldest `lastSeenAt` first.
 */
class RoomDetectionHintsStore(private val dao: DetectionHintDao) : DetectionHintsSource, DetectionHintsStore {

    private val writes = Mutex()

    override suspend fun hintsFor(host: String?): DetectionHints = DetectionHints(
        generic = dao.genericAtLeast(MIN_GENERIC_CONFIDENCE).map { it.toHint() },
        forHost = host?.takeIf { it.isNotBlank() }?.let { dao.forHost(it).map { row -> row.toHint() } }.orEmpty(),
    )

    override suspend fun reinforce(hints: List<DetectionHint>) {
        if (hints.isEmpty()) return
        writes.withLock {
            val rows = hints.map { hint ->
                val existing = dao.find(hint.host.orEmpty(), hint.kind.name, hint.token)
                DetectionHintEntity(
                    host = hint.host.orEmpty(),
                    kind = hint.kind.name,
                    token = hint.token,
                    confidence = minOf(MAX_CONFIDENCE, (existing?.confidence ?: hint.confidence) + REINFORCE_STEP),
                    successes = (existing?.successes ?: hint.successes) + 1,
                    failures = existing?.failures ?: hint.failures,
                    lastSeenAt = hint.lastSeenAt,
                )
            }
            dao.upsert(rows)
            hints.map { it.kind }.toSet().forEach { dao.trimKind(it.name, MAX_ROWS_PER_KIND) }
        }
    }

    override suspend fun penalise(host: String?, kinds: Set<HintKind>, tokens: Set<String>) {
        if (kinds.isEmpty() || tokens.isEmpty()) return
        writes.withLock {
            val hostKey = host.orEmpty()
            for (kind in kinds) for (token in tokens) {
                val row = dao.find(hostKey, kind.name, token) ?: continue
                val confidence = row.confidence - PENALTY_STEP
                if (confidence < DROP_BELOW) {
                    dao.delete(hostKey, kind.name, token)
                } else {
                    dao.upsert(listOf(row.copy(confidence = confidence, failures = row.failures + 1)))
                }
            }
        }
    }

    override suspend fun clear() = writes.withLock { dao.deleteAll() }

    override fun count(): Flow<Int> = dao.observeCount()

    private fun DetectionHintEntity.toHint() = DetectionHint(
        host = host.ifEmpty { null },
        kind = HintKind.valueOf(kind),
        token = token,
        confidence = confidence,
        successes = successes,
        failures = failures,
        lastSeenAt = lastSeenAt,
    )

    companion object {
        const val MIN_GENERIC_CONFIDENCE = 40
        const val MAX_CONFIDENCE = 100
        const val REINFORCE_STEP = 25
        const val PENALTY_STEP = 40
        const val DROP_BELOW = 20
        const val MAX_ROWS_PER_KIND = 200
    }
}
