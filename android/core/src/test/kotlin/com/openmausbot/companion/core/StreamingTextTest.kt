package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamingTextTest {
    private val table = "Spots:\n| Place | Walk |\n| --- | --- |\n| Linden | 6 min |\n| Ferry | 11 min |\nDone."

    @Test
    fun aHeaderWaitsForItsDelimiterRow() {
        assertEquals("Spots:\n", StreamingText.revealedPrefix(
            "Spots:\n| Place | Walk |\n| --", count = 30, final = false))
        assertEquals("Spots:\n", StreamingText.revealedPrefix(
            "Spots:\n| Place | Wa", count = 20, final = false))
    }

    @Test
    fun aHeaderShowsWithItsDelimiterOnceBothExist() {
        assertEquals("Spots:\n| Place | Walk |\n| --- | --- |",
            StreamingText.revealedPrefix(table, count = 12, final = true))
        val arrived = "Spots:\n| Place | Walk |\n| --- | --- |\n"
        assertEquals(arrived, StreamingText.revealedPrefix(arrived, count = arrived.length, final = false))
    }

    @Test
    fun aBodyRowAppearsWhole() {
        val into = "Spots:\n| Place | Walk |\n| --- | --- |\n| Lin".length
        assertEquals("Spots:\n| Place | Walk |\n| --- | --- |\n| Linden | 6 min |",
            StreamingText.revealedPrefix(table, count = into, final = true))
        assertEquals("| A |\n| - |\n",
            StreamingText.revealedPrefix("| A |\n| - |\n| Lin", count = 15, final = false))
    }

    @Test
    fun textOutsideTablesIsCutWhereTheRevealIs() {
        assertEquals("Spo", StreamingText.revealedPrefix(table, count = 3, final = true))
        assertEquals(table, StreamingText.revealedPrefix(table, count = table.length, final = true))
    }

    @Test
    fun streamUsesSixtyUnitsPerSecondForSmallBacklogs() {
        assertEquals(6.0, StreamingText.step(0.0, 20, 0.1), 1e-9)
        assertEquals(17.0, StreamingText.step(11.0, 20, 0.1), 1e-9)
        assertEquals(20.0, StreamingText.step(19.0, 20, 0.1), 1e-9)
    }

    @Test
    fun subCharacterFrameProgressIsRetained() {
        val first = StreamingText.step(0.0, 10, 1.0 / 120.0)
        val second = StreamingText.step(first, 10, 1.0 / 120.0)
        assertEquals(0.5, first, 1e-9)
        assertEquals(1.0, second, 1e-9)
        assertEquals(0, StreamingText.prefixEnd(intArrayOf(1, 2, 3), first))
        assertEquals(1, StreamingText.prefixEnd(intArrayOf(1, 2, 3), second))
    }

    @Test
    fun streamCatchupUsesTheCurrentBacklogRatherThanTheTotalLength() {
        assertEquals(100.0, StreamingText.step(0.0, 350, 0.1), 1e-9)
        assertEquals(150.0, StreamingText.step(50.0, 400, 0.1), 1e-9)
        val first = StreamingText.step(0.0, 350, 0.1)
        assertEquals(first + (350.0 - first) / 0.35 * 0.1, StreamingText.step(first, 350, 0.1), 1e-9)
        assertEquals(350.0, StreamingText.step(0.0, 350, 0.35), 1e-9)
    }

    @Test
    fun steadyFastStreamStaysWithinPointThreeFiveSecondsOfReceivedText() {
        val unitsPerSecond = 1_000
        val secondsPerFrame = 0.01
        var position = 0.0
        for (frame in 1..1_000) {
            val receivedLength = frame * 10
            position = StreamingText.step(position, receivedLength, secondsPerFrame)
            assertTrue(receivedLength - position <= unitsPerSecond * 0.35 + 1e-9)
        }
        assertEquals(unitsPerSecond * (0.35 - secondsPerFrame), 10_000 - position, 1e-6)
    }

    @Test
    fun stoppedStreamEventuallyConsumesTheTail() {
        var position = 0.0
        repeat(300) { position = StreamingText.step(position, 1_000, 1.0 / 60.0) }
        assertEquals(1_000.0, position)
    }

    @Test
    fun arrivalUsesNinetyUnitsPerSecondOrTotalLengthOverPointNine() {
        assertEquals(9.0, StreamingText.step(0.0, 50, 0.1, arrival = true), 1e-9)
        assertEquals(90.0, StreamingText.step(0.0, 810, 0.1, arrival = true), 1e-9)
        // The arrival rate does not decay as the unrevealed tail shrinks.
        assertEquals(590.0, StreamingText.step(500.0, 810, 0.1, arrival = true), 1e-9)
    }

    @Test
    fun everyArrivalLengthFinishesWithinPointNineSeconds() {
        for (length in listOf(0, 1, 20, 80, 81, 90, 810, 8_100, 100_000)) {
            assertEquals(length.toDouble(), StreamingText.step(0.0, length, 0.9, arrival = true), 1e-9)
            assertEquals(length.toDouble(), StreamingText.step(length * 0.25, length, 0.9, arrival = true), 1e-9)
            for (frames in listOf(54, 90, 108, 216)) {
                var position = 0.0
                repeat(frames) { position = StreamingText.step(position, length, 0.9 / frames, arrival = true) }
                assertEquals(length.toDouble(), position, "$length units over $frames frames")
            }
        }
    }

    @Test
    fun cursorIsClampedWithoutReversingForIdleOrNegativeFrameTimes() {
        assertEquals(0.0, StreamingText.step(-2.0, 10, 0.0))
        assertEquals(10.0, StreamingText.step(12.0, 10, 0.1))
        assertEquals(3.25, StreamingText.step(3.25, 10, 0.0))
        assertEquals(3.25, StreamingText.step(3.25, 10, -0.1))
        assertEquals(0.0, StreamingText.step(0.0, 0, 1.0))
    }

    @Test
    fun plainTextEndpointsAndBinaryLookupHandleFractionalBounds() {
        val ends = StreamingText.graphemeEnds("abcd")
        assertContentEquals(intArrayOf(1, 2, 3, 4), ends)
        for ((position, expected) in listOf(-1.0 to 0, 0.0 to 0, 0.999 to 0, 1.0 to 1, 2.9 to 2, 4.0 to 4, 100.0 to 4)) {
            assertEquals(expected, StreamingText.prefixEnd(ends, position), "cursor $position")
        }
        assertContentEquals(intArrayOf(), StreamingText.graphemeEnds(""))
        assertEquals(0, StreamingText.prefixEnd(intArrayOf(), 100.0))
    }

    @Test
    fun surrogateEmojiDoesNotRevealHalfASurrogatePair() {
        val ends = StreamingText.graphemeEnds("A😀B")
        assertContentEquals(intArrayOf(1, 3, 4), ends)
        assertEquals(1, StreamingText.prefixEnd(ends, 2.999))
        assertEquals(3, StreamingText.prefixEnd(ends, 3.0))
    }

    @Test
    fun familyAndOtherJoinedEmojiAreSingleClusters() {
        val family = "👨‍👩‍👧‍👦"
        val source = "A${family}B"
        val ends = StreamingText.graphemeEnds(source)
        assertContentEquals(intArrayOf(1, 1 + family.length, source.length), ends)
        for (position in 1 until 1 + family.length) {
            assertEquals(1, StreamingText.prefixEnd(ends, position.toDouble()))
        }
        assertContentEquals(intArrayOf("👩🏽‍💻".length), StreamingText.graphemeEnds("👩🏽‍💻"))
        assertContentEquals(intArrayOf("🏳️‍🌈".length), StreamingText.graphemeEnds("🏳️‍🌈"))
    }

    @Test
    fun accentsSelectorsModifiersAndKeycapsStayWithTheirBase() {
        for (cluster in listOf("e\u0301\u0327", "✈\uFE0F", "😀\uDB40\uDD00", "👍🏽", "1\uFE0F\u20E3")) {
            val ends = StreamingText.graphemeEnds(cluster)
            assertContentEquals(intArrayOf(cluster.length), ends, cluster)
            for (position in 0 until cluster.length) {
                assertEquals(0, StreamingText.prefixEnd(ends, position.toDouble()), cluster)
            }
        }
        assertContentEquals(intArrayOf(2), StreamingText.graphemeEnds("\r\n"))
    }

    @Test
    fun flagsPairRegionalIndicatorsWithoutExposingHalfAFlag() {
        val source = "🇺🇸🇨🇦"
        val ends = StreamingText.graphemeEnds(source)
        assertContentEquals(intArrayOf(4, 8), ends)
        assertEquals(0, StreamingText.prefixEnd(ends, 3.99))
        assertEquals(4, StreamingText.prefixEnd(ends, 7.99))
        assertContentEquals(intArrayOf(4), StreamingText.graphemeEnds("🇺🇸🇨"))
        assertContentEquals(intArrayOf(), StreamingText.graphemeEnds("🇺"))
    }

    @Test
    fun truncatedNetworkSuffixesWithholdSurrogatesAndJoinedClusters() {
        assertContentEquals(intArrayOf(), StreamingText.graphemeEnds("\uD83D"))
        assertContentEquals(intArrayOf(1), StreamingText.graphemeEnds("a\uD83D"))
        assertContentEquals(intArrayOf(1), StreamingText.graphemeEnds("a👨‍"))
        assertContentEquals(intArrayOf(1), StreamingText.graphemeEnds("a👨‍\uD83D"))
        assertContentEquals(intArrayOf(1, 6), StreamingText.graphemeEnds("a👨‍👩"))
        val source = "a👨‍👩‍👧‍👦🇺🇸e\u0301"
        for (length in 0..source.length) {
            val batch = source.take(length)
            val visibleEnd = StreamingText.prefixEnd(StreamingText.graphemeEnds(batch), length.toDouble())
            val visible = batch.take(visibleEnd)
            assertFalse(visible.lastOrNull()?.isHighSurrogate() == true, "batch length $length")
            assertFalse(visible.endsWith('\u200D'), "batch length $length")
        }
    }

    @Test
    fun settledArrivalCursorStillReachesHeldLegitimateSuffixes() {
        // Open streams withhold these; a settled UI can show them using the full-length cursor.
        for (source in listOf("🇺", "a👨‍")) {
            val position = StreamingText.step(0.0, source.length, 0.9, arrival = true)
            assertEquals(source.length.toDouble(), position)
            assertTrue(StreamingText.prefixEnd(StreamingText.graphemeEnds(source), position) < source.length)
        }
        // A settled caller must not bypass the safety boundary for a dangling high surrogate.
        assertEquals(1, StreamingText.prefixEnd(StreamingText.graphemeEnds("a\uD83D"), 2.0))
    }

    @Test
    fun completedInlineMarkdownIsUnchanged() {
        for (source in listOf("plain", "**bold**", "`code`", "**bold `code` tail**", "`` a ` tick ``", "**one** and **two**")) {
            assertEquals(source, StreamingText.closePartialMarkdown(source), source)
        }
    }

    @Test
    fun unfinishedInlineMarkdownClosesInsideOut() {
        val examples = listOf(
            "**bold" to "**bold**",
            "`code" to "`code`",
            "**bold `code" to "**bold `code`**",
            "**bold `**literal" to "**bold `**literal`**",
            "`` a ` tick" to "`` a ` tick``",
            "**outer **inner" to "**outer **inner****",
        )
        for ((source, expected) in examples) assertEquals(expected, StreamingText.closePartialMarkdown(source), source)
    }

    @Test
    fun codeContentsDoNotOpenStrongSpansAndBackslashesDoNotEscapeCodeClosers() {
        assertEquals("`**literal`", StreamingText.closePartialMarkdown("`**literal"))
        assertEquals("`value \\`", StreamingText.closePartialMarkdown("`value \\`"))
        assertEquals("**outer `value \\`**", StreamingText.closePartialMarkdown("**outer `value \\`"))
    }

    @Test
    fun escapedDelimitersAndEvenBackslashesAreRespected() {
        for (source in listOf("\\**literal", "\\`literal", "**done** \\**literal", "**done** \\`literal")) {
            assertEquals(source, StreamingText.closePartialMarkdown(source), source)
        }
        assertEquals("\\\\**bold**", StreamingText.closePartialMarkdown("\\\\**bold"))
        assertEquals("\\\\`code`", StreamingText.closePartialMarkdown("\\\\`code"))
    }

    @Test
    fun contentFreeOrEscapedClosersDoNotManufactureSyntax() {
        for (source in listOf("**", "** ", "`", "` ", "**word `", "**word \\", "**word **", "**word *")) {
            assertEquals(source, StreamingText.closePartialMarkdown(source), source)
        }
        assertEquals("**word**  ", StreamingText.closePartialMarkdown("**word  "))
        assertEquals("**word**\n", StreamingText.closePartialMarkdown("**word\n"))
    }

    @Test
    fun activeFencesNeverReceiveInlineClosers() {
        for (source in listOf("```kotlin\n**bold `code", "~~~text\n**bold `code", "**before\n```\n**code `", "```\ncode\n``` not a closing fence\n**code")) {
            assertEquals(source, StreamingText.closePartialMarkdown(source), source)
        }
    }

    @Test
    fun fenceMarkerWidthIndentAndClosingContextAreRespected() {
        val examples = listOf(
            "~~~\n**code `\n~~~\n**outside" to "~~~\n**code `\n~~~\n**outside**",
            "````\n```\n**code `\n````\n`outside" to "````\n```\n**code `\n````\n`outside`",
            "  ```js\n**code `\n  ```\n**outside" to "  ```js\n**code `\n  ```\n**outside**",
            "```\n~~~\n**code `\n```\n**outside" to "```\n~~~\n**code `\n```\n**outside**",
        )
        for ((source, expected) in examples) assertEquals(expected, StreamingText.closePartialMarkdown(source), source)
    }

    @Test
    fun inlineDelimitersDoNotLeakAcrossParagraphsOrFencedBlocks() {
        val blankLine = "**before\n\nplain"
        val closedFence = "**before\n```\ncode\n```\nplain"
        assertEquals(blankLine, StreamingText.closePartialMarkdown(blankLine))
        assertEquals(closedFence, StreamingText.closePartialMarkdown(closedFence))
        assertEquals("**one\ntwo**", StreamingText.closePartialMarkdown("**one\ntwo"))
    }

    @Test
    fun plainStatusRemovesInlineFormattingAndCollapsesWhitespace() {
        assertEquals("Checking bold italic also italic old and code", StreamingText.plainStatus("Checking **bold** *italic* _also italic_ ~~old~~ and `code`"))
        assertEquals("Reading the logs now", StreamingText.plainStatus("  **Reading**\t the\n logs  now\r\n"))
        assertEquals("Checking logs", StreamingText.plainStatus("**Checking** [logs](https://example.test/logs)"))
        assertEquals("Checking", StreamingText.plainStatus("**Checking"))
        assertEquals("", StreamingText.plainStatus("\n\t "))
    }

    @Test
    fun plainStatusStripsHeadingListTaskAndNestedQuoteMarkers() {
        assertEquals("Title First Second Done Pending Quoted Deep", StreamingText.plainStatus("### Title\n- First\n2) Second\n* [x] Done\n+ [ ] Pending\n> Quoted\n> > - [X] Deep"))
        assertEquals("1.2 is a version #hashtag snake_case 2 * 3 ~approx", StreamingText.plainStatus("1.2 is a version\n#hashtag snake_case 2 * 3 ~approx"))
    }

    @Test
    fun plainStatusKeepsLinkLabelsAndOmitsDestinationsAndTitles() {
        assertEquals("Read the logs then docs", StreamingText.plainStatus("Read [the **logs**](https://example.test/a_(b) \"title )\") then [docs][reference]"))
        assertEquals("Screenshot", StreamingText.plainStatus("![Screenshot](https://example.test/image.png)"))
        assertEquals("logs", StreamingText.plainStatus("[logs](https://example.test/unfinished"))
        assertEquals("nested [label]", StreamingText.plainStatus("[nested [label]](https://example.test)"))
        assertEquals("[ordinary brackets]", StreamingText.plainStatus("[ordinary brackets]"))
    }

    @Test
    fun plainStatusDropsTableDelimitersAndSeparatesCells() {
        val table = "| Name | Status |\n| :--- | ---: |\n| Worker | **Ready** |\n| Queue | `Empty` |"
        assertEquals("Name Status Worker Ready Queue Empty", StreamingText.plainStatus(table))
        assertEquals("Name Status Worker Ready", StreamingText.plainStatus("Name | Status\n--- | :---:\nWorker | Ready"))
        assertEquals("", StreamingText.plainStatus("| --- | :---: |"))
    }

    @Test
    fun plainStatusRespectsLiteralEscapesAndCodeContents() {
        assertEquals("**literal** _literal_ ~literal~ |", StreamingText.plainStatus("\\*\\*literal\\*\\* \\_literal\\_ \\~literal\\~ \\|"))
        assertEquals("**literal** snake_case a|b", StreamingText.plainStatus("`**literal**` `snake_case` `a|b`"))
        assertEquals("a ` tick", StreamingText.plainStatus("``a ` tick``"))
        assertEquals("**partial code", StreamingText.plainStatus("`**partial code"))
    }

    @Test
    fun plainStatusDropsFenceDecorationButKeepsLiteralCode() {
        assertEquals("Working # heading **literal** x|y Done", StreamingText.plainStatus("**Working**\n```kotlin\n# heading **literal**\nx|y\n```\n**Done**"))
        assertEquals("**literal**", StreamingText.plainStatus("~~~text\n**literal**"))
    }

    @Test
    fun carriageReturnAndCrLfPreserveParagraphAndFenceContext() {
        for (newline in listOf("\n", "\r", "\r\n")) {
            assertEquals(
                "**one${newline}two**",
                StreamingText.closePartialMarkdown("**one${newline}two"),
            )
            val fenced = "```$newline**literal `$newline```$newline**outside"
            assertEquals("$fenced**", StreamingText.closePartialMarkdown(fenced))
            assertEquals(
                "Title Item **literal** Done",
                StreamingText.plainStatus("# Title$newline- Item$newline```$newline**literal**$newline```$newline**Done**"),
            )
        }
    }
}
