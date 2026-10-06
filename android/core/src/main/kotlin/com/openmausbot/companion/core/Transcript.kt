package com.openmausbot.companion.core

import java.text.BreakIterator
import java.util.Locale

/**
 * Whole-message diff and table cards, and the reasoning suffix. Tables share
 * the Markdown block parser so the same cells, alignment and padding appear
 * inside a reply and in a whole-message card. CSV always includes every cell.
 *
 * The gates are deliberately all-or-nothing. A reply that *contains* a patch or
 * a table is still a reply: turning it into a card would hide the sentences
 * around it, and a reader who cannot see the sentences cannot see the answer. So
 * a card is only offered when the message is nothing else — which is also why
 * these are functions over a string rather than a scan for a fragment.
 */
sealed interface TranscriptCard {
    /**
     * A patch. [text] is the whole thing, always — the preview is a reading
     * convenience and Copy Diff must never be a subset of what arrived.
     */
    data class Diff(
        val filename: String,
        val text: String,
        val lines: List<String>,
        val additions: Int,
        val deletions: Int,
    ) : TranscriptCard {
        /** Long patches open on the head; the rest is one tap away. */
        val isTruncated: Boolean get() = lines.size > PREVIEW_LINES

        fun visibleLines(showingAll: Boolean): List<String> =
            if (showingAll) lines else lines.take(PREVIEW_LINES)

        companion object {
            /** iOS: `lines.prefix(showAllLines ? lines.count : 80)`. */
            const val PREVIEW_LINES: Int = 80
        }
    }

    /** A whole-message Markdown table, with row-major data for reading and copy. */
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<MarkdownTableAlignment> = List(headers.size) { MarkdownTableAlignment.LEADING },
    ) : TranscriptCard {
        /** Header row first, exactly as iOS builds `[columns] + rows`. */
        fun csv(): String = Csv.of(listOf(headers) + rows)
    }
}

object TranscriptCards {
    /**
     * The card a transcript row should draw instead of a paragraph, or null.
     *
     * Your own messages are never cards: the same split the desktop makes for
     * markdown applies here, because a patch you pasted is a patch you meant to
     * show, not one to fold away behind a disclosure.
     */
    fun of(message: Message): TranscriptCard? {
        if (message.role == Message.Role.USER) return null
        val text = message.text ?: return null
        return diff(text) ?: table(text)
    }

    /**
     * A closed ```` ```diff ```` fence, or a message that opens with a git
     * header. Nothing else: an unclosed fence is a reply still being written,
     * and prose that mentions `diff --git` is prose.
     */
    fun diff(source: String): TranscriptCard.Diff? {
        val text = source.trim()
        val patch = when {
            text.startsWith(DIFF_FENCE) && text.endsWith(FENCE) ->
                text.drop(DIFF_FENCE.length).dropLast(FENCE.length).trim()

            text.startsWith(GIT_HEADER) -> text

            else -> return null
        }
        val lines = patch.split("\n")
        // `+++`/`---` are the file headers, not a line added or removed.
        val additions = lines.count { it.startsWith("+") && !it.startsWith("+++") }
        val deletions = lines.count { it.startsWith("-") && !it.startsWith("---") }
        return TranscriptCard.Diff(
            filename = filename(lines.firstOrNull().orEmpty()),
            text = patch,
            lines = lines,
            additions = additions,
            deletions = deletions,
        )
    }

    /**
     * The name in the card's header: the last word of the patch's first line
     * with `b/` taken out of it, which turns `diff --git a/x.kt b/x.kt` into
     * `x.kt` and leaves a bare `--- a/x.kt` alone. A first line with no words
     * at all leaves the card named after what it is.
     */
    private fun filename(firstLine: String): String =
        firstLine.split(' ').lastOrNull { it.isNotEmpty() }?.replace("b/", "") ?: GIT_PATCH

    /**
     * A table only when the entire reply is one table block. Prose, another
     * block, or a blank line separating the body keeps it a normal reply; the
     * Markdown renderer can still show any embedded table without losing text.
     */
    fun table(source: String): TranscriptCard.Table? {
        val table = Markdown.blocks(source).singleOrNull() as? MarkdownBlock.Table ?: return null
        return TranscriptCard.Table(table.headers, table.rows, table.alignments)
    }

    private const val DIFF_FENCE = "```diff"
    private const val FENCE = "```"
    private const val GIT_HEADER = "diff --git "
    private const val GIT_PATCH = "Git patch"
}

/**
 * RFC 4180 quoting. Android keeps this helper; the iOS SQL card is gone and
 * the phone now draws a markdown table (issue 1707). A field is left alone
 * unless it carries a separator, a quote or a
 * line break, and a quote inside a quoted field is doubled. Getting this wrong
 * does not look wrong — it looks like a spreadsheet with the columns shifted.
 */
object Csv {
    fun of(rows: List<List<String>>): String =
        rows.joinToString("\n") { row -> row.joinToString(",", transform = ::field) }

    fun field(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}

/**
 * The last [count] characters the way Swift counts them.
 *
 * `String.suffix(n)` there walks `Character`s — extended grapheme clusters — so
 * it can never begin a tail halfway through one, and an emoji costs it one, not
 * two. `takeLast` counts UTF-16 code units, which both shortens the tail
 * wherever the text is not ASCII and can start it on the low half of a surrogate
 * pair or on a combining mark whose base it just dropped.
 *
 * Java's `BreakIterator` implements an older UAX #29 than the ICU on a device,
 * so the two agree on surrogate pairs and combining marks but not necessarily on
 * flags or ZWJ sequences. That is a difference in how many clusters a rare
 * emoji costs, never in leaving one broken — which is what the cut is for.
 */
fun String.takeLastCharacters(count: Int): String {
    // A string shorter in code units than [count] cannot hold more than [count]
    // clusters, so there is nothing to cut.
    if (length <= count) return this
    val clusters = BreakIterator.getCharacterInstance(Locale.ROOT)
    clusters.setText(this)
    var start = clusters.last()
    var taken = 0
    while (taken < count) {
        val previous = clusters.previous()
        if (previous == BreakIterator.DONE) break
        start = previous
        taken++
    }
    return substring(start)
}

/**
 * What the thought chamber is allowed to show.
 *
 * Reasoning runs to thousands of words and the part worth reading is always the
 * end, so the chamber holds the last [VISIBLE_CHARACTERS] of it. The cut lands
 * mid-word wherever it lands; that is why the chamber shows plain lines and not
 * markdown, and why blank lines are dropped rather than numbered. It does not
 * land mid-character: see [takeLastCharacters].
 */
object Reasoning {
    /** iOS: `String(reasoning.suffix(2_000))` — 2,000 `Character`s. */
    const val VISIBLE_CHARACTERS: Int = 2_000

    fun visible(reasoning: String): String = reasoning.takeLastCharacters(VISIBLE_CHARACTERS)

    /** The numbered lines the chamber lists once it is open. */
    fun steps(reasoning: String): List<String> =
        visible(reasoning).split("\n").filter(String::isNotBlank)
}
