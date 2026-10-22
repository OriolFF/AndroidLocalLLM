package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * B1: a row focused while the keyboard is open must end up above it. Pressing
 * Next on the last ingredient adds a row below and focuses it, again and again,
 * so each new row starts below the keyboard. Needs a soft keyboard on the device
 * (`adb shell settings put secure show_ime_with_hard_kb 1` when the AVD has a
 * hardware keyboard).
 */
class EditorImeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun edgeToEdge() {
        rule.activityRule.scenario.onActivity { WindowCompat.setDecorFitsSystemWindows(it.window, false) }
    }

    @Test
    fun newlyFocusedRowStaysAboveKeyboard() {
        rule.setContent { EditorTestHost(initial = draft(ingredients = 1, steps = 1)) }
        val view = rule.activity.window.decorView

        rule.onNode(hasSetTextAction() and hasText("Item 0", substring = false)).performScrollTo().performClick()
        rule.runOnUiThread { rule.activity.window.insetsController?.show(WindowInsetsCompat.Type.ime()) }
        rule.waitUntil(timeoutMillis = 15_000) {
            ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }

        repeat(8) {
            rule.onNode(isFocused() and hasSetTextAction()).performImeAction()
            rule.waitForIdle()
            Thread.sleep(700) // placement animation + the settle re-request
            rule.waitForIdle()
        }

        val focused = rule.onNode(isFocused() and hasSetTextAction())
        focused.assertIsDisplayed()
        val imeTop = view.height - ViewCompat.getRootWindowInsets(view)!!
            .getInsets(WindowInsetsCompat.Type.ime()).bottom
        val imeHeight = view.height - imeTop
        assertWithMessage("keyboard height $imeHeight px").that(imeHeight).isGreaterThan(300)
        val bounds = focused.fetchSemanticsNode().boundsInWindow
        assertWithMessage("focused row $bounds must sit above keyboard top $imeTop")
            .that(bounds.bottom).isAtMost(imeTop.toFloat())
    }
}
