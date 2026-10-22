package com.llmlocal.recipekeeper.clip.domain

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.llmlocal.recipekeeper.clip.domain.RecipeDraft
import org.junit.Test

class RecipeKeeperDraftParserTest {

    private val parser = RecipeKeeperDraftParser()
    private val full = RecipeKeeperParserTest.FULL_OUTPUT

    @Test
    fun `empty and header-only text give an empty draft with the tail`() {
        assertThat(parser.parse("")).isEqualTo(RecipeDraft(null, null, null, null, null, emptyList(), emptyList(), null, tail = ""))
        val partial = parser.parse("Title: Classic Ban")
        assertThat(partial.title).isNull()
        assertThat(partial.tail).isEqualTo("Title: Classic Ban")
    }

    @Test
    fun `draft stays empty (waiting hint) until a title, ingredient or step is parsed`() {
        assertThat(parser.parse("").isEmpty).isTrue()
        assertThat(parser.parse("\n \n").isEmpty).isTrue()
        assertThat(parser.parse("Title: Classic Ban").isEmpty).isTrue()
        assertThat(parser.parse("Title: Classic Banana Bread\n").isEmpty).isFalse()
        assertThat(parser.parse("Ingredients:\n- 3 ripe bananas\n").isEmpty).isFalse()
    }

    @Test
    fun `completed lines are promoted, the unfinished line stays in the tail`() {
        val draft = parser.parse("Title: Classic Banana Bread\nIngredients:\n- 3 ripe bananas\n- 1/3 cup mel")

        assertThat(draft.title).isEqualTo("Classic Banana Bread")
        assertThat(draft.ingredients).containsExactly("3 ripe bananas")
        assertThat(draft.tail).isEqualTo("- 1/3 cup mel")
        assertThat(draft.steps).isEmpty()
    }

    @Test
    fun `decimal quantities are promoted intact and dotted step numbers are stripped`() {
        val draft = parser.parse("Ingredients:\n- 250.0g potatoes\n- 2.5 cups flour\nSteps:\n2. Preheat.\n2.Grate.\n1.5 cups wat")

        assertThat(draft.ingredients).containsExactly("250.0g potatoes", "2.5 cups flour").inOrder()
        assertThat(draft.steps).containsExactly("Preheat.", "Grate.").inOrder()
        assertThat(draft.tail).isEqualTo("1.5 cups wat")
    }

    @Test
    fun `every prefix of a full output yields a monotonically growing draft`() {
        var previous = parser.parse("")
        for (end in 1..full.length) {
            val draft = parser.parse(full.substring(0, end))

            assertStable(previous.title, draft.title, "title@$end")
            assertStable(previous.description, draft.description, "description@$end")
            assertStable(previous.servings, draft.servings, "servings@$end")
            assertStable(previous.prepMinutes, draft.prepMinutes, "prep@$end")
            assertStable(previous.cookMinutes, draft.cookMinutes, "cook@$end")
            assertGrows(previous.ingredients, draft.ingredients, "ingredients@$end")
            assertGrows(previous.steps, draft.steps, "steps@$end")
            if (previous.notes != null) assertThat(draft.notes).startsWith(previous.notes!!)
            previous = draft
        }

        val finalDraft = parser.parse(full)
        val finalRecipe = RecipeKeeperParser().parse(full)!!
        assertThat(finalDraft.title).isEqualTo(finalRecipe.title)
        assertThat(finalDraft.ingredients).isEqualTo(finalRecipe.ingredients)
        assertThat(finalDraft.steps).isEqualTo(finalRecipe.steps)
        assertThat(finalDraft.notes).isEqualTo(finalRecipe.notes)
        assertThat(finalDraft.tail).isEmpty()
    }

    private fun <T> assertStable(before: T?, after: T?, label: String) {
        if (before != null) assertWithMessage(label).that(after).isEqualTo(before)
    }

    private fun assertGrows(before: List<String>, after: List<String>, label: String) {
        assertWithMessage(label).that(after.size).isAtLeast(before.size)
        before.forEachIndexed { i, item -> assertWithMessage("$label[$i]").that(after[i]).startsWith(item) }
    }
}
