package com.llmlocal.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientTest {

    @Test
    fun `accepts non-blank name`() {
        val ingredient = Ingredient(name = "eggs")
        assertEquals("eggs", ingredient.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects blank name`() {
        Ingredient(name = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty name`() {
        Ingredient(name = "")
    }
}
