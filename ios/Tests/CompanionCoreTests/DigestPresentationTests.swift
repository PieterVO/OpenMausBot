import XCTest
@testable import CompanionCore

final class DigestPresentationTests: XCTestCase {
    private func structured(_ id: String = "digest", tools: [TurnDigest.Tool] = [.init(name: "Bash", count: 3, failed: 0)], duration: Double = 40_000, calls: Int? = nil, files: TurnDigest.Files? = nil, memory: [TurnDigest.MemoryChange] = [], usage: TurnDigest.Usage? = nil, coverage: TurnDigest.HookCoverage = .full) -> Message {
        var message = Message(id: id, role: .bot, kind: .digest, at: 1)
        message.turnId = "turn"
        message.digest = TurnDigest(turnId: "turn", botId: "bot", threadId: "thread", at: 1, durationMs: duration, tools: tools, toolCalls: calls, files: files, memory: memory, reply: "Done", usage: usage, hookCoverage: coverage)
        return message
    }

    private func legacy(_ text: String) -> Message {
        var message = Message(id: "legacy", role: .bot, kind: .digest, at: 1)
        message.text = text
        message.turnId = "legacy-turn"
        return message
    }

    func testStructuredPresentationCarriesAllEvidenceAndOverrideCallCount() {
        let message = structured(tools: [.init(name: "Read", count: 2, failed: 0, sample: "Read Package.swift"), .init(name: "Bash", count: 1, failed: 1)], duration: 72_000, calls: 4,
                                 files: .init(changed: ["a.swift"], added: ["b.swift"], deleted: [], truncated: 2),
                                 memory: [.init(path: "MEMORY.md", kind: .updated)], usage: .init(input: 100, output: 50, cachedInput: 80, costUsd: 0.04), coverage: .preview)
        let presentation = DigestPresentation(message: message)
        XCTAssertEqual(presentation.duration, "1m 12s")
        XCTAssertEqual(presentation.toolCalls, 4)
        XCTAssertEqual(presentation.failedCalls, 1)
        XCTAssertEqual(presentation.files?.count, 2)
        XCTAssertEqual(presentation.files?.truncated, 2)
        XCTAssertEqual(presentation.memory.first?.kind, .updated)
        XCTAssertEqual(presentation.tokens, 150, "Cached input must not be counted a second time")
        XCTAssertEqual(presentation.costUsd, 0.04)
        XCTAssertNotNil(presentation.coverageNote)
        XCTAssertEqual(presentation.tools.first?.sample, "Read Package.swift")
        XCTAssertTrue(presentation.fallbackLines.isEmpty)
        XCTAssertEqual(presentation.slimLine, "1 failed step · Worked 1m 12s · 4 tools · 2 files")
        XCTAssertFalse(presentation.isEmpty)
        for evidence in ["Read ×2", "Bash ×1 (1 failed)", "Read Package.swift", "changed a.swift", "added b.swift", "+2 more paths", "updated MEMORY.md", "150 tokens", "$0.04"] {
            XCTAssertTrue(presentation.plainText.contains(evidence), evidence)
        }
        XCTAssertFalse(presentation.plainText.contains("Done"), "Do not repeat the reply")
    }

    func testDurationFormattingCountsAndPluralRules() {
        XCTAssertEqual(DigestPresentation(message: structured(duration: 40_000)).slimLine, "Worked 40s · 3 tools")
        XCTAssertEqual(DigestPresentation(message: structured(duration: 7_380_000)).duration, "2h 3m")
        XCTAssertEqual(DigestPresentation(message: structured(duration: 60_000)).duration, "1m 0s")
        XCTAssertEqual(DigestPresentation(message: structured(duration: 900)).duration, "0s")
        XCTAssertEqual(DigestPresentation(message: structured(tools: [.init(name: "Read", count: 1, failed: 0)], duration: 0, files: .init(changed: ["one"], added: [], deleted: []))).slimLine, "1 tool · 1 file")
        XCTAssertEqual(DigestPresentation(message: structured(tools: [.init(name: "Read", count: 3, failed: 2)], duration: 0)).slimLine, "2 failed steps · 3 tools")
        XCTAssertNil(DigestPresentation(message: structured(duration: 0)).duration)
        XCTAssertNil(DigestPresentation(message: structured()).coverageNote)
        XCTAssertNil(DigestPresentation(message: structured(coverage: .none)).coverageNote)
    }

