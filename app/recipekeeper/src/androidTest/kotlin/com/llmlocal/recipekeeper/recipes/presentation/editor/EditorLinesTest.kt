package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.R
import org.junit.Rule
import org.junit.Test

/** X1 step 3: the Move up / Move down custom actions reorder rows; a pasted 5-line list becomes 5 rows. */
class EditorLinesTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val moveUp = res.getString(R.string.recipekeeper_editor_move_up)
    private val moveDown = res.getString(R.string.recipekeeper_editor_move_down)

    /** Scrolls the (lazy) editor list until a node matching [matcher] is composed, then returns it. */
    private fun scrolledTo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
        return rule.onNode(matcher)
    }

    private fun reorderHandle(step: Int) =
        scrolledTo(hasContentDescription(res.getString(R.string.recipekeeper_editor_reorder_step, step)))

    /** Step texts in list order, read by scrolling to each expected position. */
    private fun stepTexts(count: Int): List<String> = (1..count).map { n ->
        val handle = reorderHandle(n)
        val top = handle.fetchSemanticsNode().boundsInWindow.top
        rule.onAllNodes(hasSetTextAction() and hasText("Do thing", substring = true)).fetchSemanticsNodes()
            .minByOrNull { kotlin.math.abs(it.boundsInWindow.top - top) }!!
            .config[SemanticsProperties.EditableText].text
    }

    @Test
    fun moveDownAndUpReorderSteps() {
        val intents = mutableListOf<RecipeEditorIntent>()
        rule.setContent { EditorTestHost(initial = draft(ingredients = 1, steps = 3), intents = { intents += it }) }

        reorderHandle(1).performCustomAccessibilityActionWithLabel(moveDown)
        rule.waitForIdle()
        assertThat(stepTexts(3)).containsExactly("Do thing 1", "Do thing 0", "Do thing 2").inOrder()

        reorderHandle(3).performCustomAccessibilityActionWithLabel(moveUp)
        rule.waitForIdle()
        assertThat(stepTexts(3)).containsExactly("Do thing 1", "Do thing 2", "Do thing 0").inOrder()
        assertThat(intents.filterIsInstance<RecipeEditorIntent.MoveLine>()).hasSize(2)
    }

    @Test
    fun firstRowHasNoMoveUpAndLastHasNoMoveDown() {
        rule.setContent { EditorTestHost(initial = draft(ingredients = 1, steps = 3)) }

        fun labels(step: Int) = reorderHandle(step).fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        assertThat(labels(1)).containsExactly(moveDown)
        assertThat(labels(2)).containsExactly(moveUp, moveDown)
        assertThat(labels(3)).containsExactly(moveUp)
    }

    @Test
    fun pastingFiveLinesIntoAnIngredientYieldsFiveRows() {
        rule.setContent { EditorTestHost(initial = draft(ingredients = 1, steps = 1)) }

        scrolledTo(hasSetTextAction() and hasText("Item 0", substring = false))
            .performTextReplacement("One\nTwo\nThree\nFour\nFive")
        rule.waitForIdle()

        listOf("One", "Two", "Three", "Four", "Five").forEach { line ->
            scrolledTo(hasSetTextAction() and hasText(line, substring = false)).assertExists()
        }
    }
}
