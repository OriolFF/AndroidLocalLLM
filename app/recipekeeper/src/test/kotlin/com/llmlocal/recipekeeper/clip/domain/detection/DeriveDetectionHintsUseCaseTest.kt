package com.llmlocal.recipekeeper.clip.domain.detection

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.CandidatePayload
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.recipekeeper.eval.EvalFixtures
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.testing.section
import com.llmlocal.recipekeeper.testing.snapshot
import org.json.JSONObject
import org.junit.Test

class DeriveDetectionHintsUseCaseTest {

    private val derive = DeriveDetectionHintsUseCase(
        knownUnits = setOf("g", "ml", "cup", "cups", "tsp", "cucharada", "cucharadas"),
        knownHeadings = setOf("ingredients", "instructions", "ingredientes"),
        clock = { 7L },
    )

    private fun page(name: String): PageSnapshot {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream("y1/pages/$name.json")).bufferedReader().use { it.readText() }
        return EvalFixtures.fromCapture(JSONObject(text).getJSONObject("capture"), capturedAt = 1L)
    }

    private fun candidate(payload: CandidatePayload = CandidatePayload.WholePage) = ClipCandidate(
        id = "c", title = "t", thumbnailUrl = null, snippet = null, source = CandidateSource.TEXT,
        ingredientCount = null, totalMinutes = null, payload = payload,
    )

    private fun recipe(ingredients: List<String>, steps: List<String>) = ClippedRecipe(
        id = "", title = "T", description = null, servings = null, prepMinutes = null, cookMinutes = null, totalMinutes = null,
        ingredients = ingredients, steps = steps, notes = null, sourceUrl = "", sourceHost = "", pictures = emptyList(),
        truncated = false, clippedAt = 0L,
    )

    private val greek = recipe(
        ingredients = listOf("αλεύρι 300 γρ.", "βούτυρο 200 γρ.", "ζάχαρη άχνη 100 γρ.", "βανίλια 2 κ.γ.", "αλάτι 1 πρέζα", "λάδι 2 κ.σ."),
        steps = listOf(
            "Χτυπάμε το βούτυρο με τη ζάχαρη μέχρι να αφρατέψει.",
            "Προσθέτουμε το αλεύρι και ζυμώνουμε μέχρι να σχηματιστεί μια μαλακή ζύμη.",
        ),
    )

    @Test
    fun `greek page teaches units, headings, level and number-after shape`() {
        val hints = derive(page("el-rolehead"), greek, candidate())

        assertThat(hints.filter { it.kind == HintKind.UNIT }.map { it.token }).containsExactly("γρ.")
        assertThat(hints.single { it.kind == HintKind.UNIT }.host).isNull()
        assertThat(hints.single { it.kind == HintKind.INGREDIENT_HEADING }.token).isEqualTo("υλικά")
        assertThat(hints.single { it.kind == HintKind.INSTRUCTION_HEADING }.token).isEqualTo("εκτέλεση")
        assertThat(hints.single { it.kind == HintKind.INGREDIENT_HEADING }.host).isNull()
        val level = hints.single { it.kind == HintKind.SECTION_LEVEL }
        assertThat(level.token).isEqualTo("4")
        assertThat(level.host).isEqualTo("spitikessyntages.example")
        val shape = hints.single { it.kind == HintKind.QUANTITY_SHAPE }
        assertThat(shape.token).isEqualTo(QuantityShape.NUMBER_AFTER.name)
        assertThat(shape.host).isEqualTo("spitikessyntages.example")
        assertThat(hints.map { it.confidence }.toSet()).containsExactly(25)
        assertThat(hints.map { it.lastSeenAt }.toSet()).containsExactly(7L)
    }

    @Test
    fun `a unit seen on a single line is noise`() {
        val recipe = recipe(listOf("200 zorg butter", "2 cups flour", "1 egg"), listOf("Mix.", "Bake."))
        val hints = derive(snapshot(sections = emptyList()), recipe, candidate())
        assertThat(hints.filter { it.kind == HintKind.UNIT }).isEmpty()
    }

    @Test
    fun `bare nouns are not units and known units are not re-learned`() {
        val recipe = recipe(listOf("2 cups flour", "3 eggs", "4 eggs", "1 tsp salt", "200 g butter"), listOf("Mix.", "Bake."))
        assertThat(derive(snapshot(), recipe, candidate()).filter { it.kind == HintKind.UNIT }).isEmpty()
    }

    @Test
    fun `leading number lines teach NUMBER_FIRST and a number-after majority wins over minorities`() {
        val first = recipe(listOf("2 cups flour", "1 tsp salt", "3 eggs", "harina 5"), listOf("Mix.", "Bake."))
        assertThat(derive(snapshot(), first, candidate()).single { it.kind == HintKind.QUANTITY_SHAPE }.token)
            .isEqualTo(QuantityShape.NUMBER_FIRST.name)
        val noUnits = recipe(listOf("harina", "sal", "huevos", "azúcar"), listOf("Mezclar.", "Hornear."))
        assertThat(derive(snapshot(), noUnits, candidate()).single { it.kind == HintKind.QUANTITY_SHAPE }.token)
            .isEqualTo(QuantityShape.NO_UNITS.name)
        val mixed = recipe(listOf("2 cups flour", "harina 5", "sal", "azúcar", "huevos"), listOf("Mezclar.", "Hornear."))
        assertThat(derive(snapshot(), mixed, candidate()).filter { it.kind == HintKind.QUANTITY_SHAPE }.map { it.token })
            .containsExactly(QuantityShape.NO_UNITS.name)
    }

    @Test
    fun `spanish number-after page teaches NUMBER_AFTER`() {
        val recipe = recipe(
            listOf("harina 250 g", "yogur 1 vasito", "azúcar 200 g", "aceite 120 ml"),
            listOf("Mezclar todo.", "Hornear 40 minutos."),
        )
        val hints = derive(snapshot(url = "https://www.cocina.example/receta"), recipe, candidate())
        assertThat(hints.single { it.kind == HintKind.QUANTITY_SHAPE }.token).isEqualTo(QuantityShape.NUMBER_AFTER.name)
        assertThat(hints.single { it.kind == HintKind.QUANTITY_SHAPE }.host).isEqualTo("cocina.example")
    }

    @Test
    fun `known headings are not re-learned and multi-word headings are host-specific`() {
        val sections = listOf(
            section("Ingredients", 2, "2 cups flour\n1 tsp salt\n3 eggs", top = 0),
            section("What you need to do", 2, "Mix the flour.\nBake the cake.", top = 100),
        )
        val recipe = recipe(listOf("2 cups flour", "1 tsp salt", "3 eggs"), listOf("Mix the flour.", "Bake the cake."))
        val hints = derive(snapshot(sections = sections), recipe, candidate())
        assertThat(hints.filter { it.kind == HintKind.INGREDIENT_HEADING }).isEmpty()
        val steps = hints.single { it.kind == HintKind.INSTRUCTION_HEADING }
        assertThat(steps.token).isEqualTo("what you need to do")
        assertThat(steps.host).isEqualTo("example.com")
    }

    @Test
    fun `nothing is learned below the quality floor or from structured candidates`() {
        val page = page("el-rolehead")
        assertThat(derive(page, greek.copy(ingredients = greek.ingredients.take(2)), candidate())).isEmpty()
        assertThat(derive(page, greek.copy(steps = greek.steps.take(1)), candidate())).isEmpty()
        val structured = candidate(CandidatePayload.Structured(com.llmlocal.core.web.JsonLdNode(listOf("Recipe"), JSONObject())))
        assertThat(derive(page, greek, structured)).isEmpty()
    }

    @Test
    fun `text candidates learn too`() {
        val text = candidate(CandidatePayload.Text(section("Υλικά", 4, "x", top = 0)))
        assertThat(derive(page("el-rolehead"), greek, text)).isNotEmpty()
    }
}