    func testStructuredDigestWinsOverContradictoryLegacyText() {
        var message = structured()
        message.text = "[digest] · tools: Wrong ×99 (5 failed) · files: changed wrong.swift"
        let presentation = DigestPresentation(message: message)
        XCTAssertEqual(presentation.toolCalls, 3)
        XCTAssertEqual(presentation.failedCalls, 0)
        XCTAssertFalse(digestHasProblem(message))
        XCTAssertNil(presentation.files)
    }

    func testProblemDetectionUsesFailureFlagStructuredFailuresOrLegacyToolSuffix() {
        var failedTurn = structured()
        failedTurn.turnSucceeded = false
        XCTAssertTrue(digestHasProblem(failedTurn))
        XCTAssertEqual(DigestPresentation(message: failedTurn).slimLine, "Turn failed · Worked 40s · 3 tools")
        XCTAssertTrue(digestHasProblem(structured(tools: [.init(name: "Bash", count: 2, failed: 1)])))
        XCTAssertTrue(digestHasProblem(legacy("[digest] · tools: echo a, b ×2 (1 failed), Read ×1 · reply: Done")))
        XCTAssertFalse(digestHasProblem(legacy("[digest] · tools: Bash ×2 (0 failed)")))
        XCTAssertFalse(digestHasProblem(legacy("[digest] · files: changed (2 failed) · reply: tools: Bash ×1 (3 failed)")))
        XCTAssertFalse(digestHasProblem(legacy("[digest] · tools: Bash ×2 (1 failed) extra")))
    }

    func testProblemSummariesAutomaticallyShowAtEveryDetailAndSuccessfulOnesDefaultOff() {
        let success = structured("success")
        let problem = structured("problem", tools: [.init(name: "Bash", count: 2, failed: 1)])
        var failedTurn = structured("failed-turn")
        failedTurn.turnSucceeded = false
        let fallback = legacy("[digest] · tools: Read ×2 (1 failed)")
        for detail in ActivityDetail.allCases {
            XCTAssertEqual(transcriptRows([success, problem, failedTurn, fallback], detail: detail).map(\.id), ["problem", "failed-turn", "legacy"])
            XCTAssertEqual(transcriptRows([success, problem, failedTurn, fallback], detail: detail, showSummaries: true).map(\.id), ["success", "problem", "failed-turn", "legacy"])
        }
    }

    func testNothingDoneSuppressesEvenFailedEmptyDigestsAndUsageAlone() {
        var empty = structured(tools: [], duration: 40_000, usage: .init(input: 1, output: 2))
        empty.turnSucceeded = false
        XCTAssertTrue(DigestPresentation(message: empty).isEmpty)
        let noCalls = legacy("[digest] · no tool calls · files: none changed · reply: Hello")
        for detail in ActivityDetail.allCases {
            for enabled in [false, true] {
                XCTAssertTrue(transcriptRows([empty, noCalls], detail: detail, showSummaries: enabled).isEmpty)
            }
        }
        var dropped = empty
        dropped.digest?.toolsDropped = 2
        XCTAssertFalse(DigestPresentation(message: dropped).isEmpty)
        XCTAssertFalse(DigestPresentation(message: structured(tools: [], files: .init(changed: [], added: [], deleted: [], truncated: 1))).isEmpty)
        XCTAssertFalse(DigestPresentation(message: structured(tools: [], memory: [.init(path: "notes", kind: .created)])).isEmpty)
    }

