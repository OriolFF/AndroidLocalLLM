package com.llmlocal.recipekeeper.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecipeKeeperParserTest {

    private val parser = RecipeKeeperParser()

    @Test
    fun `complete output parses every field and is detached`() {
        val recipe = parser.parse(FULL_OUTPUT)!!

        assertThat(recipe.title).isEqualTo("Classic Banana Bread")
        assertThat(recipe.description).isEqualTo("Moist banana bread with a crackly sugar top.")
        assertThat(recipe.servings).isEqualTo("1 loaf (10 slices)")
        assertThat(recipe.prepMinutes).isEqualTo(15)
        assertThat(recipe.cookMinutes).isEqualTo(60)
        assertThat(recipe.totalMinutes).isEqualTo(75)
        assertThat(recipe.ingredients).containsExactly(
            "3 ripe bananas, mashed",
            "1/3 cup melted butter",
            "3/4 cup sugar",
            "1 egg, beaten",
            "1 1/2 cups all-purpose flour",
        ).inOrder()
        assertThat(recipe.steps).containsExactly(
            "Preheat the oven to 175°C and butter a loaf pan.",
            "Stir the butter into the bananas, then mix in the sugar, egg and vanilla.",
            "Add the flour and mix until just combined.",
            "Bake for 60 minutes.",
        ).inOrder()
        assertThat(recipe.notes).isEqualTo("Let it cool before slicing. Freezes well.")
        assertThat(recipe.truncated).isFalse()
        // Detached: the sink fills these in.
        assertThat(recipe.id).isEmpty()
        assertThat(recipe.sourceUrl).isEmpty()
        assertThat(recipe.sourceHost).isEmpty()
        assertThat(recipe.pictures).isEmpty()
    }

    @Test
    fun `missing optional fields are null and title blank when omitted`() {
        val recipe = parser.parse(
            """
            Ingredients:
            - 2 eggs
            - salt
            Steps:
            1. Beat the eggs.
            2. Cook them.
            """.trimIndent(),
        )!!

        assertThat(recipe.title).isEmpty()
        assertThat(recipe.description).isNull()
        assertThat(recipe.servings).isNull()
        assertThat(recipe.prepMinutes).isNull()
        assertThat(recipe.cookMinutes).isNull()
        assertThat(recipe.totalMinutes).isNull()
        assertThat(recipe.notes).isNull()
        assertThat(recipe.ingredients).containsExactly("2 eggs", "salt")
        assertThat(recipe.steps).hasSize(2)
    }

    @Test
    fun `only cook time known gives total equal to cook`() {
        val recipe = parser.parse("Title: X\nCookMinutes: 30\nIngredients:\n- a\n")!!
        assertThat(recipe.cookMinutes).isEqualTo(30)
        assertThat(recipe.totalMinutes).isEqualTo(30)
    }

    @Test
    fun `truncated mid-step is flagged and still parses`() {
        val text = FULL_OUTPUT.substringBefore("3. Add the flour") + "3. Add the flour and mix unt"
        val recipe = parser.parse(text)!!

        assertThat(recipe.steps).hasSize(3)
        assertThat(recipe.steps.last()).isEqualTo("Add the flour and mix unt")
        assertThat(recipe.truncated).isTrue()
    }

    @Test
    fun `complete step without trailing newline is not flagged truncated`() {
        val recipe = parser.parse("Ingredients:\n- a\nSteps:\n1. Mix.\n2. Bake until golden.")!!
        assertThat(recipe.truncated).isFalse()
    }

    @Test
    fun `header synonyms, markdown decoration and other languages are accepted`() {
        val recipe = parser.parse(
            """
            **Name:** Tortilla de patatas
            Yield: 4 raciones
            Prep time: 20 minutes
            Cook time: 1 hour 5 min
            ## Ingredientes:
            • 6 huevos
            • 4 patatas
            Instrucciones:
            Step 1: Pelar las patatas.
            Step 2: Freír.
            Tips:
            Servir templada.
            """.trimIndent(),
        )!!

        assertThat(recipe.title).isEqualTo("Tortilla de patatas")
        assertThat(recipe.servings).isEqualTo("4 raciones")
        assertThat(recipe.prepMinutes).isEqualTo(20)
        assertThat(recipe.cookMinutes).isEqualTo(65)
        assertThat(recipe.ingredients).containsExactly("6 huevos", "4 patatas").inOrder()
        assertThat(recipe.steps).containsExactly("Pelar las patatas.", "Freír.").inOrder()
        assertThat(recipe.notes).isEqualTo("Servir templada.")
    }

    @Test
    fun `bullet variants, numbered ingredients, paren steps and continuation lines`() {
        val recipe = parser.parse(
            """
            Title: Soup
            Ingredients:
            * 1 onion
            – 2 carrots
            1. 3 cups stock
            Steps:
            1) Sweat the onion
            and carrots for 5 minutes.
            2) Add stock and simmer.
            """.trimIndent(),
        )!!

        assertThat(recipe.ingredients).containsExactly("1 onion", "2 carrots", "3 cups stock").inOrder()
        assertThat(recipe.steps).containsExactly("Sweat the onion and carrots for 5 minutes.", "Add stock and simmer.").inOrder()
    }

    @Test
    fun `preamble chatter before the first header is ignored`() {
        val recipe = parser.parse("Sure! Here is the recipe:\n\nTitle: Toast\nIngredients:\n- bread\nSteps:\n1. Toast it.\n")!!
        assertThat(recipe.title).isEqualTo("Toast")
        assertThat(recipe.ingredients).containsExactly("bread")
    }

    @Test
    fun `null when there are neither ingredients nor steps`() {
        assertThat(parser.parse("")).isNull()
        assertThat(parser.parse("Title: Nothing here\nDescription: just words")).isNull()
        assertThat(parser.parse("I could not find a recipe on this page.")).isNull()
    }

    @Test
    fun `minutes parsing table`() {
        val cases = mapOf(
            "45" to 45, "45 minutes" to 45, "1 hour" to 60, "1 hour 15 min" to 75, "1h30" to 90, "2 hrs" to 120,
            "PT1H30M" to 90, "P0DT0H20M" to 20, "20-25 min" to 25, "about 10 mins" to 10, "unknown" to null, "" to null,
        )
        for ((input, expected) in cases) {
            assertThat(RecipeLineGrammar.parseMinutes(input)).isEqualTo(expected)
        }
    }

    companion object {
        val FULL_OUTPUT = """
            Title: Classic Banana Bread
            Description: Moist banana bread with a crackly sugar top.
            Servings: 1 loaf (10 slices)
            PrepMinutes: 15
            CookMinutes: 60
            Ingredients:
            - 3 ripe bananas, mashed
            - 1/3 cup melted butter
            - 3/4 cup sugar
            - 1 egg, beaten
            - 1 1/2 cups all-purpose flour
            Steps:
            1. Preheat the oven to 175°C and butter a loaf pan.
            2. Stir the butter into the bananas, then mix in the sugar, egg and vanilla.
            3. Add the flour and mix until just combined.
            4. Bake for 60 minutes.
            Notes:
            Let it cool before slicing. Freezes well.
            
        """.trimIndent()
    }
}
