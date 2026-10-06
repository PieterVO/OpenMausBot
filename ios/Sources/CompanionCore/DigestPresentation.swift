// The same presentation feeds the optional transcript line, the reply's
// details link, and the work sheet. Visibility never depends on tool detail.
import Foundation

public func digestHasProblem(_ message: Message) -> Bool {
    if message.turnSucceeded == false { return true }
    if let digest = message.digest { return digest.tools.contains { $0.failed > 0 } }
    return DigestSummary(text: message.text ?? "").failedCalls > 0
}

func digestIsEmpty(_ message: Message) -> Bool {
    guard let digest = message.digest else { return DigestSummary(text: message.text ?? "").isEmpty }
    let hasCalls = digest.toolCalls.map { $0 > 0 } ?? digest.tools.contains { $0.count > 0 }
    return !hasCalls && !digest.tools.contains { $0.failed > 0 } &&
        (digest.files?.count ?? 0) == 0 && (digest.files?.truncated ?? 0) == 0 &&
        digest.memory.isEmpty && (digest.toolsDropped ?? 0) == 0 && (digest.memoryDropped ?? 0) == 0
}

func shouldShowDigest(_ message: Message, showSummaries: Bool) -> Bool {
    if message.digest != nil {
        return (showSummaries || digestHasProblem(message)) && !digestIsEmpty(message)
    }
    let summary = DigestSummary(text: message.text ?? "")
    return !summary.isEmpty && (showSummaries || message.turnSucceeded == false || summary.failedCalls > 0)
}

public func digestsByTurn(_ messages: [Message]) -> [String: Message] {
    var result: [String: Message] = [:]
    for message in messages where message.kind == .digest {
        guard !digestIsEmpty(message),
              let turn = message.turnId ?? message.digest?.turnId, !turn.isEmpty else { continue }
        result[turn] = message
    }
    return result
}

public struct DigestPresentation: Hashable, Sendable {
    public let duration: String?
    public let toolCalls: Int
    public let failedCalls: Int
    public let files: TurnDigest.Files?
    public let memory: [TurnDigest.MemoryChange]
    public let tokens: Int?
    public let costUsd: Double?
    public let coverageNote: String?
    public let tools: [TurnDigest.Tool]
    /// Only unknown/unparsed legacy items; represented fields are not repeated.
    public let fallbackLines: [DigestSummary.Line]
    public let slimLine: String
    public let isEmpty: Bool
    public let plainText: String

    public init(message: Message) {
        if let digest = message.digest {
            duration = Self.duration(digest.durationMs)
            toolCalls = max(0, digest.toolCalls ?? digestSum(digest.tools.lazy.map(\.count)))
            failedCalls = digestSum(digest.tools.lazy.map(\.failed))
            files = digest.files
            memory = digest.memory
            tokens = digest.usage.map { digestSum([$0.input, $0.output]) }
            costUsd = digest.usage?.costUsd
            coverageNote = digest.hookCoverage == .preview ? Self.previewNote : nil
            tools = digest.tools
            fallbackLines = []
            isEmpty = digestIsEmpty(message)
            var sections: [String] = []
            if let duration { sections.append(Self.worked(duration)) }
            if !tools.isEmpty {
                sections.append(Self.section("Tools", items: tools.map { tool in
                    var item = "\(tool.name) ×\(tool.count)"
                    if tool.failed > 0 { item += " (\(tool.failed) failed)" }
                    if let sample = tool.sample, !sample.isEmpty { item += "\n  \(sample)" }
                    return item
                }))
            }
            if let dropped = digest.toolsDropped, dropped > 0 { sections.append("+\(dropped) more tools") }
            if let files {
                var paths = files.added.map { "added \($0)" } + files.changed.map { "changed \($0)" } + files.deleted.map { "deleted \($0)" }
                if let count = files.truncated, count > 0 { paths.append("+\(count) more paths") }
                if !paths.isEmpty { sections.append(Self.section("Files", items: paths)) }
            }
            if !memory.isEmpty { sections.append(Self.section("Memory", items: memory.map { "\($0.kind.rawValue) \($0.path)" })) }
            if let dropped = digest.memoryDropped, dropped > 0 { sections.append("+\(dropped) more memory changes") }
            if let tokens { sections.append("\(tokens) tokens") }
            if let costUsd { sections.append(String(format: "$%.2f", costUsd)) }
            if let coverageNote { sections.append(coverageNote) }
            plainText = sections.joined(separator: "\n\n")
        } else {
            let summary = DigestSummary(text: message.text ?? "")
            let fallback = Self.fallback(summary)
            duration = nil
            toolCalls = summary.toolCalls
            failedCalls = summary.failedCalls
            files = fallback.files
            memory = fallback.memory
            tokens = nil
            costUsd = nil
            coverageNote = summary.lines.contains { $0.value.contains("(from tool previews)") } ? Self.previewNote : nil
            tools = fallback.tools
            fallbackLines = fallback.lines
            isEmpty = summary.isEmpty
            plainText = summary.plainText
        }
        var parts: [String] = []
        if failedCalls > 0 {
            parts.append(Self.count(failedCalls, one: "%lld failed step", many: "%lld failed steps"))
        } else if message.turnSucceeded == false {
            parts.append(NSLocalizedString("Turn failed", comment: "Work summary for an unsuccessful turn"))
        }
        if let duration { parts.append(Self.worked(duration)) }
        if toolCalls > 0 { parts.append(Self.count(toolCalls, one: "%lld tool", many: "%lld tools")) }
        if let files, files.count > 0 { parts.append(Self.count(files.count, one: "%lld file", many: "%lld files")) }
        if parts.isEmpty, !memory.isEmpty { parts.append(Self.count(memory.count, one: "%lld memory change", many: "%lld memory changes")) }
        slimLine = parts.isEmpty ? NSLocalizedString("What I did", comment: "Work summary") : parts.joined(separator: " · ")
    }

