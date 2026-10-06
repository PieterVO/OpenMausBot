// How a reply reaches the screen while it is still arriving.
//
// The computer streams text in network-sized batches: a few characters, then
// twenty, at uneven gaps. Drawn as they land, words pop in in clumps and the
// bubble lurches. These are the pure parts of smoothing that out: how far a
// steady reveal may advance in one frame, how to show a half-typed Markdown
// span without its raw asterisks, and how to read a stream on one plain line.

import Foundation

public enum RevealPacing {
    /// Characters per second when there is little to catch up on.
    public static let streamingRate: Double = 80
    /// The catch-up time constant: a backlog drains at backlog / catchUp
    /// characters per second, so a burst of text is absorbed in well under
    /// a second and the steady lag behind a fast stream stays near this.
    public static let streamingCatchUp: Double = 0.3
    /// A settled reply that never streamed visibly reveals at least this fast…
    public static let arrivalRate: Double = 90
    /// …and always within this long, however long it is.
    public static let arrivalDuration: Double = 0.9

    /// One frame of the reveal. `carry` holds the fractional character the
    /// last frame earned but could not show; pass it back in on the next.
    /// The rate scales with the backlog, so a burst of text drains on the
    /// `catchUp` time constant instead of queueing behind a fixed typing speed.
    public static func step(
        shown: Int,
        target: Int,
        carry: Double,
        elapsed: Double,
        minimumRate: Double,
        catchUp: Double
    ) -> (shown: Int, carry: Double) {
        guard target > shown else { return (target, 0) }
        let backlog = Double(target - shown)
        let rate = max(minimumRate, backlog / max(catchUp, 0.001))
        let earned = carry + rate * max(elapsed, 0)
        let whole = min(Int(earned), target - shown)
        let next = shown + whole
        return (next, next >= target ? 0 : earned - Double(whole))
    }
}

public enum MarkdownPartial {
    /// The first `count` characters of a reply, adjusted so a table never
    /// shows half-drawn: the reveal moves through a table a whole row at a
    /// time, and its header waits until the delimiter row under it exists.
    /// `final` says the text is complete (a settled reply) rather than still
    /// arriving, so a last line with no newline after it is a whole line.
    public static func revealedPrefix(_ text: String, count: Int, final: Bool) -> String {
        if final, count >= text.count { return text }
        let cut = text.index(text.startIndex, offsetBy: min(max(0, count), text.count))
        let lineStart = text[..<cut].lastIndex(of: "\n").map { text.index(after: $0) } ?? text.startIndex
        guard isTableLine(text[lineStart...].prefix { $0 != "\n" }) else { return String(text[..<cut]) }
        // The table this line belongs to starts at the first of its run of
        // table lines; its header and delimiter rows appear together or not
        // at all.
        var tableStart = lineStart
        while tableStart > text.startIndex {
            let above = previousLine(in: text, before: tableStart)
            guard isTableLine(above) else { break }
            tableStart = above.startIndex
        }
        guard let headerEnd = lineEnd(in: text, from: tableStart, final: final), headerEnd < text.endIndex else {
            return String(text[..<tableStart])
        }
        let delimiterStart = text.index(after: headerEnd)
        guard let delimiterEnd = lineEnd(in: text, from: delimiterStart, final: final),
              isDelimiterLine(text[delimiterStart..<delimiterEnd])
        else { return String(text[..<tableStart]) }
        if lineStart <= delimiterStart { return String(text[..<delimiterEnd]) }
        // A body row, whole once it has fully arrived.
        guard let rowEnd = lineEnd(in: text, from: lineStart, final: final) else {
            return String(text[..<lineStart])
        }
        return String(text[..<rowEnd])
    }

    private static func isTableLine<S: StringProtocol>(_ line: S) -> Bool {
        line.drop { $0 == " " }.hasPrefix("|")
    }

    private static func isDelimiterLine<S: StringProtocol>(_ line: S) -> Bool {
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        return trimmed.contains("-") && trimmed.allSatisfy { "|-: ".contains($0) }
    }

    /// The end of the line starting at `start`: its newline, or the end of a
    /// final text. Nil while the line is still arriving.
    private static func lineEnd(in text: String, from start: String.Index, final: Bool) -> String.Index? {
        if let newline = text[start...].firstIndex(of: "\n") { return newline }
        return final ? text.endIndex : nil
    }

    private static func previousLine(in text: String, before lineStart: String.Index) -> Substring {
        let end = text.index(before: lineStart)
        let start = text[..<end].lastIndex(of: "\n").map { text.index(after: $0) } ?? text.startIndex
        return text[start..<end]
    }

    /// A prefix of a Markdown reply as it should be drawn mid-stream: a bold
    /// span or inline code span that has opened but not yet closed is closed
    /// here, so the words already typed read as bold or code instead of
    /// sitting behind a literal `**` or backtick until the closer arrives.
    /// Fenced code blocks are left alone; their own parser handles an open
    /// fence.
    public static func closingOpenSpans(_ text: String) -> String {
        var inFence = false
        var lines: [Substring] = []
        var closers = ""
        for line in text.split(separator: "\n", omittingEmptySubsequences: false) {
            lines.append(line)
            if line.trimmingCharacters(in: .whitespaces).hasPrefix("```") {
                inFence.toggle()
            }
        }
        guard !inFence, let last = lines.last else { return text }
        // Only the line being typed can hold an open span; finished lines
        // were balanced (or not) by the bot, and are not ours to change.
        var shown = text
        var open = String(last)
        // An opener with nothing after it yet would draw as a literal "**"
        // or an empty code span: hold it back until its first word lands.
        if open.hasSuffix("**"), open.components(separatedBy: "**").count % 2 == 0 {
            shown.removeLast(2)
            open.removeLast(2)
        } else if open.hasSuffix("`"), open.filter({ $0 == "`" }).count % 2 == 1 {
            shown.removeLast()
            open.removeLast()
        }
        if open.filter({ $0 == "`" }).count % 2 == 1 { closers += "`" }
        let outsideCode = open.split(separator: "`", omittingEmptySubsequences: false)
            .enumerated().filter { $0.offset % 2 == 0 }.map(\.element).joined()
        // Inner spans close first: an open code span inside open bold.
        if outsideCode.components(separatedBy: "**").count % 2 == 0 { closers += "**" }
        return shown + closers
    }
}

public enum MarkdownPlain {
    /// A reply squeezed onto one quiet status line: the words, without the
    /// Markdown that only means something when it is rendered (emphasis
    /// markers, heading hashes, list bullets, table pipes and rules).
    public static func line(_ text: String) -> String {
        var words: [String] = []
        for raw in text.split(separator: "\n") {
            var line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }
            // A table's delimiter row carries no words at all.
            if line.allSatisfy({ "|-: ".contains($0) }) { continue }
            while line.hasPrefix("#") { line.removeFirst() }
            for marker in ["- [ ] ", "- [x] ", "- [X] ", "- ", "* ", "+ ", "> "] where line.hasPrefix(marker) {
                line.removeFirst(marker.count)
                break
            }
            if line.hasPrefix("|") {
                line = line.split(separator: "|").map { $0.trimmingCharacters(in: .whitespaces) }
                    .filter { !$0.isEmpty }.joined(separator: ", ")
            }
            for token in ["**", "__", "`", "~~"] {
                line = line.replacingOccurrences(of: token, with: "")
            }
            line = line.trimmingCharacters(in: .whitespaces)
            if !line.isEmpty { words.append(line) }
        }
        return words.joined(separator: " ")
    }
}
