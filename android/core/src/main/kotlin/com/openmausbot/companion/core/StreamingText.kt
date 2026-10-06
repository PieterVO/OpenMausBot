package com.openmausbot.companion.core

import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/** Pure reveal math and source presentation; callers cache [graphemeEnds] until the source changes. */
object StreamingText {
    private val grapheme = Pattern.compile("\\X")
    private val leadingMarker = Pattern.compile(
        "(?:#{1,6}(?:[ \\t]+|$)|>[ \\t]*|[-+*][ \\t]+|[0-9]{1,9}[.)][ \\t]+|\\[[ xX]\\](?:[ \\t]+|$))",
    )
    private val tableDelimiter = Pattern.compile(
        "\\|?[ \\t]*:?-{3,}:?[ \\t]*(?:\\|[ \\t]*:?-{3,}:?[ \\t]*)*\\|?",
    )

    /** Positions and rates are UTF-16 units, with fractions retained between frames. */
    fun step(
        position: Double,
        receivedLength: Int,
        elapsedSeconds: Double,
        arrival: Boolean = false,
    ): Double {
        val length = receivedLength.coerceAtLeast(0).toDouble()
        val current = position.coerceIn(0.0, length)
        val elapsed = elapsedSeconds.coerceAtLeast(0.0)
        if (elapsed == 0.0 || current == length) return current
        val rate = if (arrival) max(90.0, length / 0.9) else max(60.0, (length - current) / 0.35)
        val next = min(length, current + rate * elapsed)
        // A rounding-sized arrival tail must not extend the 0.9-second reveal by another frame.
        return if (arrival && length - next <= length * 1e-12) length else next
    }

    /**
     * Extended grapheme endpoints, built once per received source on JVM 17 / Android's ICU regex.
     * Incomplete surrogate, joiner and single regional-indicator suffixes wait for the next batch.
     * A settled caller may show a complete lone joiner/indicator, but never a dangling high surrogate.
     */
    fun graphemeEnds(source: String): IntArray {
        val limit = source.length - if (source.lastOrNull()?.isHighSurrogate() == true) 1 else 0
        if (limit == 0) return IntArray(0)
        val ends = IntArray(limit)
        val matcher = grapheme.matcher(source).region(0, limit)
        var count = 0
        while (matcher.find()) {
            val end = matcher.end()
            if (end == limit) {
                val last = source.codePointBefore(end)
                if (last == 0x200D) break
                if (last in 0x1F1E6..0x1F1FF && end - matcher.start() == 2) break
            }
            ends[count++] = end
        }
        return if (count == ends.size) ends else ends.copyOf(count)
    }