    private static var previewNote: String {
        NSLocalizedString("Based on tool previews; some work may not be shown.", comment: "Limited work-summary evidence")
    }

    private static func worked(_ duration: String) -> String {
        String(format: NSLocalizedString("Worked %@", comment: "Work-summary duration"), duration)
    }

    private static func count(_ value: Int, one: String, many: String) -> String {
        String.localizedStringWithFormat(NSLocalizedString(value == 1 ? one : many, comment: "Work-summary count"), Int64(value))
    }

    private static func duration(_ milliseconds: Double) -> String? {
        guard milliseconds.isFinite, milliseconds > 0 else { return nil }
        // A positive subsecond turn reads as 0s; an absent/zero duration is omitted.
        let seconds = Int(min(milliseconds / 1_000, Double(Int.max / 1_000)))
        if seconds >= 3_600 {
            return String.localizedStringWithFormat(NSLocalizedString("%lldh %lldm", comment: "Work duration in hours and minutes"), Int64(seconds / 3_600), Int64((seconds % 3_600) / 60))
        }
        if seconds >= 60 {
            return String.localizedStringWithFormat(NSLocalizedString("%lldm %llds", comment: "Work duration in minutes and seconds"), Int64(seconds / 60), Int64(seconds % 60))
        }
        return String.localizedStringWithFormat(NSLocalizedString("%llds", comment: "Work duration in seconds"), Int64(seconds))
    }

    private static func section(_ title: String, items: [String]) -> String {
        title + "\n" + items.map { "• \($0)" }.joined(separator: "\n")
    }

    private static let legacyTool = try! NSRegularExpression(pattern: #"^(.+?) ×(\d+)(?: \((\d+) failed\))?(?: \+\d+ more)?(?: \(from tool previews\))?$"#)
    private static let omittedPaths = try! NSRegularExpression(pattern: #"^\+(\d+) more paths$"#)

    private static func fallback(_ summary: DigestSummary) -> (tools: [TurnDigest.Tool], files: TurnDigest.Files?, memory: [TurnDigest.MemoryChange], lines: [DigestSummary.Line]) {
        var tools: [TurnDigest.Tool] = []
        var files = TurnDigest.Files(changed: [], added: [], deleted: [])
        var memory: [TurnDigest.MemoryChange] = []
        var lines: [DigestSummary.Line] = []
        for line in summary.lines {
            var unknown: [String] = []
            for item in line.items {
                switch line.label {
                case "Tools":
                    if let match = legacyTool.firstMatch(in: item, range: NSRange(item.startIndex..., in: item)),
                       let name = Range(match.range(at: 1), in: item), let count = Range(match.range(at: 2), in: item) {
                        let failed = Range(match.range(at: 3), in: item).map { Int(item[$0]) ?? Int.max } ?? 0
                        tools.append(TurnDigest.Tool(name: String(item[name]), count: Int(item[count]) ?? Int.max, failed: failed))
                        // Dropped-tool evidence has no structured legacy field.
                        if let range = item.range(of: #"\+\d+ more"#, options: .regularExpression) {
                            lines.append(DigestSummary.Line(label: nil, value: String(item[range]), items: [String(item[range])]))
                        }
                    } else { unknown.append(item) }
                case "Files":
                    if let match = omittedPaths.firstMatch(in: item, range: NSRange(item.startIndex..., in: item)),
                       let count = Range(match.range(at: 1), in: item) {
                        files.truncated = Int(item[count]) ?? Int.max
                    } else if item.hasPrefix("changed ") {
                        files.changed += paths(item, prefix: "changed ")
                    } else if item.hasPrefix("added ") {
                        files.added += paths(item, prefix: "added ")
                    } else if item.hasPrefix("deleted ") {
                        files.deleted += paths(item, prefix: "deleted ")
                    } else { unknown.append(item) }
                case "Memory":
                    if let kind = [TurnDigest.MemoryChange.Kind.created, .updated, .deleted].first(where: { item.hasPrefix($0.rawValue + " ") }) {
                        let path = String(item.dropFirst(kind.rawValue.count + 1))
                        if !path.isEmpty { memory.append(.init(path: path, kind: kind)) } else { unknown.append(item) }
                    } else { unknown.append(item) }
                default: unknown.append(item)
                }
            }
            if !unknown.isEmpty { lines.append(DigestSummary.Line(label: line.label, value: unknown.joined(separator: ", "), items: unknown)) }
        }
        return (tools, files.count > 0 || (files.truncated ?? 0) > 0 ? files : nil, memory, lines)
    }

    private static func paths(_ item: String, prefix: String) -> [String] {
        item.dropFirst(prefix.count).components(separatedBy: ", ").filter { !$0.isEmpty }
    }
}
