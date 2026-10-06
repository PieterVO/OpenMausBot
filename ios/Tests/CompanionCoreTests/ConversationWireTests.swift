import XCTest
@testable import CompanionCore

final class ConversationWireTests: XCTestCase {
    private var digest: [String: Any] {
        ["turnId": "turn", "botId": "bot", "threadId": "thread", "at": 1, "durationMs": 72_000,
         "tools": [["name": "Bash", "count": 3, "failed": 1, "sample": "swift test"]], "toolCalls": 5, "toolsDropped": 2,
         "files": ["changed": ["a.swift"], "added": ["b.swift"], "deleted": ["c.swift"], "truncated": 4],
         "memory": [["path": "MEMORY.md", "kind": "updated"]], "memoryDropped": 1, "reply": "Done.",
         "usage": ["input": 100, "output": 50, "cachedInput": 20, "costUsd": 0.04], "hookCoverage": "preview"]
    }

    private func decode(_ fields: [String: Any]) throws -> Message {
        var wire: [String: Any] = ["id": "message", "role": "bot", "kind": "digest", "at": 1, "text": "Legacy receipt"]
        wire.merge(fields) { _, new in new }
        return try JSONDecoder().decode(Message.self, from: JSONSerialization.data(withJSONObject: wire))
    }

    func testOfflineShowcaseFleetHydratesWithStructuredConversationEvidence() throws {
        let package = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let fixture = package.appendingPathComponent("App/ChatShowcasePreview.json")
        let fleet = try JSONDecoder().decode(Fleet.self, from: Data(contentsOf: fixture))
        let pepper = try XCTUnwrap(fleet.bots.first)
        XCTAssertEqual(pepper.id, "preview-pepper")
        let messages = try XCTUnwrap(pepper.messages)
        XCTAssertGreaterThanOrEqual(messages.filter { $0.tool?.name == "TodoWrite" }.count, 2)
        XCTAssertTrue(messages.contains { $0.tool?.summary != nil })
        let problem = try XCTUnwrap(messages.first { $0.id == "showcase-digest-problem" })
        XCTAssertEqual(problem.turnSucceeded, false)
        XCTAssertEqual(problem.digest?.durationMs, 72_000)
        XCTAssertEqual(problem.digest?.files?.truncated, 0)
        let success = try XCTUnwrap(messages.first { $0.id == "showcase-digest-success" })
        XCTAssertEqual(success.turnSucceeded, true)
        XCTAssertNotNil(success.digest)
        XCTAssertTrue(messages.contains { $0.card?.requestId != nil })
        XCTAssertEqual(messages.first { $0.id == "showcase-component-question" }?.card?.questions.count, 1)
        XCTAssertEqual(messages.first { $0.id == "showcase-component-credential" }?.secret?.label, "Calendar API key")
        XCTAssertEqual(messages.first { $0.id == "showcase-component-routine" }?.routineRun?.knownStatus, .completed)
        XCTAssertEqual(messages.first { $0.id == "showcase-component-report" }?.isTeammateReport, true)
        var state = CompanionState()
        state.hydrate(fleet)
        XCTAssertEqual(state.visibleTranscript(forThread: pepper.threadId).last?.id, "showcase-approval")
    }

    func testNewWireFieldsDecodeAndRoundTripWithoutLoss() throws {
        let message = try decode(["digest": digest, "turnSucceeded": false,
                                  "tool": ["name": "Bash", "input": "{\"command\":\"swift test\"}", "summary": "swift test", "itemId": "item", "output": "ok"]])
        XCTAssertEqual(message.digest?.turnId, "turn")
        XCTAssertEqual(message.digest?.durationMs, 72_000)
        XCTAssertEqual(message.digest?.tools.first?.sample, "swift test")
        XCTAssertEqual(message.digest?.toolCalls, 5)
        XCTAssertEqual(message.digest?.toolsDropped, 2)
        XCTAssertEqual(message.digest?.files?.truncated, 4)
        XCTAssertEqual(message.digest?.memory.first?.kind, .updated)
        XCTAssertEqual(message.digest?.memoryDropped, 1)
        XCTAssertEqual(message.digest?.usage?.cachedInput, 20)
        XCTAssertEqual(message.digest?.usage?.costUsd, 0.04)
        XCTAssertEqual(message.digest?.hookCoverage, .preview)
        XCTAssertEqual(message.turnSucceeded, false)
        XCTAssertEqual(message.tool?.summary, "swift test")
        XCTAssertEqual(message.tool?.itemId, "item")
        XCTAssertEqual(message.tool?.input, "{\"command\":\"swift test\"}")
        XCTAssertEqual(try JSONDecoder().decode(Message.self, from: JSONEncoder().encode(message)), message)
    }

