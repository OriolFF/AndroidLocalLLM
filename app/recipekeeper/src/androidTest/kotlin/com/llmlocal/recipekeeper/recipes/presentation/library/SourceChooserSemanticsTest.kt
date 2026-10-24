package com.llmlocal.recipekeeper.recipes.presentation.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import com.llmlocal.recipekeeper.recipes.domain.RecipeSource
import com.llmlocal.recipekeeper.recipes.presentation.recipeKeeperLibraryLabels
import org.junit.Rule
import org.junit.Test

/** Source chooser rows: voice is disabled with a "Coming soon" state, the images row follows availability. */
class SourceChooserSemanticsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val labels = recipeKeeperLibraryLabels(InstrumentationRegistry.getInstrumentation().targetContext.resources)

    private fun show(availability: ImagesAvailability, onIntent: (LibraryIntent) -> Unit = {}) {
        rule.setContent { SourceChooserContent(imagesAvailability = availability, labels = labels, onIntent = onIntent) }
    }

    @Test
    fun voiceRowIsDisabledWithComingSoonStateDescription() {
        show(ImagesAvailability.Available)

        rule.onNodeWithText(labels.sourceVoice).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, labels.comingSoon))
        rule.onNodeWithText(labels.sourceWeb).assertIsEnabled()
        rule.onNodeWithText(labels.sourceEditor).assertIsEnabled()
    }

    @Test
    fun imagesRowSupportingTextFollowsAvailability() {
        show(ImagesAvailability.NoModel)
        rule.onNodeWithText(labels.imagesNeedsModel).assertExists()
    }

    @Test
    fun imagesRowSaysTheModelCannotReadImages() {
        show(ImagesAvailability.ModelLacksVision)
        rule.onNodeWithText(labels.imagesModelCantRead).assertExists()
    }

    @Test
    fun imagesRowIsAbsentWhenDisabled() {
        show(ImagesAvailability.Disabled)
        rule.onNodeWithText(labels.sourceImages).assertDoesNotExist()
    }

    @Test
    fun tappingRowsSendsChooseSourceAndVoiceSendsNothing() {
        val intents = mutableListOf<LibraryIntent>()
        show(ImagesAvailability.Available) { intents += it }

        rule.onNodeWithText(labels.sourceImages).performClick()
        rule.onNodeWithText(labels.sourceVoice).performClick()
        assertThat(intents).containsExactly(LibraryIntent.ChooseSource(RecipeSource.IMAGES))
    }
}