    func testFallbackRepresentsKnownSectionsAndPreservesUnknownsWithoutDuplication() {
        let message = legacy("[digest] · tools: echo a, b ×2 (1 failed), Read ×1 · files: changed a.swift, b.swift; added c.swift; deleted old.swift; +2 more paths · memory: updated MEMORY.md, created notes/a.md · +3 more memory changes · reply: Done")
        let presentation = DigestPresentation(message: message)
        XCTAssertEqual(presentation.tools.map(\.name), ["echo a, b", "Read"])
        XCTAssertEqual(presentation.toolCalls, 3)
        XCTAssertEqual(presentation.failedCalls, 1)
        XCTAssertEqual(presentation.files?.changed, ["a.swift", "b.swift"])
        XCTAssertEqual(presentation.files?.added, ["c.swift"])
        XCTAssertEqual(presentation.files?.deleted, ["old.swift"])
        XCTAssertEqual(presentation.files?.truncated, 2)
        XCTAssertEqual(presentation.memory.map(\.path), ["MEMORY.md", "notes/a.md"])
        XCTAssertEqual(presentation.fallbackLines.map(\.value), ["+3 more memory changes"])
        XCTAssertEqual(presentation.slimLine, "1 failed step · 3 tools · 4 files")
        XCTAssertNil(presentation.duration)
        XCTAssertNil(presentation.tokens)
        XCTAssertNil(presentation.costUsd)
        XCTAssertEqual(presentation.plainText, DigestSummary(text: message.text!).plainText)
    }

    func testFallbackPreviewAndUnrecognizedItemsRemainAvailable() {
        let preview = DigestPresentation(message: legacy("[digest] · tools: Bash ×2 +3 more (from tool previews) · files: unusual evidence · memory: future notes"))
        XCTAssertEqual(preview.tools.first?.count, 2)
        XCTAssertNotNil(preview.coverageNote)
        XCTAssertEqual(preview.fallbackLines.map(\.value), ["+3 more", "unusual evidence", "future notes"])
        let unknown = DigestPresentation(message: legacy("[digest] · newer evidence"))
        XCTAssertFalse(unknown.isEmpty)
        XCTAssertEqual(unknown.slimLine, "What I did")
        XCTAssertEqual(unknown.fallbackLines.first?.value, "newer evidence")
        let memory = DigestPresentation(message: legacy("[digest] · memory: created notes.md"))
        XCTAssertEqual(memory.slimLine, "1 memory change")
    }

    func testDigestsByTurnKeepsLatestNonemptyAndUsesStructuredTurnMarkerWhenNeeded() {
        let earlier = structured("earlier")
        var empty = structured("empty", tools: [])
        empty.turnId = "turn"
        var other = structured("other")
        other.turnId = nil
        other.digest?.turnId = "other-turn"
        var answer = structured("not-a-digest")
        answer.kind = .text
        var noTurn = legacy("[digest] · tools: Read ×1")
        noTurn.turnId = nil
        let result = digestsByTurn([earlier, empty, other, answer, noTurn])
        XCTAssertEqual(result["turn"]?.id, "earlier")
        XCTAssertEqual(result["other-turn"]?.id, "other")
        XCTAssertEqual(result.count, 2)
        let latest = structured("latest")
        XCTAssertEqual(digestsByTurn([earlier, latest])["turn"]?.id, "latest")
    }

    func testUntrustedLargeCountsCannotOverflowPresentationOrLegacyParser() {
        let message = structured(tools: [.init(name: "A", count: Int.max, failed: Int.max), .init(name: "B", count: 1, failed: 1)], usage: .init(input: Int.max, output: 1))
        let presentation = DigestPresentation(message: message)
        XCTAssertEqual(presentation.toolCalls, Int.max)
        XCTAssertEqual(presentation.failedCalls, Int.max)
        XCTAssertEqual(presentation.tokens, Int.max)
        let fallback = DigestPresentation(message: legacy("[digest] · tools: A ×\(Int.max) (\(Int.max) failed), B ×1 (1 failed)"))
        XCTAssertEqual(fallback.toolCalls, Int.max)
        XCTAssertEqual(fallback.failedCalls, Int.max)
    }
}
