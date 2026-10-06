import XCTest
@testable import CompanionCore

final class PlanTranscriptTests: XCTestCase {
    private func text(_ id: String, role: Message.Role = .bot) -> Message {
        var message = Message(id: id, role: role, kind: .text, at: 1)
        message.text = id
        return message
    }

    private func step(_ id: String, name: String = "Bash", input: String? = nil, turn: String? = nil) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: 2)
        message.tool = ToolActivity(name: name, ok: true, input: input)
        message.turnId = turn
        return message
    }

    private func plan(_ id: String, status: String = "pending", turn: String? = nil) -> Message {
        step(id, name: "TodoWrite", input: "{\"todos\":[{\"content\":\"Read code\",\"status\":\"\(status)\"}]}", turn: turn)
    }

    func testOnePlanPerTurnKeepsFirstPositionAndLatestStateAtEveryDetail() {
        let messages = [text("user", role: .user), plan("first", turn: "turn"), text("narration"), plan("last", status: "completed", turn: "turn"), text("reply")]
        for detail in ActivityDetail.allCases {
            let rows = transcriptRows(messages, detail: detail)
            XCTAssertEqual(rows.map(\.id), ["user", "plan.first", "narration", "reply"])
            guard case let .plan(id, message, plan) = rows[1] else { return XCTFail("Missing plan at \(detail)") }
            XCTAssertEqual(id, "plan.first")
            XCTAssertEqual(message.id, "last")
            XCTAssertTrue(plan.isFinished)
            XCTAssertEqual(rows[1].head.id, "last")
            XCTAssertEqual(rows[1].kind, .activity)
        }
        XCTAssertFalse(isActivityReceipt(messages[1]))
    }

    func testLegacyGroupsFollowLatestUserWhileExplicitTurnsRemainIndependent() {
        let messages = [plan("before"), plan("before-update", status: "done"), text("u1", role: .user), plan("legacy"),
                        plan("explicit", turn: "one"), plan("other", turn: "two"), text("u2", role: .user),
                        plan("second-legacy"), plan("explicit-update", status: "done", turn: "one")]
        for detail in ActivityDetail.allCases {
            let rows = transcriptRows(messages, detail: detail)
            XCTAssertEqual(rows.map(\.id), ["plan.before", "u1", "plan.legacy", "plan.explicit", "plan.other", "u2", "plan.second-legacy"])
            guard case let .plan(_, latest, state) = rows[3] else { return XCTFail("Missing explicit group") }
            XCTAssertEqual(latest.id, "explicit-update")
            XCTAssertTrue(state.isFinished)
        }
    }

    func testPlanAndLaterSuppressedUpdatesBreakReducedRuns() {
        let messages = [step("a"), step("b"), plan("first", turn: "turn"), step("c"), step("d"),
                        plan("last", status: "done", turn: "turn"), step("e"), step("f")]
        let rows = transcriptRows(messages, detail: .reduced)
        XCTAssertEqual(rows.map(\.id), ["run.a", "plan.first", "run.c", "run.e"])
        for row in rows {
            if case let .activityRun(items) = row { XCTAssertTrue(items.allSatisfy { $0.tool?.name == "Bash" }) }
        }
        XCTAssertEqual(transcriptRows(messages, detail: .hidden).map(\.id), ["plan.first"])
    }

    func testIncrementalTasksBecomeOnePlanIncludingEmptyDeletionState() {
        let messages = [
            step("create", name: "TaskCreate", input: #"{"subject":"One"}"#, turn: "turn"),
            step("unknown", name: "TaskUpdate", input: #"{"taskId":"2","status":"done"}"#, turn: "turn"),
            step("delete", name: "TaskUpdate", input: #"{"taskId":"1","status":"deleted"}"#, turn: "turn"),
        ]
        XCTAssertFalse(isActivityReceipt(messages[0]))
        for detail in ActivityDetail.allCases {
            let rows = transcriptRows(messages, detail: detail)
            guard case let .plan(_, latest, state) = rows[0] else { return XCTFail("Missing incremental plan") }
            XCTAssertEqual(latest.id, "delete")
            XCTAssertTrue(state.items.isEmpty)
            XCTAssertEqual(rows.map(\.id), detail == .hidden ? ["plan.create"] : ["plan.create", "unknown"])
        }
    }

    func testMalformedPlanPreviewKeepsExistingActivitySemantics() {
        let malformed = step("bad", name: "TodoWrite", input: "{broken")
        XCTAssertTrue(isActivityReceipt(malformed))
        XCTAssertEqual(transcriptRows([malformed], detail: .full).map(\.id), ["bad"])
        XCTAssertEqual(transcriptRows([malformed], detail: .hidden).map(\.id), [])
    }

    func testRosterPlansRemainActivityAndUseLatestLabelsWithoutSpeaking() {
        var last = plan("last", status: "done", turn: "turn")
        last.tool?.name = "todo_write: finished"
        let messages = [text("answer"), plan("first", turn: "turn"), text("reply"), last]
        XCTAssertEqual(rosterPreview(messages, detail: .hidden), "reply")
        XCTAssertEqual(rosterPreview(messages, detail: .full), "todo_write: finished")
        XCTAssertEqual(rosterPreview(messages, detail: .reduced), "todo_write: finished")
        XCTAssertEqual(Walkie.settledReply(transcript: [text("reply"), last], baseline: [], busy: false), "reply")
        XCTAssertEqual(last.kind, .activity)
    }

    func testPlanUpdatesDoNotChangeCompletedNarrationFoldsOrLiveNarration() {
        var narration = text("narration")
        narration.turnId = "turn"
        var answer = text("answer")
        answer.turnId = "turn"
        answer.turnTerminal = true
        let messages = [text("user", role: .user), narration, plan("first", turn: "turn"), plan("last", status: "done", turn: "turn"), answer]
        XCTAssertEqual(transcriptRows(messages, detail: .hidden).map(\.id), ["user", "turn.turn", "plan.first", "answer"])
        XCTAssertEqual(liveNarration(Array(messages.dropLast()), busy: true, detail: .hidden).latest, "narration")
        XCTAssertEqual(liveNarration(messages, busy: true, detail: .hidden), .none)
    }
}
