package com.llmlocal.core.llm.prompt

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.model.Ingredient
import org.junit.Test

class RecipePromptBuilderTest {

    private val builder = RecipePromptBuilder()

    @Test
    fun `system instruction mentions Title, Ingredients, Steps sections`() {
        val system = builder.systemInstruction()
        assertThat(system).contains("Title")
        assertThat(system).contains("Ingredients")
        assertThat(system).contains("Steps")
    }

    @Test
    fun `user prompt lists each ingredient on a separate line`() {
        val ingredients = listOf(
            Ingredient("eggs"),
            Ingredient("tomato"),
            Ingredient("onion"),
        )
        val user = builder.userPrompt(ingredients)
        assertThat(user).contains("- eggs")
        assertThat(user).contains("- tomato")
        assertThat(user).contains("- onion")
    }

    @Test
    fun `user prompt with no ingredients still produces a well-formed message`() {
        val user = builder.userPrompt(emptyList())
        // Should still include the "Ingredients:" header.
        assertThat(user).contains("Ingredients")
    }
}
