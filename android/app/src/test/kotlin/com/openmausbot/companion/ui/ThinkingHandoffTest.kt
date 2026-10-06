package com.openmausbot.companion.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The answer outranks the reasoning: a reader who opened the thinking panel
 * must not keep a wall of reasoning above the reply once it starts streaming,
 * yet the folded "Thought for Ns" line must still reopen it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThinkingHandoffTest {
    @get:Rule
    val compose = createComposeRule()

    private val expanded = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded")

    @Test
    fun `an open reasoning panel folds when the answer starts and can be reopened`() {
        var answering by mutableStateOf(false)
        compose.setContent { ThinkingView(reasoning = "Comparing the two trains.\n\nThe later one is quieter.", answering = answering) }

        compose.onNodeWithTag("thinking-row").performClick()
        compose.onNodeWithTag("thinking-panel").assertIsDisplayed()

        answering = true
        compose.waitForIdle()
        compose.onNodeWithTag("thinking-panel").assertDoesNotExist()

        compose.onNodeWithTag("thinking-row").performClick()
        compose.onNodeWithTag("thinking-panel").assertIsDisplayed()
        compose.onNodeWithTag("thinking-row").assert(expanded)
    }
}
