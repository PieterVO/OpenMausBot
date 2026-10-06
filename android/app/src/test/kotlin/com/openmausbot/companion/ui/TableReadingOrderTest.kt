package com.openmausbot.companion.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.unit.width
import com.openmausbot.companion.core.MarkdownTableAlignment
import com.openmausbot.companion.core.TranscriptCard
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The order the table card offers a screen reader, read off the merged tree.
 *
 * This is the one thing about [DataTableCard] no pure function can answer. The
 * card was once a `Row` of `Column`s, and TalkBack on an API 34 emulator read it out as
 * *"LANGUAGE, Python, Java, Rust, YEAR, 1991, 1995, 2010"* — the whole of the
 * first column before the second, so no row survived the reading. A test that
 * asserted over `card.rows` would have been green through all of that; the
 * defect lived in the semantics tree, so the assertion has to be over the
 * semantics tree, which is why this file mounts the real composition under
 * Robolectric rather than reasoning about the model.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TableReadingOrderTest {

    @get:Rule
    val compose = createComposeRule()

    private val languages = TranscriptCard.Table(
        headers = listOf("language", "year"),
        rows = listOf(
            listOf("Python", "1991"),
            listOf("Java", "1995"),
            listOf("Rust", "2010"),
        ),
    )

    private val quotedTable = TranscriptCard.Table(
        headers = listOf("name", "notes"),
        rows = listOf(
            listOf("Coast, west", "Bring \"tea\""),
            listOf("Café", "two\nlines"),
        ),
    )

    private val quotedCsv = "name,notes\n\"Coast, west\",\"Bring \"\"tea\"\"\"\nCafé,\"two\nlines\""

    /**
     * Everything the merged tree carries, in the order the tree carries it.
     *
     * That child order is the order the composition built, which is exactly the
     * thing that was wrong before, and so the thing worth asserting on. It is
     * not by itself the sequence TalkBack speaks: the platform delegate takes
     * this tree, groups nodes by geometry and publishes
     * `traversalBefore`/`traversalAfter` relations over it. The two agree for
     * this card — the corrected tree is row-major and the corrected layout is
     * row-major — and transposing the grid makes this walk return the exact
     * column-major sequence that was measured on an API 34 device, which is why
     * the walk is evidence at all. A pass with TalkBack on a device stays the
     * final word.
     */
    private fun spoken(node: SemanticsNode): List<String> = buildList {
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { add(it) }
        node.children.forEach { addAll(spoken(it)) }
    }

    private fun spokenCard(card: TranscriptCard.Table): List<String> {
        compose.setContent { CompanionTheme(darkTheme = false) { DataTableCard(card) } }
        return spoken(compose.onRoot().fetchSemanticsNode())
    }

    @Test
    fun `the tree reads by row, headings first, the way iOS does`() {
        assertEquals(
            listOf(
                "language", "year",
                "Python", "1991",
                "Java", "1995",
                "Rust", "2010",
                "Copy table as CSV",
            ),
            spokenCard(languages),
        )
    }

    @Test
    fun `a short row is read as an empty cell, not as a shifted one`() {
        val ragged = TranscriptCard.Table(
            headers = listOf("language", "year"),
            rows = listOf(listOf("Python", "1991"), listOf("Java")),
        )
        assertEquals(
            listOf(
                "language", "year",
                "Python", "1991",
                "Java", "",
                "Copy table as CSV",
            ),
            spokenCard(ragged),
        )
    }

    @Test
    fun `the table has a stable container and only a top trailing 48dp copy icon`() {
        compose.setContent { CompanionTheme(darkTheme = false) { DataTableCard(languages) } }

        val table = compose.onNodeWithTag("data-table").assertExists()
        compose.onNodeWithText("DATA TABLE").assertDoesNotExist()
        compose.onNodeWithText("3 rows").assertDoesNotExist()
        compose.onNodeWithText("Copy CSV").assertDoesNotExist()

        val button = compose.onNodeWithContentDescription("Copy table as CSV")
        val buttonBounds = button.getBoundsInRoot()
        val tableBounds = table.getBoundsInRoot()
        assertEquals(48f, buttonBounds.width.value, 0.5f, "copy target width")
        assertEquals(48f, buttonBounds.height.value, 0.5f, "copy target height")
        assertEquals(tableBounds.top.value, buttonBounds.top.value, 0.5f, "copy target top")
        assertEquals(tableBounds.right.value, buttonBounds.right.value, 0.5f, "copy target trailing edge")
        assertEquals(
            listOf("Copy table as CSV"),
            table.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label },
        )
    }

    @Test
    fun `the copy icon writes the exact CSV with commas quotes and newlines`() {
        compose.setContent { CompanionTheme(darkTheme = false) { DataTableCard(quotedTable) } }

        compose.onNodeWithContentDescription("Copy table as CSV").performClick()
        assertClipboard(quotedCsv)
    }

    @Test
    fun `the TalkBack custom action writes the same exact CSV`() {
        compose.setContent { CompanionTheme(darkTheme = false) { DataTableCard(quotedTable) } }

        compose.onNodeWithTag("data-table").performCustomAccessibilityActionWithLabel("Copy table as CSV")
        assertClipboard(quotedCsv)
    }

    private fun assertClipboard(expected: String) {
        compose.runOnIdle {
            val clipboard = RuntimeEnvironment.getApplication()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = requireNotNull(clipboard.primaryClip)
            assertEquals(1, clip.itemCount)
            assertEquals(expected, clip.getItemAt(0).text.toString())
        }
    }

    @Test
    fun `a column still lines up, and a row still shares a top`() {
        compose.setContent { CompanionTheme(darkTheme = false) { DataTableCard(languages) } }

        fun left(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.left
        fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

        val first = left("language")
        listOf("Python", "Java", "Rust").forEach { assertEquals(first, left(it), 0.5f, it) }
        val second = left("year")
        listOf("1991", "1995", "2010").forEach { assertEquals(second, left(it), 0.5f, it) }
        assertTrue(second > first, "the second column has to sit to the right of the first")

        assertEquals(top("Python"), top("1991"), 0.5f, "Python/1991")
        assertEquals(top("Java"), top("1995"), 0.5f, "Java/1995")
        assertEquals(top("Rust"), top("2010"), 0.5f, "Rust/2010")
        assertTrue(top("Java") > top("Python"), "row two sits below row one")
    }

    @Test
    fun `delimiter alignment still places trailing and centered cells in their columns`() {
        val aligned = TranscriptCard.Table(
            headers = listOf("name", "count", "status"),
            rows = listOf(listOf("Python", "1", "up"), listOf("Java", "123456", "ready")),
            alignments = listOf(MarkdownTableAlignment.LEADING, MarkdownTableAlignment.TRAILING, MarkdownTableAlignment.CENTER),
        )
        compose.setContent { CompanionTheme(darkTheme = false) { DataTableCard(aligned) } }

        fun bounds(text: String) = compose.onNodeWithText(text).getBoundsInRoot()
        assertEquals(bounds("count").right.value, bounds("1").right.value, 0.5f, "trailing short cell")
        assertEquals(bounds("count").right.value, bounds("123456").right.value, 0.5f, "trailing wide cell")
        fun center(text: String) = bounds(text).let { (it.left.value + it.right.value) / 2f }
        assertEquals(center("status"), center("up"), 0.5f, "centered short cell")
        assertEquals(center("status"), center("ready"), 0.5f, "centered wider cell")
    }

    @Test
    fun `a table wider than the card still scrolls sideways`() {
        val wide = TranscriptCard.Table(
            headers = List(8) { "column number $it" },
            rows = listOf(List(8) { "a fairly long value $it" }),
        )
        compose.setContent { CompanionTheme(darkTheme = false) { DataTableCard(wide) } }
        val scroller = compose
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .fetchSemanticsNode()
        val range = scroller.config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue(range.maxValue() > 0f, "the grid overflows, so there is somewhere to scroll to")
    }

    /**
     * The production policy measures one rule per row boundary, not one per
     * column. These test-owned nodes expose its actual full-width constraints
     * and row positions without adding decorative test scaffolding to the UI.
     */
    @Test
    fun `continuous separators span every column and gutter after the tallest cell`() {
        compose.setContent {
            Layout(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                content = {
                    Box(Modifier.testTag("header-fill"))
                    Box(Modifier.testTag("heading-0").size(width = 120.dp, height = 60.dp))
                    Box(Modifier.testTag("heading-1").size(width = 30.dp, height = 12.dp))
                    Box(Modifier.testTag("heading-2").size(width = 20.dp, height = 12.dp))
                    Box(Modifier.testTag("cell-0-0").size(width = 40.dp, height = 10.dp))
                    Box(Modifier.testTag("cell-0-1").size(width = 200.dp, height = 30.dp))
                    Box(Modifier.testTag("cell-0-2").size(width = 20.dp, height = 10.dp))
                    repeat(3) { column ->
                        Box(Modifier.testTag("cell-1-$column").size(width = 20.dp, height = 10.dp))
                    }
                    repeat(2) { Box(Modifier.testTag("rule-$it").height(1.dp)) }
                },
                measurePolicy = tableGridMeasurePolicy(columnCount = 3, rowCount = 2),
            )
        }

        fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

        // 120.dp heading + 200.dp body cell + 64.dp floor + two 18.dp gutters.
        assertEquals(420f, bounds("header-fill").width.value, 0.5f, "full header width")
        assertEquals(60f, bounds("header-fill").height.value, 0.5f, "measured tall header")
        repeat(2) { row ->
            assertEquals(420f, bounds("rule-$row").width.value, 0.5f, "continuous rule $row")
            assertEquals(0f, bounds("rule-$row").left.value, 0.5f, "rule $row origin")
        }

        // The same content-derived columns still align, with real gutters.
        listOf(0f, 138f, 356f).forEachIndexed { column, x ->
            assertEquals(x, bounds("heading-$column").left.value, 0.5f, "heading $column origin")
            repeat(2) { row ->
                assertEquals(x, bounds("cell-$row-$column").left.value, 0.5f, "cell $row/$column origin")
            }
        }
        assertEquals(64f, bounds("rule-0").top.value, 0.5f, "header boundary")
        assertEquals(69f, bounds("cell-0-0").top.value, 0.5f, "first row top")
        assertEquals(103f, bounds("rule-1").top.value, 0.5f, "boundary after tallest first-row cell")
        assertEquals(108f, bounds("cell-1-0").top.value, 0.5f, "second row top")
    }

    @Test
    fun `a narrow grid stretches its continuous rule to the viewport`() {
        compose.setContent {
            Layout(
                modifier = Modifier.width(300.dp).horizontalScroll(rememberScrollState()),
                content = {
                    Box(Modifier.testTag("header-fill"))
                    repeat(4) { Box(Modifier.size(20.dp)) }
                    Box(Modifier.testTag("rule").height(1.dp))
                },
                measurePolicy = tableGridMeasurePolicy(columnCount = 2, rowCount = 1),
            )
        }

        assertEquals(300f, compose.onNodeWithTag("header-fill").getBoundsInRoot().width.value, 0.5f, "header viewport width")
        assertEquals(300f, compose.onNodeWithTag("rule").getBoundsInRoot().width.value, 0.5f, "rule viewport width")
    }

    private fun assertEquals(expected: Float, actual: Float, delta: Float, what: String) {
        assertTrue(abs(expected - actual) <= delta, "$what: expected $expected, was $actual")
    }
}
