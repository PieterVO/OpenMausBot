package com.openmausbot.companion.ui

import androidx.compose.ui.res.stringResource

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Brush
import com.openmausbot.companion.core.MarkdownTableAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Reasoning
import com.openmausbot.companion.core.TranscriptCard
import kotlinx.coroutines.launch

/** Native patch and table surfaces; copy and row-major accessibility stay unchanged. */

/** What the clipboard shows a copied card came from. */
private const val CARD_CLIP_LABEL = "OpenMausMobile card"

/** Added and removed, in the app's own palette rather than Tailwind's. */
private val DiffAdded = Color(MausPalette.argb("green"))
private val DiffRemoved = Color(MausPalette.argb("red"))
private val DiffHunk = Color(MausPalette.argb("cyan"))

/**
 * A patch, with its head visible and all of it on the clipboard.
 *
 * The preview stops at 80 lines because a 4,000-line patch inside a scrolling
 * transcript is a scroll the reader cannot get out of. Copy Diff never stops:
 * the clipboard is where the patch is actually used.
 */
@Composable
fun DiffCard(card: TranscriptCard.Diff, modifier: Modifier = Modifier) {
    var showingDiff by rememberSaveable(card.text) { mutableStateOf(true) }
    var showingAll by rememberSaveable(card.text) { mutableStateOf(false) }
    val copy = rememberCopy()
    // `GitPRDiffCardView.swift` fires `Haptics.selection()` on both of these.
    val haptics = rememberHaptics()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(chatTint.theirs, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = card.filename,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .background(secondaryTint.copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    // Two glyphs and two numbers say "three added, one removed"
                    // to anyone who can see them; this says it to everyone else.
                    .localizedSemantics(
                        mergeDescendants = true,
                        contentDescription = {
                            stringResource(R.string.mobile_a11y_diff_stats, card.additions, card.deletions)
                        },
                    ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "+${card.additions}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = DiffAdded,
                )
                Text(
                    text = "-${card.deletions}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = DiffRemoved,
                )
            }
        }

        if (card.text.isNotEmpty()) {
            Disclosure(
                expanded = showingDiff,
                label = if (showingDiff) "Hide Diff" else "View Diff",
                onToggle = {
                    haptics.play(HapticCue.SELECT)
                    showingDiff = !showingDiff
                },
            )

            if (showingDiff) {
                val lines = remember(card, showingAll) { card.visibleLines(showingAll) }
                // Horizontal scroll rather than wrapping, for the same reason the
                // markdown code block does it: indentation is most of what a
                // patch is saying, and a wrapped `-` line stops looking removed.
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                secondaryTint.copy(alpha = 0.10f),
                                RoundedCornerShape(10.dp),
                            )
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        lines.forEach { DiffLine(it) }
                    }
                }

                if (card.isTruncated) {
                    val label = if (showingAll) {
                        stringResource(
                            R.string.mobile_diff_show_first_lines,
                            TranscriptCard.Diff.PREVIEW_LINES,
                        )
                    } else {
                        stringResource(
                            R.string.mobile_diff_show_all_lines,
                            card.lines.size,
                        )
                    }
                    TextButton(
                        onClick = {
                            haptics.play(HapticCue.SELECT)
                            showingAll = !showingAll
                        },
                        // Compose has no "hint" the way UIAccessibility does, so
                        // iOS's hint is folded into the name: the reader must not
                        // be left thinking Copy Diff copies the preview.
                        modifier = Modifier.localizedSemantics(contentDescription = {
                            stringResource(R.string.mobile_a11y_diff_copy_hint, label)
                        }),
                    ) {
                        Text(label, fontSize = 13.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = secondaryTint.copy(alpha = 0.2f))

        TextButton(onClick = { copy(card.text) }) {
            Text(stringResource(R.string.mobile_copy_diff_18f2296a), fontSize = 13.sp, color = chatTint.ink)
        }
    }
}