    /** Largest complete cached endpoint at or before the fractional cursor; no source scan. */
    fun prefixEnd(ends: IntArray, position: Double): Int {
        var low = 0
        var high = ends.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (ends[middle].toDouble() <= position) low = middle + 1 else high = middle
        }
        return if (low == 0) 0 else ends[low - 1]
    }
    /**
     * The paced UTF-16 prefix, with pipe tables revealed a whole row at a time.
     * The header waits for its complete delimiter; an open stream's last line
     * waits for a newline even after the cursor has caught up to received text.
     */
    fun revealedPrefix(text: String, count: Int, final: Boolean): String {
        if (final && count >= text.length) return text
        val cut = count.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', cut - 1) + 1
        if (!isRevealTableLine(text, lineStart)) return text.substring(0, cut)

        var tableStart = lineStart
        while (tableStart > 0) {
            val previousStart = text.lastIndexOf('\n', tableStart - 2) + 1
            if (!isRevealTableLine(text, previousStart)) break
            tableStart = previousStart
        }
        val headerEnd = completeRevealLineEnd(text, tableStart, final)
            ?: return text.substring(0, tableStart)
        if (headerEnd == text.length) return text.substring(0, tableStart)
        val delimiterStart = headerEnd + 1
        val delimiterEnd = completeRevealLineEnd(text, delimiterStart, final)
            ?: return text.substring(0, tableStart)
        if (!isRevealDelimiter(text, delimiterStart, delimiterEnd)) return text.substring(0, tableStart)
        if (lineStart <= delimiterStart) return text.substring(0, delimiterEnd)

        val rowEnd = completeRevealLineEnd(text, lineStart, final)
            ?: return text.substring(0, lineStart)
        return text.substring(0, rowEnd)
    }

    private fun isRevealTableLine(text: String, start: Int): Boolean {
        var index = start
        while (index < text.length && text[index] == ' ') index++
        return index < text.length && text[index] == '|'
    }

    private fun completeRevealLineEnd(text: String, start: Int, final: Boolean): Int? {
        val newline = text.indexOf('\n', start)
        return if (newline >= 0) newline else if (final) text.length else null
    }

    private fun isRevealDelimiter(text: String, from: Int, until: Int): Boolean {
        var start = from
        var end = until
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        var dash = false
        for (index in start until end) {
            when (text[index]) {
                '-' -> dash = true
                '|', ':', ' ' -> Unit
                else -> return false
            }
        }
        return dash
    }


    /** Closes only active strong/code spans, inside-out; fenced code and literal escapes stay intact. */
    fun closePartialMarkdown(source: String): String {
        val pending = ArrayList<Delimiter>(2)
        var fence: Fence? = null
        var lineStart = 0
        while (lineStart < source.length) {
            val lineEnd = lineEnd(source, lineStart)
            val candidate = fenceAt(source, lineStart, lineEnd)
            val activeFence = fence
            if (activeFence != null) {
                if (candidate != null && closesFence(source, candidate, activeFence, lineEnd)) fence = null
            } else if (candidate != null && opensFence(source, candidate, lineEnd)) {
                fence = candidate
                pending.clear()
            } else if (onlyWhitespace(source, lineStart, lineEnd)) {
                // Inline formatting cannot span a Markdown paragraph boundary.
                pending.clear()
            } else {
                var index = lineStart
                while (index < lineEnd) {
                    val character = source[index]
                    val activeCode = pending.lastOrNull()?.takeIf { it.marker == '`' }
                    if (activeCode != null) {
                        if (character == '`') {
                            val end = runEnd(source, index, lineEnd)
                            if (end - index == activeCode.width) pending.removeAt(pending.lastIndex)
                            index = end
                        } else {
                            index++
                        }
                    } else when (character) {
                        '\\' -> index = min(index + 2, lineEnd)
                        '`' -> {
                            val end = runEnd(source, index, lineEnd)
                            pending += Delimiter('`', end - index, end)
                            index = end
                        }
                        '*' -> {
                            val end = runEnd(source, index, lineEnd)
                            var remaining = end - index
                            if (canClose(source, index, end)) {
                                while (remaining >= 2 && pending.lastOrNull()?.marker == '*') {
                                    pending.removeAt(pending.lastIndex)
                                    remaining -= 2
                                }
                            }
                            if (remaining >= 2 && canOpen(source, index, end)) {
                                pending += Delimiter('*', 2, end)
                            }
                            index = end
                        }
                        else -> index++
                    }
                }
            }
            lineStart = nextLineStart(source, lineEnd)
        }
        if (fence != null || pending.isEmpty()) return source
        // Closing a content-free code opener or an escaped delimiter would manufacture Markdown.
        if (pending.any { onlyWhitespace(source, it.contentStart, source.length) }) return source
        var insertion = source.length
        if (pending.last().marker != '`') {
            while (insertion > 0 && source[insertion - 1].isWhitespace()) insertion--
            var index = insertion - 1
            while (index >= 0 && source[index] == '\\') index--
            if ((insertion - 1 - index) % 2 != 0) return source
            index = insertion - 1
            while (index >= 0 && source[index] == '*') index--
            if (index < insertion - 1 && (index < 0 || source[index].isWhitespace())) return source
        }
        val result = StringBuilder(source.length + pending.sumOf { it.width }).append(source, 0, insertion)
        for (index in pending.indices.reversed()) {
            val delimiter = pending[index]
            repeat(delimiter.width) { result.append(delimiter.marker) }
        }
        result.append(source, insertion, source.length)
        return result.toString()
    }

    /** A compact readable status, without block/inline decoration or link destinations. */
    fun plainStatus(source: String): String {
        val result = StringBuilder(source.length)
        val markerMatcher = leadingMarker.matcher(source)
        val delimiterMatcher = tableDelimiter.matcher(source)
        var fence: Fence? = null
        var lineStart = 0
        while (lineStart < source.length) {
            val lineEnd = lineEnd(source, lineStart)
            val candidate = fenceAt(source, lineStart, lineEnd)
            val activeFence = fence
            if (activeFence != null) {
                if (candidate != null && closesFence(source, candidate, activeFence, lineEnd)) {
                    fence = null
                } else {
                    for (index in lineStart until lineEnd) appendStatus(result, source[index])
                }
            } else if (candidate != null && opensFence(source, candidate, lineEnd)) {
                fence = candidate
            } else {
                var start = lineStart
                while (start < lineEnd && source[start].isWhitespace()) start++
                var end = lineEnd
                while (end > start && source[end - 1].isWhitespace()) end--
                if (!delimiterMatcher.region(start, end).matches()) {
                    while (markerMatcher.region(start, end).lookingAt()) start = markerMatcher.end()
                    appendPlain(source, start, end, result)
                }
            }
            appendStatus(result, ' ')
            lineStart = nextLineStart(source, lineEnd)
        }
        if (result.lastOrNull() == ' ') result.deleteCharAt(result.lastIndex)
        return result.toString()
    }

    private data class Delimiter(val marker: Char, val width: Int, val contentStart: Int)
    private data class Fence(val marker: Char, val width: Int, val after: Int)

    private fun lineEnd(source: String, start: Int): Int {
        var index = start
        while (index < source.length && source[index] != '\n' && source[index] != '\r') index++
        return index
    }

    private fun nextLineStart(source: String, end: Int): Int =
        if (end + 1 < source.length && source[end] == '\r' && source[end + 1] == '\n') end + 2 else end + 1

    private fun fenceAt(source: String, start: Int, end: Int): Fence? {
        var index = start
        while (index < end && source[index] == ' ' && index - start < 4) index++
        if (index - start > 3 || index == end || source[index] !in "`~") return null
        val after = runEnd(source, index, end)
        return if (after - index >= 3) Fence(source[index], after - index, after) else null
    }

    private fun opensFence(source: String, fence: Fence, end: Int): Boolean {
        if (fence.marker == '`') {
            for (index in fence.after until end) if (source[index] == '`') return false
        }
        return true
    }

    private fun closesFence(source: String, candidate: Fence, active: Fence, end: Int): Boolean =
        candidate.marker == active.marker && candidate.width >= active.width &&
            onlyWhitespace(source, candidate.after, end)

    private fun onlyWhitespace(source: String, start: Int, end: Int): Boolean {
        for (index in start until end) if (!source[index].isWhitespace()) return false
        return true
    }

    private fun runEnd(source: String, start: Int, end: Int): Int {
        var index = start + 1
        while (index < end && source[index] == source[start]) index++
        return index
    }

    private fun isPunctuation(codePoint: Int): Boolean {
        if (codePoint in 0x21..0x7E && !Character.isLetterOrDigit(codePoint)) return true
        return when (Character.getType(codePoint)) {
            Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(), -> true
            else -> false
        }
    }

    private fun isWhitespace(codePoint: Int): Boolean =
        Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)

    private fun canOpen(source: String, start: Int, end: Int): Boolean {
        if (end == source.length || isWhitespace(source.codePointAt(end))) return false
        return !isPunctuation(source.codePointAt(end)) || start == 0 ||
            isWhitespace(source.codePointBefore(start)) || isPunctuation(source.codePointBefore(start))
    }

    private fun canClose(source: String, start: Int, end: Int): Boolean {
        if (start == 0 || isWhitespace(source.codePointBefore(start))) return false
        return !isPunctuation(source.codePointBefore(start)) || end == source.length ||
            isWhitespace(source.codePointAt(end)) || isPunctuation(source.codePointAt(end))
    }

    private fun appendStatus(result: StringBuilder, character: Char) {
        if (character.isWhitespace()) {
            if (result.isNotEmpty() && result.last() != ' ') result.append(' ')
        } else {
            result.append(character)
        }
    }

    private fun appendPlain(source: String, start: Int, end: Int, result: StringBuilder) {
        var index = start
        while (index < end) {
            val character = source[index]
            if (character == '\\' && index + 1 < end && isPunctuation(source[index + 1].code)) {
                appendStatus(result, source[index + 1])
                index += 2
                continue
            }
            if (character == '`') {
                val after = runEnd(source, index, end)
                var close = after
                var closeEnd = end
                while (close < end) {
                    if (source[close] == '`') {
                        val next = runEnd(source, close, end)
                        if (next - close == after - index) {
                            closeEnd = next
                            break
                        }
                        close = next
                    } else {
                        close++
                    }
                }
                for (position in after until close) appendStatus(result, source[position])
                index = closeEnd
                continue
            }
            val bracket = if (character == '!' && index + 1 < end && source[index + 1] == '[') index + 1 else index
            if (source[bracket] == '[') {
                val labelEnd = matchingEnd(source, bracket, end, '[', ']')
                val destination = labelEnd + 1
                if (labelEnd >= 0 && destination < end && source[destination] in "([") {
                    appendPlain(source, bracket + 1, labelEnd, result)
                    val opener = source[destination]
                    val destinationEnd = matchingEnd(source, destination, end, opener, if (opener == '(') ')' else ']')
                    index = if (destinationEnd < 0) end else destinationEnd + 1
                    continue
                }
            }
            if (character in "*_~") {
                val after = runEnd(source, index, end)
                val left = canOpen(source, index, after)
                val right = canClose(source, index, after)
                val strip = when (character) {
                    '_' -> (left && (!right || index == 0 || isPunctuation(source.codePointBefore(index)))) ||
                        (right && (!left || after == source.length || isPunctuation(source.codePointAt(after))))
                    '~' -> after - index >= 2 && (left || right)
                    else -> left || right
                }
                if (!strip) for (position in index until after) appendStatus(result, source[position])
                index = after
                continue
            }
            appendStatus(result, if (character == '|') ' ' else character)
            index++
        }
    }

    private fun matchingEnd(source: String, start: Int, end: Int, opener: Char, closer: Char): Int {
        var depth = 1
        var quote: Char? = null
        var index = start + 1
        while (index < end) {
            val character = source[index]
            when {
                character == '\\' -> index++
                quote != null -> if (character == quote) quote = null
                opener == '(' && character in "\"'" && source[index - 1].isWhitespace() -> quote = character
                character == opener -> depth++
                character == closer -> if (--depth == 0) return index
            }
            index++
        }
        return -1
    }
}
