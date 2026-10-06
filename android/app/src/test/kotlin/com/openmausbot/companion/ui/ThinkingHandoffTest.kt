package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import java.time.Duration
import org.junit.Assert.assertTrue
import org.robolectric.shadows.ShadowSystemClock
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
import androidx.compose.ui.test.onNodeWithText
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

    @Test
    fun `offscreen thinking keeps the finished duration when shown again`() {
        var clipped by mutableStateOf(false)
        var answering by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.size(200.dp).clipToBounds()) {
                Box(Modifier.offset(y = if (clipped) 400.dp else 0.dp)) {
                    ThinkingView(reasoning = "Comparing the trains.", answering = answering)
                }
            }
        }
        compose.runOnIdle { clipped = true }
        compose.waitForIdle()
        compose.runOnIdle { ShadowSystemClock.advanceBy(Duration.ofSeconds(8)) }
        compose.runOnIdle { answering = true }
        compose.waitForIdle()
        compose.runOnIdle { clipped = false }
        compose.waitForIdle()
        val text = compose.onNodeWithText("Thought for", substring = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        val seconds = Regex("Thought for (\\d+)s").find(text)?.groupValues?.get(1)?.toInt()
        assertTrue("paused thinking must include its hidden elapsed time: $text", seconds != null && seconds >= 8)
        compose.runOnIdle {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
            clipped = true
        }
        compose.waitForIdle()
        compose.runOnIdle { clipped = false }
        compose.waitForIdle()
        compose.onNodeWithText(text).assertIsDisplayed()
    }
}