    func testAbsentAndMalformedToolPreviewFieldsNeverBreakMessage() throws {
        let legacy = try decode(["tool": ["name": "Bash"]])
        XCTAssertNil(legacy.digest)
        XCTAssertNil(legacy.turnSucceeded)
        XCTAssertNil(legacy.tool?.input)
        XCTAssertNil(legacy.tool?.summary)
        XCTAssertNil(legacy.tool?.itemId)
        for bad in [NSNull(), 42, true, ["bad"], ["bad": "object"]] as [Any] {
            let message = try decode(["turnSucceeded": bad, "tool": ["name": "Bash", "input": bad, "summary": bad, "itemId": bad, "output": bad]])
            XCTAssertEqual(message.id, "message")
            XCTAssertEqual(message.tool?.name, "Bash")
            // A boolean is malformed for a preview, but valid turn status.
            XCTAssertEqual(message.turnSucceeded, bad as? Bool)
            XCTAssertNil(message.tool?.input)
            XCTAssertNil(message.tool?.summary)
            XCTAssertNil(message.tool?.itemId)
            XCTAssertNil(message.tool?.output)
        }
    }

    func testEveryMalformedOptionalMessageFieldLeavesRequiredEnvelopeIntact() throws {
        let fields = ["text", "turnId", "turnTerminal", "turnSucceeded", "digest", "card", "secret", "tool", "threadRef", "compaction", "routineRun", "parentId", "queueId", "steered", "from", "via", "reactions", "comm", "hasImage", "png", "mime", "attachments"]
        for field in fields {
            let message = try decode([field: ["wrong", "shape"]])
            XCTAssertEqual(message.id, "message", field)
            XCTAssertEqual(message.kind, .digest, field)
            XCTAssertEqual(message.role, .bot, field)
            XCTAssertEqual(message.at, 1, field)
        }
    }

    func testMalformedDigestOrNestedEvidenceDecodesNilNotMissingMessage() throws {
        for bad in [NSNull(), false, 12, "bad", [], [:]] as [Any] {
            XCTAssertNil(try decode(["digest": bad]).digest)
        }
        let invalidFields: [(String, Any)] = [
            ("turnId", 2), ("durationMs", -1), ("tools", [["name": "Bash", "count": -1, "failed": 0]]),
            ("tools", [["name": "Bash", "count": 1, "failed": "no"]]), ("toolCalls", "many"), ("toolsDropped", -1),
            ("files", ["changed": [42], "added": [], "deleted": []]),
            ("files", ["changed": [], "added": [], "deleted": [], "truncated": true]),
            ("memory", [["path": "MEMORY.md", "kind": "future"]]), ("memoryDropped", -1),
            ("usage", ["input": -1, "output": 2]), ("usage", ["input": 1, "output": 2, "costUsd": "unknown"]),
            ("hookCoverage", "future"),
        ]
        for (field, bad) in invalidFields {
            var malformed = digest
            malformed[field] = bad
            let message = try decode(["digest": malformed])
            XCTAssertNil(message.digest, field)
            XCTAssertEqual(message.text, "Legacy receipt", field)
        }
        var missing = digest
        missing.removeValue(forKey: "tools")
        XCTAssertNil(try decode(["digest": missing]).digest)
    }

    func testNullableCostAndOptionalDigestFieldsAreSupported() throws {
        var minimal = digest
        for key in ["toolCalls", "toolsDropped", "files", "memoryDropped"] { minimal.removeValue(forKey: key) }
        minimal["usage"] = ["input": 1, "output": 2, "costUsd": NSNull()]
        let decoded = try XCTUnwrap(decode(["digest": minimal]).digest)
        XCTAssertNil(decoded.toolCalls)
        XCTAssertNil(decoded.files)
        XCTAssertNil(decoded.usage?.costUsd)
    }

    func testMalformedOptionalMessageStillDecodesInsideFramesAndArrays() throws {
        let wire = #"{"id":"message","role":"bot","kind":"text","at":1,"digest":{"tools":false},"turnSucceeded":"no","tool":{"name":"Bash","input":42}}"#
        let messages = try JSONDecoder().decode([Message].self, from: Data("[\(wire)]".utf8))
        XCTAssertEqual(messages.count, 1)
        let frame = try JSONDecoder().decode(Frame.self, from: Data("{\"kind\":\"message.patch\",\"threadId\":\"thread\",\"message\":\(wire)}".utf8))
        guard case let .messagePatch(threadId, message) = frame else { return XCTFail("Missing patch") }
        XCTAssertEqual(threadId, "thread")
        XCTAssertEqual(message.id, "message")
        XCTAssertNil(message.digest)
    }
}
