package com.openmausbot.companion.core

enum class MarkdownTableAlignment { LEADING, TRAILING, CENTER }

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val indent: Int, val text: String) : MarkdownBlock
    data class Ordered(val indent: Int, val number: Int, val text: String) : MarkdownBlock
    data class Task(val indent: Int, val number: Int?, val checked: Boolean, val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Code(val language: String?, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<MarkdownTableAlignment> = List(headers.size) { MarkdownTableAlignment.LEADING },
    ) : MarkdownBlock
    data object Rule : MarkdownBlock
}

object Markdown {
    fun blocks(source: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        // Open list markers prevent an indented continuation becoming a table.
        val listIndents = mutableListOf<Int>()

        fun closeLists(line: String) {
            val leading = leadingCount(line)
            while (listIndents.isNotEmpty() && leading <= listIndents.last()) {
                listIndents.removeAt(listIndents.lastIndex)
            }
        }

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" "))
            paragraph.clear()
        }

        val lines = source
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split('\n')
        var index = 0
        while (index < lines.size) {
            val line = lines[index++]
            val trimmed = line.trim()

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flushParagraph()
                closeLists(line)
                val marker = trimmed.take(3)
                val language = trimmed.drop(3).trim().ifEmpty { null }
                val body = mutableListOf<String>()
                while (index < lines.size) {
                    val next = lines[index++]
                    if (next.trim().startsWith(marker)) break
                    body += next
                }
                blocks += MarkdownBlock.Code(language, body.joinToString("\n"))
                continue
            }

            if (trimmed.isEmpty()) {
                flushParagraph()
                listIndents.clear()
                continue
            }

            if (trimmed.length >= 3 && trimmed.first() in "-*_") {
                if (trimmed.all { it == trimmed.first() }) {
                    flushParagraph()
                    closeLists(line)
                    blocks += MarkdownBlock.Rule
                    continue
                }
            }

            heading(trimmed)?.let {
                flushParagraph()
                closeLists(line)
                blocks += it
                continue
            }

            if (trimmed.startsWith('>')) {
                flushParagraph()
                closeLists(line)
                blocks += MarkdownBlock.Quote(trimmed.drop(1).trim())
                continue
            }

            val leading = leadingCount(line)
            closeLists(line)
            if (listIndents.isNotEmpty() && leading > listIndents.last()) {
                val item = listItem(line)
                if (item != null) {
                    flushParagraph()
                    listIndents += leading
                    blocks += item
                } else {
                    paragraph += trimmed
                }
                continue
            }

            takeTable(line, lines, index)?.let {
                flushParagraph()
                blocks += it.block
                index = it.nextIndex
                continue
            }

            listItem(line)?.let {
                flushParagraph()
                listIndents += leading
                blocks += it
                continue
            }

            paragraph += trimmed
        }
        flushParagraph()
        return blocks
    }

    private fun leadingCount(line: String): Int {
        var count = 0
        while (count < line.length && (line[count] == ' ' || line[count] == '\t')) count++
        return count
    }

    private fun heading(trimmed: String): MarkdownBlock.Heading? {
        val hashes = trimmed.takeWhile { it == '#' }.length
        if (hashes !in 1..6) return null
        val rest = trimmed.drop(hashes)
        if (!rest.startsWith(' ')) return null
        return MarkdownBlock.Heading(hashes, rest.trim())
    }

    private fun listItem(line: String): MarkdownBlock? {
        val leading = leadingCount(line)
        val indent = (leading / 2).coerceAtMost(4)
        val trimmed = line.trim()

        if (trimmed.length >= 2 && trimmed[0] in "-*+" && trimmed[1] == ' ') {
            val text = trimmed.drop(2)
            taskMark(text)?.let { return MarkdownBlock.Task(indent, null, it.checked, it.text) }
            return MarkdownBlock.Bullet(indent, text)
        }

        val digits = trimmed.takeWhile(Char::isDigit)
        if (digits.isNotEmpty() && digits.length <= 9) {
            val rest = trimmed.drop(digits.length)
            if (rest.startsWith(". ") || rest.startsWith(") ")) {
                val text = rest.drop(2)
                val number = digits.toIntOrNull() ?: 1
                taskMark(text)?.let { return MarkdownBlock.Task(indent, number, it.checked, it.text) }
                return MarkdownBlock.Ordered(indent, number, text)
            }
        }
        return null
    }

    private data class TaskMark(val checked: Boolean, val text: String)

    /** `[ ]`, `[x]`, or `[X]`, alone or followed by a space or tab. */
    private fun taskMark(text: String): TaskMark? {
        if (!text.startsWith('[')) return null
        val close = text.indexOf(']')
        if (close != 2 || text[1] !in " xX") return null
        val after = text.drop(close + 1)
        if (after.isNotEmpty() && after[0] != ' ' && after[0] != '\t') return null
        return TaskMark(text[1] != ' ', after.dropWhile { it == ' ' || it == '\t' })
    }

    private data class ParsedTable(val block: MarkdownBlock.Table, val nextIndex: Int)

    private fun takeTable(line: String, lines: List<String>, nextIndex: Int): ParsedTable? {
        val headers = rowCells(line) ?: return null
        if (oddBackticks(line)) return null
        val delimiter = rowCells(lines.getOrElse(nextIndex) { "" })?.takeIf { it.all(::isDelimiterCell) }
        val table: TableBuilder
        var index = nextIndex
        if (delimiter != null) {
            table = TableBuilder(headers, delimiter)
            index++
        } else {
            table = weld(line, headers) ?: return null
        }
        while (index < lines.size) {
            val body = lines[index]
            if (oddBackticks(body)) break
            val row = rowCells(body) ?: break
            table.appendRow(row)
            index++
        }
        return ParsedTable(table.build(), index)
    }

    private fun rowCells(line: String): MutableList<String>? {
        val leading = leadingCount(line)
        if (leading > 3 || (0 until leading).any { line[it] != ' ' }) return null
        if ('|' !in line || listItem(line) != null) return null
        val trimmed = line.trim()
        if (trimmed.startsWith('>') || trimmed.startsWith("```") || trimmed.startsWith("~~~")) return null
        if (heading(trimmed) != null) return null
        if (trimmed.length >= 3 && trimmed.first() in "-*_" && trimmed.all { it == trimmed.first() }) {
            return null
        }
        return cells(line).takeIf { it.isNotEmpty() }
    }

    private fun isDelimiterCell(cell: String): Boolean {
        var index = 0
        if (cell.startsWith(':')) index++
        val start = index
        while (index < cell.length && cell[index] == '-') index++
        if (index == start) return false
        if (index < cell.length && cell[index] == ':') index++
        return index == cell.length
    }

    private fun alignment(cell: String): MarkdownTableAlignment = when {
        cell.startsWith(':') && cell.endsWith(':') -> MarkdownTableAlignment.CENTER
        cell.endsWith(':') -> MarkdownTableAlignment.TRAILING
        else -> MarkdownTableAlignment.LEADING
    }

    private class TableBuilder(val headers: MutableList<String>, delimiters: List<String>) {
        private val alignments = MutableList(headers.size) { index ->
            delimiters.getOrNull(index)?.let(::alignment) ?: MarkdownTableAlignment.LEADING
        }
        val rows = mutableListOf<MutableList<String>>()

        // Wider multiline rows grow earlier rows too, so no value is lost or
        // read under the wrong column. Welded rows instead wrap at header width.
        fun appendRow(row: MutableList<String>) {
            val extra = row.size - headers.size
            if (extra > 0) {
                repeat(extra) {
                    headers += ""
                    alignments += MarkdownTableAlignment.LEADING
                    rows.forEach { it += "" }
                }
            }
            while (row.size < headers.size) row += ""
            rows += row
        }

        fun build(): MarkdownBlock.Table = MarkdownBlock.Table(headers, rows, alignments)
    }

    /** A one-line table; escapes and code spans deliberately stay prose. */
    private fun weld(line: String, candidate: List<String>): TableBuilder? {
        val indent = leadingCount(line)
        if (indent > 3 || line.getOrNull(indent) != '|') return null
        if ('`' in line || '\\' in line || candidate.all(::isDelimiterCell)) return null
        val range = delimiterRun(line) ?: return null
        val header = line.substring(0, range.first)
        val run = line.substring(range.first, range.last + 1)
        val body = line.substring(range.last + 1)
        val headers = cells(header)
        if (headers.size < 2 || !header.trim().endsWith('|')) return null
        val trimmedBody = body.trim()
        if (trimmedBody.isNotEmpty() && !trimmedBody.startsWith('|')) return null
        val delimiters = cells(run)
        if (delimiters.isEmpty() || !delimiters.all(::isDelimiterCell)) return null

        val table = TableBuilder(headers, delimiters)
        var chunk = mutableListOf<String>()
        var boundary = false
        for (value in cells(body)) {
            if (boundary) {
                boundary = false
                if (value.isEmpty()) continue
            }
            chunk += value
            if (chunk.size == headers.size) {
                table.rows += chunk
                chunk = mutableListOf()
                boundary = true
            }
        }
        if (chunk.isNotEmpty()) {
            while (chunk.size < headers.size) chunk += ""
            table.rows += chunk
        }
        return table
    }

    /**
     * Split only unescaped pipes outside code spans. Drop one empty outer
     * cell at each edge; keep other backslashes exactly as they arrived.
     */
    private fun cells(line: String): MutableList<String> {
        val trimmed = line.trim()
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        var inCode = false
        for (character in trimmed) {
            when {
                escaped -> {
                    if (character != '|') current.append('\\')
                    current.append(character)
                    escaped = false
                }
                character == '\\' -> escaped = true
                character == '`' -> {
                    inCode = !inCode
                    current.append(character)
                }
                character == '|' && !inCode -> {
                    parts += current.toString().trim()
                    current.setLength(0)
                }
                else -> current.append(character)
            }
        }
        if (escaped) current.append('\\')
        parts += current.toString().trim()
        if (trimmed.startsWith('|') && parts.firstOrNull()?.isEmpty() == true) parts.removeAt(0)
        if (trimmed.endsWith('|') && parts.lastOrNull()?.isEmpty() == true) parts.removeAt(parts.lastIndex)
        return parts
    }

    /** The first consecutive run of `| --- | --- |` delimiter cells. */
    private fun delimiterRun(line: String): IntRange? {
        var index = 0
        while (index < line.length) {
            val pipe = line.indexOf('|', index)
            if (pipe == -1) return null
            var cursor = pipe + 1
            var cells = 0
            var end = cursor
            while (cursor < line.length) {
                var look = cursor
                while (look < line.length && line[look] == ' ') look++
                if (look < line.length && line[look] == ':') look++
                val dashes = look
                while (look < line.length && line[look] == '-') look++
                if (look == dashes) break
                if (look < line.length && line[look] == ':') look++
                while (look < line.length && line[look] == ' ') look++
                if (look >= line.length || line[look] != '|') break
                cells++
                end = look + 1
                cursor = end
            }
            if (cells >= 1) return pipe until end
            index = pipe + 1
        }
        return null
    }

    private fun oddBackticks(line: String): Boolean {
        var count = 0
        var escaped = false
        for (character in line) {
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == '`') {
                count++
            }
        }
        return count % 2 == 1
    }
}
