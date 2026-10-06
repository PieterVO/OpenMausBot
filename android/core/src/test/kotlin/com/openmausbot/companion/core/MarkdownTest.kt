package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarkdownTest {
    @Test
    fun plainTextIsOneParagraph() {
        assertEquals(listOf(MarkdownBlock.Paragraph("just a reply")), Markdown.blocks("just a reply"))
    }

    @Test
    fun emptyInputProducesNothing() {
        assertEquals(emptyList(), Markdown.blocks(""))
        assertEquals(emptyList(), Markdown.blocks("\n\n  \n"))
    }

    @Test
    fun softBreaksBecomeSpaces() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph("one line and its continuation")),
            Markdown.blocks("one line\nand its continuation"),
        )
    }

    @Test
    fun blankLineSeparatesParagraphs() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph("first"), MarkdownBlock.Paragraph("second")),
            Markdown.blocks("first\n\nsecond"),
        )
    }

    @Test
    fun headingLevels() {
        assertEquals(listOf(MarkdownBlock.Heading(1, "Title")), Markdown.blocks("# Title"))
        assertEquals(listOf(MarkdownBlock.Heading(3, "Deeper")), Markdown.blocks("### Deeper"))
    }

    @Test
    fun hashWithoutSpaceIsNotAHeading() {
        assertEquals(listOf(MarkdownBlock.Paragraph("#hashtag")), Markdown.blocks("#hashtag"))
        assertEquals(listOf(MarkdownBlock.Paragraph("####### seven")), Markdown.blocks("####### seven"))
    }

    @Test
    fun bulletMarkers() {
        assertEquals(
            listOf(
                MarkdownBlock.Bullet(0, "one"),
                MarkdownBlock.Bullet(0, "two"),
                MarkdownBlock.Bullet(0, "three"),
            ),
            Markdown.blocks("- one\n* two\n+ three"),
        )
    }

    @Test
    fun nestedBulletsCountIndent() {
        assertEquals(
            listOf(
                MarkdownBlock.Bullet(0, "top"),
                MarkdownBlock.Bullet(1, "nested"),
                MarkdownBlock.Bullet(2, "deeper"),
            ),
            Markdown.blocks("- top\n  - nested\n    - deeper"),
        )
    }

    @Test
    fun orderedListsKeepTheirNumbers() {
        assertEquals(
            listOf(
                MarkdownBlock.Ordered(0, 1, "first"),
                MarkdownBlock.Ordered(0, 2, "second"),
                MarkdownBlock.Ordered(0, 10, "tenth"),
            ),
            Markdown.blocks("1. first\n2. second\n10) tenth"),
        )
    }

    @Test
    fun numberWithoutDelimiterIsProse() {
        assertEquals(listOf(MarkdownBlock.Paragraph("2026 was the year")), Markdown.blocks("2026 was the year"))
        assertEquals(listOf(MarkdownBlock.Paragraph("3.14 is pi")), Markdown.blocks("3.14 is pi"))
    }

    @Test
    fun inlineSyntaxSurvivesTheSplit() {
        assertEquals(
            listOf(MarkdownBlock.Bullet(0, "**bold** and `code` and [link](https://x.test)")),
            Markdown.blocks("- **bold** and `code` and [link](https://x.test)"),
        )
    }

    @Test
    fun fencedCodeKeepsLanguageAndWhitespace() {
        assertEquals(
            listOf(MarkdownBlock.Code("swift", "let x = 1\n    indented")),
            Markdown.blocks("```swift\nlet x = 1\n    indented\n```"),
        )
    }

    @Test
    fun fenceWithoutLanguage() {
        assertEquals(listOf(MarkdownBlock.Code(null, "plain")), Markdown.blocks("```\nplain\n```"))
    }

    @Test
    fun unclosedFenceRunsToTheEnd() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph("here:"), MarkdownBlock.Code("py", "print(1)")),
            Markdown.blocks("here:\n```py\nprint(1)"),
        )
    }

    @Test
    fun fenceContentIsNotReparsed() {
        assertEquals(
            listOf(MarkdownBlock.Code(null, "# not a heading\n- not a bullet")),
            Markdown.blocks("```\n# not a heading\n- not a bullet\n```"),
        )
    }

    @Test
    fun quote() {
        assertEquals(listOf(MarkdownBlock.Quote("quoted")), Markdown.blocks("> quoted"))
    }

    @Test
    fun horizontalRules() {
        assertEquals(listOf(MarkdownBlock.Rule), Markdown.blocks("---"))
        assertEquals(listOf(MarkdownBlock.Rule), Markdown.blocks("***"))
        assertEquals(listOf(MarkdownBlock.Rule), Markdown.blocks("___"))
    }

    @Test
    fun ruleNeedsThreeAndNothingElse() {
        assertEquals(listOf(MarkdownBlock.Paragraph("--")), Markdown.blocks("--"))
        assertEquals(listOf(MarkdownBlock.Paragraph("-- dashes --")), Markdown.blocks("-- dashes --"))
    }

    @Test
    fun crlfDoesNotCreatePhantomBlocks() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph("one two")),
            Markdown.blocks("one\r\ntwo"),
        )
        assertEquals(
            listOf(MarkdownBlock.Code(null, "one\ntwo")),
            Markdown.blocks("```\r\none\r\ntwo\r\n```"),
        )
    }

    @Test
    fun partialInputAlwaysRendersSomething() {
        listOf("#", "# ", "# Head", "- ", "- it", "**bo", "```", "```sw\nlet", "[link](htt").forEach {
            assertTrue(Markdown.blocks(it).isNotEmpty(), "dropped everything for $it")
        }
    }

    @Test
    fun noPrefixOfAReplyLosesCharacters() {
        val reply = "# Result\n\nRan **two** checks:\n\n- `pnpm test` passed\n- `pnpm lint` passed\n\n```sh\npnpm test\n```\n\n> nothing else to report"
        for (length in 1..reply.length) {
            val partial = reply.take(length)
            val rendered = Markdown.blocks(partial).joinToString(separator = "", transform = ::text)
            val sent = partial.filter { !it.isWhitespace() && it !in "#->`*_" }
            val shown = rendered.filter { !it.isWhitespace() && it !in "#->`*_" }
            assertEquals(sent, shown, "lost content at $length characters")
        }
    }

    @Test
    fun incrementalMatchesEveryPrefixOfBlockAndTableFixtures() {
        val sources = listOf(
            "# Result\n\nRan **two** checks:\n\n- `test` passed\n- [x] done\n1. [ ] next\n\n> quoted\n---\nTail",
            "Title\n===\nSubtitle\n---\n- item\n  continuation\n  - child\n    continuation\n\nAfter",
            "Lead\n| A | B |\n| :-- | --: |\n| 1 | 2 |\n| 3 | 4 | 5 |\n| 6 |\nAfter | prose\n\nEnd",
            "A | B\n- | -\n1 | 2\n3 | 4\n\nEnd",
            "| A | B | |---|---| | | 2 | | 3 | 4 |\n| 5 | 6 |\n\nEnd",
            "| `a|b` | c |\n| - | - |\n| x \\| y | z |\n| `partial\n\nAfter",
            "- intro\n  | A | B |\n  | --- | --- |\n  | 1 | 2 |\n- next\n  > note\n  | A | B |\n  | - | - |\n\nEnd",
            "before\n```kotlin\n# literal\n\n| A | B |\n| - | - |\n**code `\n```\n\nAfter",
            "~~~text\nliteral\n\n~~~\n# Heading\nTail",
            "```unclosed\n- literal\n\n| A | B |\n| - | - |\n**tail",
            "````lang\n```\n\n**still fenced\n````\nTail",
            "**before\n```lang\nliteral\n``` not a closing fence\n\n**still literal",
            "    ```indented\nliteral\n```\n**outside",
            "**one\n## Heading\n- `two\n\n**next `code",
            "\\**literal\n**word \\\n\n`code \\`\n\n👨‍👩‍👧‍👦 e\u0301 🇺🇸",
            "\r\n# Heading\r\n\r\n| A | B |\r\n| - | - |\r\n| 1 | 2 |\r\n\r\nTail",
            "# Heading\r\r- parent\r  - child\r  continuation\r\r```lang\rcode\r```\rTail",
        )
        for (source in sources) assertIncrementalPrefixes(source)
    }

    @Test
    fun incrementalMatchesEveryPrefixOfAdjacentSyntaxCombinations() {
        val lines = listOf(
            "", "plain", "**open", "`code", "# Heading", "> quote", "---", "===",
            "- item", "  - nested", "1. [x] task", "  continuation",
            "| A | B |", "| - | - |", "| 1 | 2 | 3 |", "| `open | code |",
            "| A | B | |---|---| | 1 | 2 |", "```", "~~~", "``` not a closer",
        )
        for (first in lines) for (second in lines) {
            assertIncrementalPrefixes("# Settled\n\n$first\n$second\nTail")
        }
    }

    @Test
    fun incrementalResetsForReplacementRewindAndEmptySource() {
        for (closing in listOf(false, true)) {
            val parser = Markdown.Incremental()
            for (source in listOf(
                "# First\n\n- item\nTail",
                "# First\n\n- item\nTail appended",
                "# Replacement\n\n| A | B |\n| - | - |\n| 1 | 2 |",
                "# Replacement\n\n| A | B |\n| - | - |\n| changed | row |",
                "# Replacement",
                "",
                "```new\n**literal",
                "```new\n**literal\n```\n\nDone",
            )) {
                val expected = Markdown.blocks(if (closing) StreamingText.closePartialMarkdown(source) else source)
                assertEquals(expected, parser.blocks(source, closePartial = closing), source)
            }
            val source = "# Fixed\n\n**mutable tail"
            parser.blocks(source, closePartial = closing)
            for (end in source.length downTo 0) {
                val prefix = source.take(end)
                assertEquals(
                    Markdown.blocks(if (closing) StreamingText.closePartialMarkdown(prefix) else prefix),
                    parser.blocks(source, end, closePartial = closing),
                    "rewind $end, closing=$closing",
                )
            }
        }
    }

    @Test
    fun incrementalCanSwitchBetweenRawAndTemporarilyClosedMarkdown() {
        val source = "**open\n# Heading\n- item\nTail"
        val parser = Markdown.Incremental()
        for (closing in listOf(false, true, false, true)) {
            assertEquals(
                Markdown.blocks(if (closing) StreamingText.closePartialMarkdown(source) else source),
                parser.blocks(source, closePartial = closing),
                "closing=$closing",
            )
        }
    }

    @Test
    fun incrementalReusesSettledBlocksAndUnchangedFrameResults() {
        val parser = Markdown.Incremental()
        val source = "# Heading\n\n| A | B |\n| - | - |\n| 1 | 2 |\n\n- parent\n  - child\n\n**Mutable tail"
        val tailStart = source.indexOf("**Mutable")
        val first = parser.blocks(source, tailStart, closePartial = true)
        assertTrue(parser.settledSourceLength > 0)
        for (end in tailStart + 1..source.length) {
            val next = parser.blocks(source, end, closePartial = true)
            first.forEachIndexed { index, block -> assertSame(block, next[index], "settled block $index at $end") }
            assertSame(next, parser.blocks(source, end, closePartial = true), "unchanged frame at $end")
        }
        assertEquals(Markdown.blocks(source), parser.blocks(source, closePartial = false))
    }

    private fun assertIncrementalPrefixes(source: String) {
        val raw = Markdown.Incremental()
        val closed = Markdown.Incremental()
        val batches = Markdown.Incremental()
        for (end in 0..source.length) {
            val prefix = source.take(end)
            assertEquals(Markdown.blocks(prefix), raw.blocks(source, end), "raw prefix $end: $source")
            val expected = Markdown.blocks(StreamingText.closePartialMarkdown(prefix))
            assertEquals(expected, closed.blocks(source, end, closePartial = true), "closed prefix $end: $source")
            assertEquals(expected, batches.blocks(prefix, closePartial = true), "appended batch $end: $source")
        }
    }

    private fun text(block: MarkdownBlock): String = when (block) {
        is MarkdownBlock.Paragraph -> block.text
        is MarkdownBlock.Bullet -> block.text
        is MarkdownBlock.Ordered -> block.number.toString() + block.text
        is MarkdownBlock.Task -> (block.number?.toString() ?: "") + block.text
        is MarkdownBlock.Heading -> block.text
        is MarkdownBlock.Code -> block.language.orEmpty() + block.text
        is MarkdownBlock.Quote -> block.text
        is MarkdownBlock.Table -> (block.headers + block.rows.flatten()).joinToString("")
        MarkdownBlock.Rule -> ""
    }
}
