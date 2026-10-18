package com.llmlocal.core.web

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class JsonLdExtractorTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream("jsonld/$name.json")) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    private fun extract(vararg names: String): List<JsonLdNode> =
        JsonLdExtractor.fromScriptBlocks(names.map(::fixture))

    private fun List<JsonLdNode>.recipes() = filter { "Recipe" in it.types }

    private fun List<JsonLdNode>.names() = map { it.raw.optString("name") }

    @Test
    fun `single recipe object`() {
        val nodes = extract("single_recipe")
        assertThat(nodes).hasSize(1)
        assertThat(nodes[0].types).containsExactly("Recipe")
        assertThat(nodes[0].raw.getString("name")).isEqualTo("Simple Sourdough Pancakes")
        assertThat(nodes[0].raw.getJSONArray("recipeIngredient").length()).isEqualTo(6)
        assertThat(nodes[0].raw.getString("totalTime")).isEqualTo("PT25M")
    }

    @Test
    fun `graph is expanded and mainEntity inside it is followed`() {
        val nodes = extract("graph_recipe")
        assertThat(nodes.map { it.types.single() }).containsExactly("Recipe", "WebPage", "Recipe")
        assertThat(nodes.recipes().names()).containsExactly("Bread", "No-Knead Crusty Bread")
    }

    @Test
    fun `top-level array yields one node per recipe`() {
        val nodes = extract("array_of_recipes")
        assertThat(nodes.recipes().names()).containsExactly("Roundup Recipe One", "Roundup Recipe Two").inOrder()
    }

    @Test
    fun `type array is kept as a list`() {
        val nodes = extract("type_array_recipe")
        assertThat(nodes).hasSize(1)
        assertThat(nodes[0].types).containsExactly("Recipe", "Thing").inOrder()
    }

    @Test
    fun `mainEntity of a WebPage is followed and the page is kept too`() {
        val nodes = extract("main_entity_recipe")
        assertThat(nodes.map { it.types.single() }).containsExactly("Recipe", "WebPage")
        assertThat(nodes.recipes().single().raw.getString("name")).isEqualTo("Referenced Recipe")
    }

    @Test
    fun `HowToSection instructions stay nested in the raw node`() {
        val nodes = extract("howto_section_recipe")
        assertThat(nodes).hasSize(1) // sections and steps are not flattened into nodes
        val instructions = nodes[0].raw.getJSONArray("recipeInstructions")
        assertThat(instructions.length()).isEqualTo(2)
        val sauce = instructions.getJSONObject(0)
        assertThat(sauce.getString("@type")).isEqualTo("HowToSection")
        assertThat(sauce.getString("name")).isEqualTo("Sauce")
        val steps = sauce.getJSONArray("itemListElement")
        assertThat(steps.getJSONObject(1).getString("text")).isEqualTo("Season with salt and basil.")
    }

    @Test
    fun `ImageObject images are reachable from the raw node`() {
        val image = extract("image_object_recipe").single().raw.getJSONArray("image")
        assertThat(image.length()).isEqualTo(3)
        assertThat(image.getJSONObject(0).getString("url")).isEqualTo("https://example.com/a.jpg")
        assertThat(image.getString(1)).isEqualTo("https://example.com/b.jpg")
        assertThat(image.getJSONObject(2).getString("contentUrl")).isEqualTo("https://example.com/c.jpg")
    }

    @Test
    fun `yield array with QuantitativeValue is preserved`() {
        val yield = extract("yield_array_recipe").single().raw.get("recipeYield")
        assertThat(yield).isInstanceOf(JSONArray::class.java)
        yield as JSONArray
        assertThat(yield.getString(0)).isEqualTo("4 servings")
        assertThat(yield.getJSONObject(1).getInt("value")).isEqualTo(8)
    }

    @Test
    fun `all fixtures together produce one node per recipe`() {
        val nodes = extract(
            "single_recipe", "graph_recipe", "array_of_recipes", "type_array_recipe",
            "main_entity_recipe", "howto_section_recipe", "image_object_recipe", "yield_array_recipe",
        )
        assertThat(nodes.recipes()).hasSize(10)
    }

    @Test
    fun `html comment, cdata and bom wrappers are stripped`() {
        val body = """{"@type":"Recipe","name":"Wrapped"}"""
        val nodes = JsonLdExtractor.fromScriptBlocks(
            listOf(
                "\n  <!--\n  $body\n  -->\n",
                "<![CDATA[$body]]>",
                "﻿$body",
            ),
        )
        assertThat(nodes).hasSize(3)
        assertThat(nodes.names()).containsExactly("Wrapped", "Wrapped", "Wrapped")
    }

    @Test
    fun `malformed, empty and non-object blocks are dropped without losing the rest`() {
        val nodes = JsonLdExtractor.fromScriptBlocks(
            listOf(
                "{ not json at all",
                "",
                "   ",
                "42",
                "\"just a string\"",
                "[1, 2, 3]",
                """{"name":"no type"}""",
                """{"@type":"Recipe","name":"Survivor"}""",
            ),
        )
        assertThat(nodes.names()).containsExactly("Survivor")
    }

    @Test
    fun `schema org prefixes on types are normalised`() {
        val nodes = JsonLdExtractor.fromScriptBlocks(
            listOf(
                """{"@type":"https://schema.org/Recipe","name":"a"}""",
                """{"@type":"http://schema.org/Recipe","name":"b"}""",
                """{"@type":"schema:Recipe","name":"c"}""",
                """{"@type":["Recipe", 7, null, " Thing "],"name":"d"}""",
            ),
        )
        assertThat(nodes.map { it.types }).containsExactly(
            listOf("Recipe"), listOf("Recipe"), listOf("Recipe"), listOf("Recipe", "Thing"),
        ).inOrder()
    }

    @Test
    fun `nested graphs and mainEntity arrays are walked`() {
        val nodes = JsonLdExtractor.fromScriptBlocks(
            listOf(
                """
                {"@graph":[
                  {"@type":"WebPage","mainEntity":[{"@type":"Recipe","name":"one"},{"@type":"Recipe","name":"two"}]},
                  {"@graph":[{"@type":"Recipe","name":"three"}]}
                ]}
                """.trimIndent(),
            ),
        )
        assertThat(nodes.recipes().names()).containsExactly("one", "two", "three").inOrder()
    }

    @Test
    fun `raw is the same object the extractor parsed`() {
        val node = JsonLdExtractor.fromScriptBlocks(listOf("""{"@type":"Recipe","x":{"y":1}}""")).single()
        assertThat(node.raw).isInstanceOf(JSONObject::class.java)
        assertThat(node.raw.getJSONObject("x").getInt("y")).isEqualTo(1)
    }
}
