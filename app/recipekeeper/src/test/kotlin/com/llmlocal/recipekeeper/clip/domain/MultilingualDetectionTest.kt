package com.llmlocal.recipekeeper.clip.domain

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.recipekeeper.eval.EvalFixtures
import com.llmlocal.recipekeeper.testing.section
import com.llmlocal.recipekeeper.testing.snapshot
import com.llmlocal.recipekeeper.testing.testDurationParser
import org.json.JSONObject
import org.junit.Test

/**
 * Y1: Spanish / Catalan / Greek pages without structured data. The JSON
 * fixtures are `capture.js` output (jsdom harness) for
 * `core/web/tools/capture-harness/fixture-{es-blog-h4,ca-boldheads,el-rolehead}.html`.
 */
class MultilingualDetectionTest {

    private val config = RecipeKeeperClipConfig(untitledTitle = "Untitled recipe", durationParser = testDurationParser)

    private fun page(name: String): PageSnapshot {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream("y1/pages/$name.json")).bufferedReader().use { it.readText() }
        return EvalFixtures.fromCapture(JSONObject(text).getJSONObject("capture"), capturedAt = 1L)
    }

    private fun assertOneTextCandidate(name: String, title: String) {
        val candidates = config.detect(page(name))
        assertThat(candidates).hasSize(1)
        assertThat(candidates.single().source).isEqualTo(CandidateSource.TEXT)
        assertThat(candidates.single().title).isEqualTo(title)
    }

    @Test
    fun `spanish blog with h4 headings and quantities after the name`() = assertOneTextCandidate("es-blog-h4", "Bizcocho de yogur casero")

    @Test
    fun `catalan page with bold-only headings, cullerades and pessic`() = assertOneTextCandidate("ca-boldheads", "Coca de llardons")

    @Test
    fun `greek page with role=heading headings, k s and gr`() = assertOneTextCandidate("el-rolehead", "Μπισκότα βουτύρου")

    @Test
    fun `real spanish news-site recipe page (cronica) yields one text candidate`() =
        assertOneTextCandidate("es-cronica-real", "Esta receta utiliza el vasito de yogur como unidad de medida, garantizando un dulce perfecto y delicioso sin la necesidad de balanzas ni equipos especiales.")

    @Test
    fun `spanish category and index pages of two-line cards yield nothing`() {
        val cards = (1..8).map { n ->
            section("Receta $n", 3, "2 tazas de harina\n1 cucharada de aceite\nUna receta rápida número $n.", top = n * 100)
        }
        val sections = listOf(section("Recetas de cocina", 1, "Nuestras recetas favoritas.", top = 0)) + cards

        assertThat(config.detect(snapshot(url = "https://www.example.es/categoria/recetas-faciles/", sections = sections))).isEmpty()
        assertThat(config.detect(snapshot(url = "https://www.example.es/recetas/", sections = sections))).isEmpty()
        val single = listOf(section("Tarta", 1, "2 tazas de harina\n1 cucharada de aceite\nHornear.", top = 0))
        assertThat(config.detect(snapshot(url = "https://www.example.es/categoria/recetas/tartas", sections = single))).isEmpty()
        assertThat(config.detect(snapshot(url = "https://www.example.es/categories/receptes/pastissos", sections = single))).isEmpty()
        assertThat(config.detect(snapshot(url = "https://www.example.gr/κατηγορια/syntages/", sections = single))).isEmpty()
    }
}
