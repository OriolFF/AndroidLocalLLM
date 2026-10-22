package com.llmlocal.recipekeeper.recipes.presentation.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.llmlocal.recipekeeper.recipes.presentation.recipeKeeperLibraryLabels
import org.junit.Rule
import org.junit.Test

/** B2: the heart exposes its on/off state through `SemanticsProperties.StateDescription`, not only the glyph. */
class FavouriteToggleSemanticsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val labels = recipeKeeperLibraryLabels(InstrumentationRegistry.getInstrumentation().targetContext.resources)

    private fun hasState(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun stateDescriptionFollowsCheckedState() {
        rule.setContent {
            var checked by remember { mutableStateOf(false) }
            FavouriteToggle(checked = checked, labels = labels, onCheckedChange = { checked = it })
        }

        rule.onNodeWithContentDescription(labels.favourite, useUnmergedTree = false).assert(hasState(labels.favouriteStateOff))
        rule.onNodeWithContentDescription(labels.favourite).performClick()
        rule.onNodeWithContentDescription(labels.favourite).assert(hasState(labels.favouriteStateOn))
        rule.onNodeWithContentDescription(labels.favourite).performClick()
        rule.onNodeWithContentDescription(labels.favourite).assert(hasState(labels.favouriteStateOff))
    }
}
