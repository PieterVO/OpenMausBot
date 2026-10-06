package com.openmausbot.companion.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationDesignTest {
    @Test fun `contact surfaces separate and ink actions pass contrast in all skins`() {
        val colors = listOf("green", "blue", "red", "orange", "purple", "cyan", "pink", "yellow", "teal", "coral", "unknown")
        for (skin in AppearanceSkin.entries) for (color in colors) {
            val background = Color(cssHexToArgb(skin.colors.app))
            val tint = ConversationTint.create(Color(MausPalette.argb(color)), background, skin.isDark)
            fun contrast(a: Color, b: Color): Float {
                val x = a.luminance(); val y = b.luminance()
                return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
            }
            assertTrue(contrast(tint.theirs, background) >= if (skin.isDark) 1.25f else 1.20f, "$skin / $color surface")
            assertTrue(contrast(tint.ink, tint.theirs) >= 4.5f, "$skin / $color ink")
            assertTrue(contrast(tint.ink, tint.actionText) >= 4.5f, "$skin / $color action")
        }
        assertTrue((Color.White.luminance() + 0.05f) / (BubbleColor.mine.luminance() + 0.05f) >= 4.5f)
    }
    @Test fun `only appended rows animate never initial content paging or updates`() {
        val arrivals = TranscriptArrivals()
        assertEquals(emptySet(), arrivals.update(listOf("a", "b")))
        assertEquals(emptySet(), arrivals.update(listOf("older", "a", "b")))
        assertEquals(setOf("c"), arrivals.update(listOf("older", "a", "b", "c")))
        assertEquals(setOf("c"), arrivals.update(listOf("older", "a", "b", "c")))
        assertEquals(setOf("c"), arrivals.update(listOf("oldest", "older", "a", "b", "c")))
        assertEquals(setOf("c", "d"), arrivals.update(listOf("oldest", "older", "a", "b", "c", "d")))
        assertTrue(arrivals.consume("c"))
        assertTrue(!arrivals.consume("c"), "A disposed and re-created row must not animate again")
        assertEquals(setOf("d"), arrivals.update(listOf("oldest", "older", "a", "b", "c", "d")))
    }
    @Test fun `first message in a loaded empty chat is a fresh arrival`() {
        val arrivals = TranscriptArrivals()
        assertEquals(emptySet(), arrivals.update(emptyList()))
        assertTrue(!arrivals.consumeTailChange())
        assertEquals(setOf("first"), arrivals.update(listOf("first")))
        assertTrue(arrivals.consumeTailChange())
        assertTrue(!arrivals.consumeTailChange(), "A preference re-render must not follow an old send")
        assertTrue(arrivals.consume("first"))
        assertEquals(emptySet(), arrivals.update(listOf("older", "first")))
        assertTrue(!arrivals.consumeTailChange(), "Paging at the head must not reclaim the viewport")
    }
    @Test fun `a visible live reply hands bot text over without another arrival`() {
        val arrivals = TranscriptArrivals()
        arrivals.update(listOf("ask"))
        arrivals.recordLiveTextVisible(true)
        assertEquals(setOf("tool", "user"), arrivals.update(
            listOf("ask", "tool", "narration", "reply", "user"),
            botTextIds = setOf("narration", "reply"),
        ))
        assertTrue(!arrivals.consume("reply"))
        assertTrue(!arrivals.consume("narration"), "Live narration uses the same invisible handover")
        assertTrue(arrivals.consume("tool"), "Work rows still use their normal arrivals")
        assertTrue(arrivals.consume("user"))
        assertTrue(arrivals.consumeTailChange(), "Handover still changes the tail for following")
    }
    @Test fun `hidden text and a working bubble do not count as visible streaming`() {
        val arrivals = TranscriptArrivals()
        arrivals.update(listOf("ask"))
        arrivals.recordLiveTextVisible(false)
        assertEquals(setOf("reply"), arrivals.update(listOf("ask", "reply"), botTextIds = setOf("reply")))
        assertTrue(arrivals.consume("reply"), "An unseen answer should reveal from empty")
    }
    @Test fun `previous live visibility cannot replay history or leak between trackers`() {
        val first = TranscriptArrivals()
        first.recordLiveTextVisible(true)
        assertEquals(emptySet(), first.update(listOf("history"), botTextIds = setOf("history")))
        assertEquals(emptySet(), first.update(listOf("older", "history"), botTextIds = setOf("older", "history")))
        first.recordLiveTextVisible(false)
        assertEquals(setOf("later"), first.update(listOf("older", "history", "later"), botTextIds = setOf("later")))
        val second = TranscriptArrivals()
        second.update(listOf("ask"))
        assertEquals(setOf("reply"), second.update(listOf("ask", "reply"), botTextIds = setOf("reply")))
    }
}
