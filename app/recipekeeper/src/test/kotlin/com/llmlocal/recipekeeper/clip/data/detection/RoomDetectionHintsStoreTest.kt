package com.llmlocal.recipekeeper.clip.data.detection

import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHint
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHints
import com.llmlocal.recipekeeper.clip.domain.detection.HintKind
import com.llmlocal.recipekeeper.clip.domain.detection.PenaliseDetectionHintsUseCase
import com.llmlocal.recipekeeper.clip.domain.detection.ResetDetectionHintsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomDetectionHintsStoreTest {

    private val dao = FakeDetectionHintDao()
    private val store = RoomDetectionHintsStore(dao)

    private fun hint(token: String, host: String? = null, kind: HintKind = HintKind.UNIT, confidence: Int = 25, seen: Long = 1L) =
        DetectionHint(host, kind, token, confidence, successes = 0, failures = 0, lastSeenAt = seen)

    @Test
    fun `first success lifts a new hint to 50 so it is read at once, a repeat to 75`() = runTest {
        store.reinforce(listOf(hint("γρ.")))
        val first = store.hintsFor(null).generic.single()
        assertThat(first.confidence).isEqualTo(50)
        assertThat(first.successes).isEqualTo(1)

        store.reinforce(listOf(hint("γρ.", seen = 9L)))
        val second = store.hintsFor(null).generic.single()
        assertThat(second.confidence).isEqualTo(75)
        assertThat(second.successes).isEqualTo(2)
        assertThat(second.lastSeenAt).isEqualTo(9L)
        store.reinforce(listOf(hint("γρ.")))
        store.reinforce(listOf(hint("γρ.")))
        assertThat(store.hintsFor(null).generic.single().confidence).isEqualTo(100)
    }

    @Test
    fun `generic hints below 40 are not read, host hints come back for their host only`() = runTest {
        dao.upsert(listOf(com.llmlocal.recipekeeper.recipes.data.DetectionHintEntity("", "UNIT", "low", 30, 1, 0, 1L)))
        store.reinforce(listOf(hint("a.example", "a.example", HintKind.SECTION_LEVEL), hint("b.example", "b.example", HintKind.SECTION_LEVEL)))

        assertThat(store.hintsFor("a.example").generic).isEmpty()
        assertThat(store.hintsFor("a.example").forHost.map { it.host }).containsExactly("a.example")
        assertThat(store.hintsFor(null).forHost).isEmpty()
        assertThat(store.hintsFor("c.example")).isEqualTo(DetectionHints.EMPTY)
    }

    @Test
    fun `a penalty costs 40 and a hint falling below 20 is deleted`() = runTest {
        store.reinforce(listOf(hint("x"))) // 50
        store.reinforce(listOf(hint("x"))) // 75
        store.penalise(null, setOf(HintKind.UNIT), setOf("x")) // 35
        val after = dao.find("", "UNIT", "x")!!
        assertThat(after.confidence).isEqualTo(35)
        assertThat(after.failures).isEqualTo(1)
        store.penalise(null, setOf(HintKind.UNIT), setOf("x")) // -5 → deleted
        assertThat(dao.find("", "UNIT", "x")).isNull()
    }

    @Test
    fun `penalty use case reaches generic and host rows and spares unrelated ones`() = runTest {
        store.reinforce(listOf(hint("γρ."), hint("4", "a.example", HintKind.SECTION_LEVEL), hint("kept")))
        val used = store.hintsFor("a.example")
        PenaliseDetectionHintsUseCase(store)("a.example", DetectionHints(used.generic.filter { it.token == "γρ." }, used.forHost))

        assertThat(dao.find("", "UNIT", "γρ.")).isNull()
        assertThat(dao.find("a.example", "SECTION_LEVEL", "4")).isNull()
        assertThat(dao.find("", "UNIT", "kept")).isNotNull()
    }

    @Test
    fun `rows per kind are capped to the 200 most recently seen`() = runTest {
        store.reinforce((1..205).map { hint("u$it", seen = it.toLong()) })
        val units = dao.all.filter { it.kind == "UNIT" }
        assertThat(units).hasSize(200)
        assertThat(units.map { it.token }).doesNotContain("u1")
        assertThat(units.map { it.token }).contains("u205")
    }

    @Test
    fun `clear empties the store and count follows`() = runTest {
        store.reinforce(listOf(hint("a"), hint("b")))
        assertThat(store.count().first()).isEqualTo(2)
        ResetDetectionHintsUseCase(store)()
        assertThat(store.count().first()).isEqualTo(0)
        assertThat(store.hintsFor(null)).isEqualTo(DetectionHints.EMPTY)
    }

    @Test
    fun `accessors group tokens by kind`() {
        val hints = DetectionHints(
            generic = listOf(hint("γρ."), hint("υλικά", kind = HintKind.INGREDIENT_HEADING)),
            forHost = listOf(hint("4", "h", HintKind.SECTION_LEVEL), hint("NUMBER_AFTER", "h", HintKind.QUANTITY_SHAPE), hint("bogus", "h", HintKind.QUANTITY_SHAPE)),
        )
        assertThat(hints.units()).containsExactly("γρ.")
        assertThat(hints.ingredientHeadings()).containsExactly("υλικά")
        assertThat(hints.sectionLevels()).containsExactly(4)
        assertThat(hints.quantityShapes()).containsExactly(com.llmlocal.recipekeeper.clip.domain.detection.QuantityShape.NUMBER_AFTER)
    }
}