@Composable
private fun DiffLine(line: String) {
    val added = line.startsWith("+") && !line.startsWith("+++")
    val removed = line.startsWith("-") && !line.startsWith("---")
    val hunk = line.startsWith("@@") || line.startsWith("diff")
    Text(
        text = line,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        softWrap = false,
        color = when {
            added -> DiffAdded
            removed -> DiffRemoved
            hunk -> DiffHunk
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .background(
                when {
                    added -> DiffAdded.copy(alpha = 0.14f)
                    removed -> DiffRemoved.copy(alpha = 0.14f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * A table the reader can actually read across, and take away as CSV.
 *
 * Read across in the literal sense: heading row first, then one row at a time,
 * left to right, one row at a time. iOS no longer builds this card (issue
 * 1707). This used to be the transpose of a row of columns, a `Row` of
 * self-measuring `Column`s, and on an API 34 emulator TalkBack duly announced
 * *"LANGUAGE, Python, Java, Rust, YEAR, 1991, 1995, 2010"*: every value of the
 * first column before the second, so `Python`↔`1991` was not a row at all for
 * a reader who cannot see the screen. The composition decided that order, and
 * it built it the wrong way round.
 *
 * Composition order is not the whole of the answer:
 * `AndroidComposeViewAccessibilityDelegateCompat` runs its own traversal pass
 * over the merged tree, groups nodes by geometry and publishes
 * `traversalBefore`/`traversalAfter` relations from that grouping. Here the two
 * stages agree, because the children are emitted row-major and then placed
 * row-major, so neither has anything left to reorder. That agreement is the
 * reason the fix holds, not an excuse to skip the check: `TableReadingOrderTest`
 * pins the tree, and TalkBack on a device is what pins the reading.
 *
 * Holding both — the row order *and* columns that still line up — is why the
 * grid is its own [Layout] rather than a `Row` or a `Column` of anything. The
 * children arrive row-major, which is the order the reader gets; the measure
 * pass then widens each column to its widest cell, which is the alignment iOS
 * gives up by handing every cell the same `minWidth` and letting them drift.
 * The rules are measured after the cells, against the whole grid width including
 * its column gutters. A horizontal scroller supplies unbounded width, so the
 * grid must give those continuous separators their width explicitly.
 */
@Composable
fun DataTableCard(card: TranscriptCard.Table, modifier: Modifier = Modifier) {
    val copy = rememberCopy()
    val copyCsv = { copy(card.csv()) }
    val copyLabel = localizedMobileCopy("Copy table as CSV")
    val scroll = rememberScrollState()
    val fadeSurface = chatTint.inset.compositeOver(chatTint.theirs).compositeOver(MaterialTheme.colorScheme.surface)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("data-table")
            .clip(RoundedCornerShape(12.dp))
            .background(chatTint.inset)
            .semantics {
                customActions = listOf(CustomAccessibilityAction(copyLabel) {
                    copyCsv()
                    true
                })
            },
    ) {
        // Reserve the overlaid copy target inside the final column, not a separate rail.
        SelectionContainer {
            Box(Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                if (scroll.canScrollForward) drawRect(
                    Brush.horizontalGradient(if (rtl) listOf(fadeSurface, Color.Transparent) else listOf(Color.Transparent, fadeSurface),
                        startX = if (rtl) 0f else size.width - 24.dp.toPx(), endX = if (rtl) 24.dp.toPx() else size.width),
                    topLeft = androidx.compose.ui.geometry.Offset(if (rtl) 0f else size.width - 24.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(24.dp.toPx(), size.height),
                )
            }) {
                DataGrid(headers = card.headers, rows = card.rows, alignments = card.alignments,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(scroll))
            }
        }
        IconButton(
            onClick = copyCsv,
            modifier = Modifier.align(Alignment.TopEnd).size(MIN_TOUCH_TARGET),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tool_copy),
                contentDescription = copyLabel,
                modifier = Modifier.size(18.dp),
                tint = chatTint.ink,
            )
        }
    }
}

/**
 * The cells, in the order they are read: headings, then row by row.
 *
 * A semantic-free header fill comes first, followed by all cells row-major,
 * then one continuous separator before each body row. Only the cells carry
 * meaning; the fill and rules get their final sizes from
 * [tableGridMeasurePolicy] after the columns and row heights are known.
 */
@Composable
private fun DataGrid(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    alignments: List<MarkdownTableAlignment> = emptyList(),
) {
    if (headers.isEmpty()) return
    val separator = MaterialTheme.colorScheme.outlineVariant
    Layout(
        modifier = modifier,
        content = {
            // One additional inset over the container is subtly stronger, not
            // a doubled overlay compounded over an already tinted surface.
            Box(Modifier.background(chatTint.inset))
            headers.forEachIndexed { column, header ->
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                    modifier = Modifier
                        .padding(end = if (column == headers.lastIndex) MIN_TOUCH_TARGET else 0.dp)
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
            rows.forEach { row ->
                headers.indices.forEach { column ->
                    MarkdownInlineText(
                        // A short row is padded, not dropped: iOS reads
                        // `colIdx < row.count ? row[colIdx] : stringResource(R.string.mobile_text_da39a3ee)` for the same
                        // reason, and a missing cell that took no space would
                        // slide the rest of the row under the wrong heading.
                        text = row.getOrElse(column) { "" },
                        tail = false,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier
                            .padding(end = if (column == headers.lastIndex) MIN_TOUCH_TARGET else 0.dp)
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
            }
            repeat(rows.size) {
                HorizontalDivider(thickness = 0.5.dp, color = separator)
            }
        },
        measurePolicy = tableGridMeasurePolicy(columnCount = headers.size, rowCount = rows.size, alignments = alignments),
    )
}

/** iOS asks every cell for `minWidth: 65`; this is that, in whole dp. */
private val TABLE_MIN_COLUMN_WIDTH = 64.dp

/** The gutter between two columns, and the leading between two rows. */
private val TABLE_COLUMN_GAP = 18.dp
private val TABLE_ROW_GAP = 4.dp

/**
 * Column widths from the content, row positions from the columns.
 *
 * Expects a header fill, [columnCount] headings, the body cells row-major, and
 * [rowCount] separators. Cells are measured without width constraints so a
 * horizontal scroller never wraps them. Each column takes its widest cell,
 * floored at [minColumnWidth]; the fill and separators then take the resolved
 * full grid width, including every gutter. The fill takes the measured header
 * height, so larger text never escapes its background.
 *
 * Internal so tests can measure the production policy's actual columns, fill
 * and continuous separators without adding test-only nodes to the card.
 */
internal fun tableGridMeasurePolicy(
    columnCount: Int,
    rowCount: Int,
    minColumnWidth: Dp = TABLE_MIN_COLUMN_WIDTH,
    columnGap: Dp = TABLE_COLUMN_GAP,
    rowGap: Dp = TABLE_ROW_GAP,
    alignments: List<MarkdownTableAlignment> = emptyList(),
): MeasurePolicy = MeasurePolicy { measurables, constraints ->
    require(columnCount > 0) { "a table with no columns has nothing to lay out" }
    val unbounded = Constraints()
    val headings = List(columnCount) { measurables[1 + it].measure(unbounded) }
    val cells = List(rowCount * columnCount) {
        measurables[1 + columnCount + it].measure(unbounded)
    }

    val floor = minColumnWidth.roundToPx()
    val widths = IntArray(columnCount) { column ->
        var widest = maxOf(floor, headings[column].width)
        for (row in 0 until rowCount) {
            widest = maxOf(widest, cells[row * columnCount + column].width)
        }
        widest
    }

    val gutter = columnGap.roundToPx()
    val leading = rowGap.roundToPx()
    val x = IntArray(columnCount)
    var pen = 0
    for (column in 0 until columnCount) {
        x[column] = pen
        pen += widths[column] + gutter
    }
    val width = constraints.constrainWidth(pen - gutter)
    val headingHeight = maxOf(MIN_TOUCH_TARGET.roundToPx(), headings.maxOf { it.height })
    val headerFill = measurables[0].measure(Constraints.fixed(width, headingHeight))
    val rules = List(rowCount) {
        measurables[1 + (rowCount + 1) * columnCount + it].measure(Constraints.fixedWidth(width))
    }
    val ruleY = IntArray(rowCount)
    val y = IntArray(rowCount)
    var baseline = headingHeight
    for (row in 0 until rowCount) {
        baseline += leading
        ruleY[row] = baseline
        baseline += rules[row].height + leading
        y[row] = baseline
        baseline += (0 until columnCount).maxOf { cells[row * columnCount + it].height }
    }

    fun alignedX(column: Int, childWidth: Int): Int = x[column] + when (alignments.getOrNull(column)) {
        MarkdownTableAlignment.TRAILING -> widths[column] - childWidth
        MarkdownTableAlignment.CENTER -> (widths[column] - childWidth) / 2
        else -> 0
    }
    layout(width, constraints.constrainHeight(baseline)) {
        headerFill.placeRelative(0, 0)
        headings.forEachIndexed { column, heading -> heading.placeRelative(alignedX(column, heading.width), (headingHeight - heading.height) / 2) }
        rules.forEachIndexed { row, rule -> rule.placeRelative(0, ruleY[row]) }
        cells.forEachIndexed { index, cell ->
            cell.placeRelative(alignedX(index % columnCount, cell.width), y[index / columnCount])
        }
    }
}


/** Hide/View, announced as the disclosure it is. */
@Composable
private fun Disclosure(expanded: Boolean, label: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "Collapse" else "Expand",
                onClick = onToggle,
            )
            .localizedSemantics(stateDescription = {
                stringResource(
                    if (expanded) R.string.mobile_a11y_expanded else R.string.mobile_a11y_collapsed,
                )
            }),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) {
                Icons.Filled.KeyboardArrowDown
            } else {
                Icons.AutoMirrored.Filled.KeyboardArrowRight
            },
            contentDescription = null,
            tint = secondaryTint,
            modifier = Modifier.size(18.dp),
        )
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = secondaryTint)
    }
}

/**
 * A copy, as the two halves iOS gives it: the clipboard write, and the tick that
 * says it happened.
 *
 * `PlatformBridge.copyToPasteboard` ends every copy with `Haptics.selection()`,
 * unconditionally — so Copy Diff and Android's Copy CSV are confirmed by feel
 * and not only by a toast. iOS markdown tables do not offer Copy CSV (issue 1707).
 * neither platform shows. A copy is the one action on these cards with no
 * visible result at all: the button does not move, nothing opens, and the only
 * evidence is in a clipboard the reader has to leave the app to see. That is
 * exactly the interaction that needs the confirmation.
 *
 * Both cards go through this one object, so neither route can lose the tick
 * without the other losing it too — and [invoke] plays exactly one cue per call,
 * after the write is dispatched, in the order the Swift does it.
 */
internal class CardClipboard(
    private val write: (String) -> Unit,
    private val haptics: Haptics,
) {
    operator fun invoke(text: String) {
        write(text)
        haptics.play(HapticCue.SELECT)
    }
}

/** One clipboard write and one tick, reused by both cards. */
@Composable
private fun rememberCopy(): CardClipboard {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    return remember(clipboard, scope, haptics) {
        CardClipboard(
            write = { text ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CARD_CLIP_LABEL, text)))
                }
                Unit
            },
            haptics = haptics,
        )
    }
}
