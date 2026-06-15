package com.llmlocal.core.llm.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecipeParserTest {

    private val parser = RecipeParser()

    @Test
    fun `parses a well-formed recipe`() {
        val text = """
            Title: Garlic Tomato Omelette

            Ingredients:
            - 3 eggs
            - 1 tomato
            - 1 small onion
            - salt
            - olive oil

            Steps:
            1. Crack the eggs into a bowl and beat.
            2. Dice the tomato and onion.
            3. Heat oil in a pan and sauté the onion until translucent.
            4. Add tomato and cook for 2 minutes.
            5. Pour in the eggs and cook until set.

            Notes:
            Serve with bread.
        """.trimIndent()

        val recipe = parser.parse(text)
        assertThat(recipe).isNotNull()
        assertThat(recipe!!.title).isEqualTo("Garlic Tomato Omelette")
        assertThat(recipe.ingredients).containsExactly(
            "3 eggs", "1 tomato", "1 small onion", "salt", "olive oil"
        )
        assertThat(recipe.steps).hasSize(5)
        assertThat(recipe.steps.first()).isEqualTo("Crack the eggs into a bowl and beat.")
        assertThat(recipe.notes).isEqualTo("Serve with bread.")
    }

    @Test
    fun `parses numbered steps with parenthesis style`() {
        val text = """
            Title: Simple Salad

            Ingredients:
            - lettuce
            - tomato

            Steps:
            1) Wash the lettuce
            2) Slice the tomato
            3) Toss together
        """.trimIndent()

        val recipe = parser.parse(text)!!
        assertThat(recipe.steps).containsExactly(
            "Wash the lettuce", "Slice the tomato", "Toss together"
        ).inOrder()
    }

    @Test
    fun `parses partial stream - title only returns a recipe with the title and empty body`() {
        // The parser is intentionally lenient: a partial stream with only
        // a title yields a Recipe with the title and empty ingredients /
        // steps. This gives the UI something to show as tokens stream in.
        val partial = "Title: Soup"
        val recipe = parser.parse(partial)
        assertThat(recipe).isNotNull()
        assertThat(recipe!!.title).isEqualTo("Soup")
        assertThat(recipe.ingredients).isEmpty()
        assertThat(recipe.steps).isEmpty()
    }

    @Test
    fun `returns null for empty or whitespace-only text`() {
        assertThat(parser.parse("")).isNull()
        assertThat(parser.parse("   \n  ")).isNull()
    }

    @Test
    fun `handles missing Notes section`() {
        val text = """
            Title: Toast

            Ingredients:
            - bread

            Steps:
            1. Toast the bread
        """.trimIndent()

        val recipe = parser.parse(text)!!
        assertThat(recipe.notes).isNull()
    }
}
