package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.recipes.domain.EditSection
import org.junit.Test

class SplitPastedLinesUseCaseTest {

    private val split = SplitPastedLinesUseCase()

    @Test
    fun `ingredients strip bullets numbers and blank lines`() {
        val text = "- 2 eggs\n* 100 g flour\n• milk\n\n1. salt\n2) pepper\n(3) oil\n  plain  "
        assertThat(split(text, EditSection.INGREDIENTS))
            .containsExactly("2 eggs", "100 g flour", "milk", "salt", "pepper", "oil", "plain").inOrder()
    }

    @Test
    fun `ingredients handle windows line endings`() {
        assertThat(split("a\r\nb\r\n", EditSection.INGREDIENTS)).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `steps split on numbered markers keeping multi-line bodies`() {
        val text = "1. Whisk the eggs\nuntil pale.\n2) Fold in flour.\nStep 3: Bake."
        assertThat(split(text, EditSection.STEPS))
            .containsExactly("Whisk the eggs\nuntil pale.", "Fold in flour.", "Bake.").inOrder()
    }

    @Test
    fun `steps split on blank lines when no markers`() {
        assertThat(split("Mix it.\n\nBake it.", EditSection.STEPS)).containsExactly("Mix it.", "Bake it.").inOrder()
    }

    @Test
    fun `steps without markers or blank lines stay one step`() {
        assertThat(split("Mix it\nthen bake", EditSection.STEPS)).containsExactly("Mix it\nthen bake")
    }

    @Test
    fun `a single marker is not a list`() {
        assertThat(split("1. only one\nmore", EditSection.STEPS)).containsExactly("1. only one\nmore")
    }

    @Test
    fun `steps handle windows line endings`() {
        assertThat(split("1. a\r\n2. b", EditSection.STEPS)).containsExactly("a", "b").inOrder()
    }
}
