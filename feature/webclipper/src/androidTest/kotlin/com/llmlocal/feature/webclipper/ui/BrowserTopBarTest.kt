package com.llmlocal.feature.webclipper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.webclipper.WebClipperLabels
import com.llmlocal.feature.webclipper.mvi.BrowserIntent
import com.llmlocal.feature.webclipper.mvi.BrowserState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-QA regression for finding 1: the address bar pill must enter edit
 * mode when tapped (and on a blank start), stay focused, and route typing +
 * the IME *Go* action to the ViewModel as intents.
 *
 * The ViewModel is replaced by a two-field reducer so the test exercises the
 * composable's focus handling only.
 */
@RunWith(AndroidJUnit4::class)
class BrowserTopBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val labels = WebClipperLabels()
    private val intents = mutableListOf<BrowserIntent>()

    /** Minimal stand-in for `BrowserViewModel.onIntent`: focus + draft only. */
    private class Host(initial: BrowserState) {
        var state by mutableStateOf(initial)

        fun reduce(intent: BrowserIntent) {
            state = when (intent) {
                is BrowserIntent.UrlFocusChanged -> state.copy(
                    urlFocused = intent.focused,
                    draftUrl = if (intent.focused) state.url else state.draftUrl,
                )
                is BrowserIntent.UpdateDraftUrl -> state.copy(draftUrl = intent.value)
                BrowserIntent.SubmitUrl -> state.copy(urlFocused = false)
                else -> state
            }
        }
    }

    private fun setTopBar(initial: BrowserState): Host {
        val host = Host(initial)
        compose.setContent {
            MaterialTheme {
                BrowserTopBar(
                    state = host.state,
                    labels = labels,
                    onIntent = { intent ->
                        intents += intent
                        host.reduce(intent)
                    },
                    onBackToLibrary = {},
                )
            }
        }
        return host
    }

    private fun editableAddressBar() = compose.onNode(
        hasSetTextAction() and hasContentDescription(labels.urlFieldDescription),
    )

    private fun pillButton() = compose.onNode(
        hasContentDescription(labels.urlFieldDescription) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
    )

    @Test
    fun tappingThePillOpensAFocusedEditableField() {
        val host = setTopBar(BrowserState(url = "https://www.cooking.example/recipes/pasta"))

        editableAddressBar().assertDoesNotExist()
        pillButton().performClick()
        compose.waitForIdle()

        assertThat(host.state.urlFocused).isTrue()
        editableAddressBar().assertExists().assertIsFocused()
        // The draft is pre-filled with the full URL so the user can edit it.
        editableAddressBar().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString("https://www.cooking.example/recipes/pasta"),
            ),
        )
    }

    @Test
    fun typingAndImeGoEmitDraftUpdatesAndSubmit() {
        setTopBar(BrowserState())

        pillButton().performClick()
        compose.waitForIdle()
        editableAddressBar().assertIsFocused()

        editableAddressBar().performTextInput("cooking.example/pasta")
        compose.waitForIdle()
        assertThat(intents.filterIsInstance<BrowserIntent.UpdateDraftUrl>().last().value)
            .isEqualTo("cooking.example/pasta")

        editableAddressBar().performImeAction()
        compose.waitForIdle()
        // *Go* submits and clears focus; the blur event may land before or
        // after `SubmitUrl` (the ViewModel handles both orders), so only the
        // relative order with the last draft update matters.
        val lastDraft = intents.indexOfLast { it is BrowserIntent.UpdateDraftUrl }
        val submit = intents.indexOf(BrowserIntent.SubmitUrl)
        assertThat(submit).isGreaterThan(lastDraft)
        assertThat(intents.filterIsInstance<BrowserIntent.UpdateDraftUrl>().last().value)
            .isEqualTo("cooking.example/pasta")
    }

    @Test
    fun blankStartAutoFocusesTheAddressBar() {
        val host = Host(BrowserState())
        compose.setContent {
            MaterialTheme {
                WebClipperBrowserScreen(
                    state = host.state,
                    labels = labels,
                    onIntent = { intent ->
                        intents += intent
                        host.reduce(intent)
                    },
                    onBackToLibrary = {},
                    snackbarHostState = SnackbarHostState(),
                    webView = { modifier -> Box(modifier) },
                )
            }
        }
        compose.waitForIdle()

        assertThat(host.state.urlFocused).isTrue()
        editableAddressBar().assertExists().assertIsFocused()
    }
}
