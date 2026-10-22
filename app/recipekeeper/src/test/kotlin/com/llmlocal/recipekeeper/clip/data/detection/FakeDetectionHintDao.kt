package com.llmlocal.recipekeeper.clip.data.detection

import com.llmlocal.recipekeeper.recipes.data.DetectionHintDao
import com.llmlocal.recipekeeper.recipes.data.DetectionHintEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [DetectionHintDao] mirroring the SQL (`host = ''` is generic). */
class FakeDetectionHintDao : DetectionHintDao {
    private val rows = MutableStateFlow(emptyList<DetectionHintEntity>())
    val all: List<DetectionHintEntity> get() = rows.value

    override suspend fun genericAtLeast(minConfidence: Int) = rows.value.filter { it.host == "" && it.confidence >= minConfidence }
    override suspend fun forHost(host: String) = rows.value.filter { it.host == host }
    override suspend fun find(host: String, kind: String, token: String) =
        rows.value.firstOrNull { it.host == host && it.kind == kind && it.token == token }

    override suspend fun upsert(hints: List<DetectionHintEntity>) {
        val keys = hints.map { Triple(it.host, it.kind, it.token) }.toSet()
        rows.value = rows.value.filterNot { Triple(it.host, it.kind, it.token) in keys } + hints
    }

    override suspend fun delete(host: String, kind: String, token: String) {
        rows.value = rows.value.filterNot { it.host == host && it.kind == kind && it.token == token }
    }

    override suspend fun trimKind(kind: String, keep: Int) {
        val keepRows = rows.value.filter { it.kind == kind }.sortedByDescending { it.lastSeenAt }.take(keep).toSet()
        rows.value = rows.value.filter { it.kind != kind || it in keepRows }
    }

    override suspend fun deleteAll() { rows.value = emptyList() }
    override fun observeCount(): Flow<Int> = rows.map { it.size }
}
