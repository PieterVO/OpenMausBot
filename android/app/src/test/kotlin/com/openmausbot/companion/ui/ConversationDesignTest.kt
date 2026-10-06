package com.openmausbot.companion.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationDesignTest {
    @Test fun `contact ink and filled actions pass contrast in all skins`() {
        val colors = listOf("green", "blue", "red", "orange", "purple", "cyan", "pink", "yellow", "teal", "coral", "unknown")
        for (skin in AppearanceSkin.entries) for (color in colors) {
            val tint = ConversationTint.create(Color(MausPalette.argb(color)), Color(cssHexToArgb(skin.colors.card)), skin.isDark)
            fun contrast(a: Color, b: Color): Float {
                val x = a.luminance(); val y = b.luminance()
                return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
            }
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
}
