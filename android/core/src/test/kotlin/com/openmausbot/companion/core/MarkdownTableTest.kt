package com.openmausbot.companion.core

import com.openmausbot.companion.core.MarkdownTableAlignment.CENTER
import com.openmausbot.companion.core.MarkdownTableAlignment.LEADING
import com.openmausbot.companion.core.MarkdownTableAlignment.TRAILING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The table and task rules from iOS MarkdownTableTests, including streaming prefixes. */
class MarkdownTableTest {
    @Test
    fun proseAroundAMultilineTable() {
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("Lead-in prose"),
                MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2")), listOf(LEADING, LEADING)),
                MarkdownBlock.Paragraph("After"),
            ),
            Markdown.blocks("Lead-in prose\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\nAfter"),
        )
    }

    @Test
    fun weldedTableAfterProse() {
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("Lead-in prose"),
                MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2")), listOf(LEADING, LEADING)),
                MarkdownBlock.Paragraph("After"),
            ),
            Markdown.blocks("Lead-in prose\n| A | B | |---|---| | 1 | 2 |\n\nAfter"),
        )
    }

    @Test
    fun weldedEmptyCellsAndShortTail() {
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("", "2"), listOf("", "4")), listOf(LEADING, LEADING))),
            Markdown.blocks("| A | B | |---|---| | | 2 | | | 4 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "")), listOf(LEADING, LEADING))),
            Markdown.blocks("| A | B | |---|---| | 1 |"),
        )
    }

    @Test
    fun multilineExtraCellGrowsAndWeldedExtraCellWraps() {
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B", ""), listOf(listOf("1", "2", "3")), listOf(LEADING, LEADING, LEADING))),
            Markdown.blocks("| A | B |\n| --- | --- |\n| 1 | 2 | 3 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3", "")), listOf(LEADING, LEADING))),
            Markdown.blocks("| A | B | |---|---| | 1 | 2 | 3 |"),
        )
    }

    @Test
    fun delimiterWidthAndCenter() {
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B", "C"), listOf(listOf("1", "2", "3")), listOf(LEADING, LEADING, LEADING))),
            Markdown.blocks("| A | B | C |\n| --- | --- |\n| 1 | 2 | 3 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B", "C"), listOf(listOf("1", "2", "3")), listOf(LEADING, TRAILING, LEADING))),
            Markdown.blocks("| A | B | C |\n| :--- | ---: |\n| 1 | 2 | 3 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2")), listOf(LEADING, LEADING))),
            Markdown.blocks("| A | B |\n| --- | --- | --- | --- |\n| 1 | 2 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A"), listOf(listOf("mid")), listOf(CENTER))),
            Markdown.blocks("| A |\n| :---: |\n| mid |"),
        )
    }

    @Test
    fun escapesAndCodeSpansDoNotSplitCells() {
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("a | b", "c", "d"), listOf(listOf("1", "2", "3")), listOf(LEADING, LEADING, LEADING))),
            Markdown.blocks("| a \\| b | c | d |\n| --- | --- |\n| 1 | 2 | 3 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("`a|b`", "c"), emptyList(), listOf(LEADING, LEADING))),
            Markdown.blocks("| `a|b` | c |\n| --- | --- |"),
        )
    }

    @Test
    fun optionalOuterPipesAndOneHyphen() {
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B", "C"), listOf(listOf("1", "2", "3")), listOf(LEADING, LEADING, LEADING))),
            Markdown.blocks("A | B | C\n--- | ---\n1 | 2 | 3"),
        )
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2")), listOf(LEADING, LEADING))),
            Markdown.blocks("| A | B\n--- | ---\n1 | 2 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2")), listOf(LEADING, LEADING))),
            Markdown.blocks("| A | B |\n| - | - |\n| 1 | 2 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A"), listOf(listOf("1")), listOf(LEADING))),
            Markdown.blocks("| A |\n| --- |\n| 1 |"),
        )
    }

    @Test
    fun whatIsNotATable() {
        assertEquals(listOf(MarkdownBlock.Paragraph("Pros | Cons"), MarkdownBlock.Rule), Markdown.blocks("Pros | Cons\n---"))
        assertEquals(listOf(MarkdownBlock.Paragraph("Pros | Cons -")), Markdown.blocks("Pros | Cons\n-"))
        for (source in listOf(
            "Example: | A | B | |---|---| | 1 | 2 |",
            "| A | B | |---|---| then prose",
            "| Step | --- | Done |",
            "| A \\| B | C | |---|---| | 1 | 2 |",
        )) {
            assertEquals(listOf(MarkdownBlock.Paragraph(source)), Markdown.blocks(source))
        }
        assertEquals(listOf(MarkdownBlock.Code(null, "- [x] literal")), Markdown.blocks("```\n- [x] literal\n```"))
        assertEquals(listOf(MarkdownBlock.Code(null, "- [x] literal")), Markdown.blocks("```\n- [x] literal"))
        assertEquals(
            listOf(
                MarkdownBlock.Table(listOf("A", "B"), emptyList(), listOf(LEADING, LEADING)),
                MarkdownBlock.Paragraph("| 1 | 2 |"),
            ),
            Markdown.blocks("| A | B |\n| --- | --- |\n\n| 1 | 2 |"),
        )
        assertEquals(
            listOf(
                MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2")), listOf(LEADING, LEADING)),
                MarkdownBlock.Paragraph("After"),
            ),
            Markdown.blocks("| A | B |\n| --- | --- |\n| 1 | 2 |\nAfter"),
        )
        assertEquals(
            listOf(
                MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2")), listOf(LEADING, LEADING)),
                MarkdownBlock.Task(0, null, true, "done"),
            ),
            Markdown.blocks("| A | B |\n| --- | --- |\n| 1 | 2 |\n- [x] done"),
        )
        assertEquals(
            listOf(MarkdownBlock.Paragraph("| A | B | | --- | --- | | 1 | 2 |")),
            Markdown.blocks("    | A | B |\n    | --- | --- |\n    | 1 | 2 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Bullet(0, "intro"), MarkdownBlock.Paragraph("| A | B | | --- | --- | | 1 | 2 |")),
            Markdown.blocks("- intro\n  | A | B |\n  | --- | --- |\n  | 1 | 2 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Bullet(0, "parent"), MarkdownBlock.Bullet(1, "child"), MarkdownBlock.Paragraph("| A | B | | - | - |")),
            Markdown.blocks("- parent\n  - child\n  | A | B |\n  | - | - |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Quote("| A | B |"), MarkdownBlock.Quote("| --- | --- |"), MarkdownBlock.Quote("| 1 | 2 |")),
            Markdown.blocks("> | A | B |\n> | --- | --- |\n> | 1 | 2 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Task(0, null, true, "A | B"), MarkdownBlock.Paragraph("--- | ---")),
            Markdown.blocks("- [x] A | B\n--- | ---"),
        )
    }

    @Test
    fun tasks() {
        assertEquals(listOf(MarkdownBlock.Task(0, null, false, "open")), Markdown.blocks("- [ ] open"))
        assertEquals(listOf(MarkdownBlock.Task(0, null, true, "done")), Markdown.blocks("* [x] done"))
        assertEquals(listOf(MarkdownBlock.Task(0, null, true, "done")), Markdown.blocks("+ [X] done"))
        assertEquals(listOf(MarkdownBlock.Task(0, 1, false, "open")), Markdown.blocks("1. [ ] open"))
        assertEquals(listOf(MarkdownBlock.Task(0, 1, false, "open")), Markdown.blocks("1) [ ] open"))
        assertEquals(listOf(MarkdownBlock.Task(1, null, true, "nested")), Markdown.blocks("  - [x] nested"))
        assertEquals(
            listOf(MarkdownBlock.Task(0, null, false, "parent"), MarkdownBlock.Task(1, null, true, "child")),
            Markdown.blocks("- [ ] parent\n  - [x] child"),
        )
        assertEquals(listOf(MarkdownBlock.Task(0, null, false, "")), Markdown.blocks("- [ ]"))
        assertEquals(listOf(MarkdownBlock.Bullet(0, "[x]no-space")), Markdown.blocks("- [x]no-space"))
        assertEquals(listOf(MarkdownBlock.Bullet(0, "[  ] no")), Markdown.blocks("- [  ] no"))
    }

    @Test
    fun aListItemOrTabIsNotADelimiter() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph("| A | B |"), MarkdownBlock.Bullet(0, "| --- | --- |"), MarkdownBlock.Paragraph("| 1 | 2 |")),
            Markdown.blocks("| A | B |\n- | --- | --- |\n| 1 | 2 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Paragraph("| A | B | | --- | --- | | 1 | 2 |")),
            Markdown.blocks("| A | B |\n\t| --- | --- |\n| 1 | 2 |"),
        )
        assertEquals(
            listOf(MarkdownBlock.Bullet(0, "intro"), MarkdownBlock.Quote("note"), MarkdownBlock.Paragraph("| A | B | | --- | --- |")),
            Markdown.blocks("- intro\n  > note\n  | A | B |\n  | --- | --- |"),
        )
    }

    @Test
    fun shortWeldedDelimiterIsPadded() {
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2")), listOf(LEADING, LEADING))),
            Markdown.blocks("| A | B | |---| | 1 | 2 |"),
        )
    }

    @Test
    fun bracketSpaceIsNotACheckboxAndAQuotedTaskStaysAQuote() {
        assertEquals(listOf(MarkdownBlock.Bullet(0, "[ x ] no")), Markdown.blocks("- [ x ] no"))
        assertEquals(listOf(MarkdownBlock.Quote("- [x] done")), Markdown.blocks("> - [x] done"))
    }

    @Test
    fun tokensSurviveEveryPrefix() {
        val sources = mapOf(
            "Lead-in prose\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\nAfter" to listOf("Lead-in prose", "After", "1", "2"),
            "| -5% | 12:30 | \$3 | x |\n| --- | --- | --- | --- |\n| a | b | c | d |" to listOf("-5%", "12:30", "\$3", "x", "a"),
            "- [x] next" to listOf("next"),
        )
        for ((source, tokens) in sources) {
            for (length in 1..source.length) {
                val partial = source.take(length)
                val shown = Markdown.blocks(partial).joinToString("\n", transform = ::payload)
                for (token in tokens.filter { it in partial }) {
                    assertTrue(token in shown, "lost $token from $partial")
                }
            }
        }
    }

    private fun payload(block: MarkdownBlock): String = when (block) {
        is MarkdownBlock.Paragraph -> block.text
        is MarkdownBlock.Bullet -> block.text
        is MarkdownBlock.Quote -> block.text
        is MarkdownBlock.Heading -> block.text
        is MarkdownBlock.Ordered -> "${block.number}. " + block.text
        is MarkdownBlock.Task -> (block.number?.let { "$it. " } ?: "") + block.text
        is MarkdownBlock.Code -> block.text
        MarkdownBlock.Rule -> ""
        is MarkdownBlock.Table -> (block.headers + block.rows.flatten()).joinToString("\n")
    }

    @Test
    fun oneToThreeSpaceIndentIsATable() {
        for (indent in 1..3) {
            val spaces = " ".repeat(indent)
            assertEquals(
                listOf(MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2")), listOf(LEADING, LEADING))),
                Markdown.blocks("$spaces| A | B |\n$spaces| --- | --- |\n$spaces| 1 | 2 |"),
            )
        }
    }

    @Test
    fun specialCharactersSurviveInCells() {
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("-5%", "12:30", "\$3", "x"), listOf(listOf("a", "b", "c", "d")), List(4) { LEADING })),
            Markdown.blocks("| -5% | 12:30 | \$3 | x |\n| --- | --- | --- | --- |\n| a | b | c | d |"),
        )
    }

    @Test
    fun growingColumnsPadEarlierAndShorterRows() {
        assertEquals(
            listOf(
                MarkdownBlock.Table(
                    listOf("A", "B", "", ""),
                    listOf(listOf("1", "", "", ""), listOf("2", "3", "4", "5"), listOf("6", "7", "", "")),
                    listOf(CENTER, TRAILING, LEADING, LEADING),
                ),
            ),
            Markdown.blocks("| A | B |\n| :-: | -: |\n| 1 |\n| 2 | 3 | 4 | 5 |\n| 6 | 7 |"),
        )
    }

    @Test
    fun weldedTablesRetainAlignmentAndConsumeFollowingBodyRows() {
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3", "")), listOf(CENTER, TRAILING))),
            Markdown.blocks("| A | B | |:-:|-:| | 1 | 2 |\n| 3 |"),
        )
    }

    @Test
    fun unclosedCodeSpansDoNotBecomeTableHeadersOrBodyRows() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph("| `A | B | | --- | --- | | 1 | 2 |")),
            Markdown.blocks("| `A | B |\n| --- | --- |\n| 1 | 2 |"),
        )
        assertEquals(
            listOf(
                MarkdownBlock.Table(listOf("A", "B"), emptyList(), listOf(LEADING, LEADING)),
                MarkdownBlock.Paragraph("| `1 | 2 |"),
            ),
            Markdown.blocks("| A | B |\n| --- | --- |\n| `1 | 2 |"),
        )
        val weldedCode = "| `A|B` | C | |---|---| | 1 | 2 |"
        assertEquals(listOf(MarkdownBlock.Paragraph(weldedCode)), Markdown.blocks(weldedCode))
    }

    @Test
    fun codeSpanAndBackslashContentSurvivesInBodyCells() {
        assertEquals(
            listOf(
                MarkdownBlock.Table(
                    listOf("expr", "path"),
                    listOf(listOf("`a|b`", "C:\\Users\\n"), listOf("a | b", "tail\\")),
                    listOf(LEADING, LEADING),
                ),
            ),
            Markdown.blocks("| expr | path |\n| --- | --- |\n| `a|b` | C:\\Users\\n |\na \\| b | tail\\"),
        )
    }

    @Test
    fun taskWhitespaceAndOrderedMetadataArePreserved() {
        assertEquals(listOf(MarkdownBlock.Task(0, null, true, "done")), Markdown.blocks("- [x]\t  done"))
        assertEquals(listOf(MarkdownBlock.Task(2, 12, true, "nested")), Markdown.blocks("    12) [X] nested"))
        assertEquals(listOf(MarkdownBlock.Task(4, null, false, "deep")), Markdown.blocks("            - [ ] deep"))
        assertEquals(listOf(MarkdownBlock.Task(0, null, true, "")), Markdown.blocks("- [x]"))
        assertEquals(listOf(MarkdownBlock.Bullet(0, "[y] no")), Markdown.blocks("- [y] no"))
        assertEquals(listOf(MarkdownBlock.Ordered(0, 2, "[x]no-space")), Markdown.blocks("2. [x]no-space"))
        assertEquals(listOf(MarkdownBlock.Paragraph("[x] not a list")), Markdown.blocks("[x] not a list"))
    }

    @Test
    fun wholeMessageAndEmbeddedTablesHaveIdenticalDataAndCsv() {
        val sources = listOf(
            "| A | B |\n| :-: | -: |\n| 1 | 2 |",
            "A | B\n-- | -:\n1 | 2 | 3\n4 |",
            "| `a|b` | c |\n| --- | --- |",
            "| A | B | |:-:|-:| | | 2 | | 3 |",
            "| expr | note |\n| --- | --- |\n| a \\| b | he said \"hi\", then left |",
        )
        for (source in sources) {
            val whole = Markdown.blocks(source).single() as MarkdownBlock.Table
            val embedded = Markdown.blocks("Before\n$source\n\nAfter").filterIsInstance<MarkdownBlock.Table>().single()
            val card = assertNotNull(TranscriptCards.table(source))
            assertEquals(whole, embedded)
            assertEquals(whole.headers, card.headers)
            assertEquals(whole.rows, card.rows)
            assertEquals(whole.alignments, card.alignments)
            assertEquals(Csv.of(listOf(whole.headers) + whole.rows), card.csv())
            assertNull(TranscriptCards.table("Before\n$source\n\nAfter"))
        }
    }

    @Test
    fun oldTableConstructorsKeepLeadingAlignmentAndRowMajorCsvData() {
        val headers = listOf("language", "year")
        val rows = listOf(listOf("Python", "1991"), listOf("Java"))
        val block = MarkdownBlock.Table(headers, rows)
        val card = TranscriptCard.Table(headers, rows)
        assertEquals(listOf(LEADING, LEADING), block.alignments)
        assertEquals(listOf(LEADING, LEADING), card.alignments)
        assertEquals(headers, card.headers)
        assertEquals(rows, card.rows)
        assertEquals("language,year\nPython,1991\nJava", card.csv())
    }
}
