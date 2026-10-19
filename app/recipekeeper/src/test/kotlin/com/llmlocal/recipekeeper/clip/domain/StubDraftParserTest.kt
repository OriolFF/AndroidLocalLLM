package com.llmlocal.recipekeeper.clip.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StubDraftParserTest {

    private val full = """
        Title: Tomato soup
        Servings: 4
        PrepMinutes: 10
        CookMinutes: 25
        Ingredients:
        - 1 kg tomatoes
        * 2 tbsp oil
        Steps:
        1. Roast.
        2) Blend.
        Notes:
        Freezes well.

    """.trimIndent()

    @Test
    fun `parses a complete output`() {
        val draft = StubDraftParser.parse(full)

        assertThat(draft.title).isEqualTo("Tomato soup")
        assertThat(draft.servings).isEqualTo("4")
        assertThat(draft.prepMinutes).isEqualTo(10)
        assertThat(draft.cookMinutes).isEqualTo(25)
        assertThat(draft.ingredients).containsExactly("1 kg tomatoes", "2 tbsp oil").inOrder()
        assertThat(draft.steps).containsExactly("Roast.", "Blend.").inOrder()
        assertThat(draft.notes).isEqualTo("Freezes well.")
        assertThat(draft.tail).isEmpty()
    }

    @Test
    fun `an unterminated last line stays in the tail`() {
        val draft = StubDraftParser.parse("Title: Tomato soup\nIngredients:\n- 1 kg toma")

        assertThat(draft.title).isEqualTo("Tomato soup")
        assertThat(draft.ingredients).isEmpty()
        assertThat(draft.tail).isEqualTo("- 1 kg toma")
    }

    @Test
    fun `every prefix yields a draft that only grows`() {
        var previous = StubDraftParser.parse("")
        for (end in 1..full.length) {
            val draft = StubDraftParser.parse(full.substring(0, end))
            assertThat(draft.ingredients.size).isAtLeast(previous.ingredients.size)
            assertThat(draft.steps.size).isAtLeast(previous.steps.size)
            if (previous.title != null) assertThat(draft.title).isEqualTo(previous.title)
            previous = draft
        }
    }
}
