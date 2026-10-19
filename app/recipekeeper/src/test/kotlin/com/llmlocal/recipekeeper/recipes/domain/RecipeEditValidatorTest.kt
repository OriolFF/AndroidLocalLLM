package com.llmlocal.recipekeeper.recipes.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecipeEditValidatorTest {

    private fun edit(title: String = "Soup", ingredient: String = "water", step: String = "boil") = RecipeEdit(
        id = null,
        origin = RecipeOrigin.CREATED,
        title = title,
        ingredients = listOf(EditableLine(1, ingredient)),
        steps = listOf(EditableLine(2, step)),
    )

    @Test
    fun `valid draft has no errors`() {
        assertThat(RecipeEditValidator.validate(edit())).isEmpty()
    }

    @Test
    fun `blank title is required`() {
        assertThat(RecipeEditValidator.validate(edit(title = "  "))).containsExactly(EditorError.TitleRequired)
    }

    @Test
    fun `needs an ingredient or a step`() {
        assertThat(RecipeEditValidator.validate(edit(ingredient = " ", step = ""))).containsExactly(EditorError.NeedContent)
        assertThat(RecipeEditValidator.validate(edit(ingredient = " "))).isEmpty()
        assertThat(RecipeEditValidator.validate(edit(step = ""))).isEmpty()
    }

    @Test
    fun `reports both errors`() {
        assertThat(RecipeEditValidator.validate(edit(title = "", ingredient = "", step = "")))
            .containsExactly(EditorError.TitleRequired, EditorError.NeedContent)
    }
}
