package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.core.MarkdownBlock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownConversationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun inlineCodeLinkKeepsCapsuleAndOpensTarget() {
        var opened: String? = null
        compose.setContent { MaterialTheme {
            MarkdownText("[`folder`](https://example.invalid/notes)", Modifier.testTag("code-link"), openLink = { opened = it })
        } }
        assertEquals(TextDecoration.Underline, codeLayout("folder").layoutInput.style.textDecoration)
        compose.onNodeWithTag("code-link").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("https://example.invalid/notes", opened) }
    }

    @Test fun completedTaskCodeKeepsBoldMonospacingAndStrike() {
        compose.setContent { MaterialTheme { MarkdownText("- [x] **`folder`**") } }
        val style = codeLayout("folder").layoutInput.style
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(16.sp * 0.94f, style.fontSize)
        assertTrue(style.textDecoration?.contains(TextDecoration.LineThrough) == true)
    }

    @Test fun caretOpacityChangesKeepBodyLayoutAndAccessibleText() {
        compose.mainClock.autoAdvance = false
        val opacity = mutableFloatStateOf(1f)
        val blocks = listOf(MarkdownBlock.Paragraph("A reply"))
        compose.setContent { MaterialTheme {
            MarkdownBlocks(blocks, Modifier.testTag("caret-reply"), caret = true, caretAlpha = { opacity.floatValue })
        } }
        compose.mainClock.advanceTimeByFrame()
        val before = caretLayout()
        assertEquals("A reply ▍", before.layoutInput.text.text)
        compose.runOnIdle { opacity.floatValue = 0.35f }
        compose.mainClock.advanceTimeByFrame()
        val after = caretLayout()
        assertSame(before.layoutInput.text, after.layoutInput.text)
        assertEquals(before.size, after.size)
        compose.onNodeWithText("A reply ▍", substring = false).assertExists()
    }

    private fun caretLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("A reply ▍", substring = false).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    private fun codeLayout(text: String): TextLayoutResult {
        compose.waitForIdle()
        return compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.firstOrNull { it.layoutInput.style.fontFamily == FontFamily.Monospace }
        }.single()
    }
}
