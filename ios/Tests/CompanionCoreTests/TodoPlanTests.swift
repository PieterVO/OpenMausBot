import XCTest
@testable import CompanionCore

final class TodoPlanTests: XCTestCase {
    private func tool(_ name: String, _ input: String, output: String? = nil) -> ToolActivity {
        ToolActivity(name: name, output: output, input: input)
    }

    private func tool(_ input: String) -> ToolActivity { tool("TodoWrite", input) }

    private func activity(_ id: String, name: String, input: String, output: String? = nil) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: 1)
        message.tool = tool(name, input, output: output)
        return message
    }

    func testSnapshotShapesAndTextFallbacks() throws {
        let shapes: [(String, String, [String])] = [
            ("TodoWrite", #"{"todos":[{"content":"content","subject":"ignored"},{"subject":"subject"},{"title":"title"},{"text":"text"},{"description":"description"}]}"#, ["content", "subject", "title", "text", "description"]),
            ("update_plan", #"{"plan":[{"step":"step","content":"ignored"},{"content":"content"},{"text":"text"}],"explanation":"Working"}"#, ["step", "content", "text"]),
            ("acp_plan", #"{"entries":[{"content":"entry","priority":"high"}]}"#, ["entry"]),
            ("exec.ToDo_List", #"{"items":[{"text":"item","completed":true}]}"#, ["item"]),
        ]
        for (name, input, texts) in shapes {
            XCTAssertEqual(try XCTUnwrap(TodoPlan.parse(tool: tool(name, input))).items.map(\.text), texts)
        }
        for name in ["todowrite", "todo_write", "unrelated"] {
            XCTAssertNotNil(TodoPlan.parse(tool: tool(name, #"{"todos":[{"content":"Read"}]}"#)))
        }
        XCTAssertNil(TodoPlan.parse(tool: tool("write", #"{"items":[{"text":"Not a todo tool"}]}"#)))
    }

    func testEveryStatusAliasNormalizesAndUnknownDefaultsPending() {
        let groups: [(TodoItem.Status, [String])] = [
            (.done, ["completed", "complete", "done", "finished"]),
            (.active, ["in_progress", "in-progress", "in progress", "ACTIVE", "running", "doing", "current"]),
            (.pending, ["pending", "todo", "not_started", "open", "queued", "future-status", ""]),
            (.cancelled, ["cancelled", "canceled", "skipped", "deleted"]),
        ]
        for (status, aliases) in groups {
            for alias in aliases { XCTAssertEqual(TodoItem.Status.normalize(alias), status, alias) }
        }
    }

    func testStatusWinsOverBooleansAndBooleansAreStrict() throws {
        let plan = try XCTUnwrap(TodoPlan.parse(tool: tool(#"{"todos":[{"content":"a","status":"pending","completed":true},{"content":"b","completed":true},{"content":"c","completed":false,"done":true},{"content":"d","done":true},{"content":"e","done":false},{"content":"f","completed":1},{"content":"g","status":42,"done":true}]}"#)))
        XCTAssertEqual(plan.items.map(\.status), [.pending, .done, .pending, .done, .pending, .pending, .done])
    }

    func testActiveFormsAndIndexIDsSurviveSkippedAndTruncatedEntries() throws {
        let plan = try XCTUnwrap(TodoPlan.parse(tool: tool(#"{"todos":[{"content":"  Read files  ","status":"in_progress","activeForm":"Reading files","active_form":"ignored"},"[additional items omitted]",{"content":" "},null,42,{"content":"Test","active_form":"Testing"}]}"#)))
        XCTAssertEqual(plan.items.map(\.id), ["0", "5"])
        XCTAssertEqual(plan.items.map(\.text), ["Read files", "Test"])
        XCTAssertEqual(plan.items.map(\.activeText), ["Reading files", "Testing"])
        XCTAssertTrue(plan.truncated)
        XCTAssertEqual(plan.active?.activeText, "Reading files")
    }

    func testMalformedAndEmptySnapshotsDoNotInventPlans() {
        let inputs = ["", "[]", "null", "not JSON", #"{"todos":{}}"#, #"{"todos":[]}"#,
                      #"{"todos":["[additional items omitted]",{},null,{"content":false}]}"#,
                      "{\"todos\":[{\"content\":\"Read\"}]}\n[… preview shortened]"]
        for input in inputs { XCTAssertNil(TodoPlan.parse(tool: tool(input)), input) }
    }

    func testDerivedProgressExcludesCancelledAndRequiresNonzeroTotal() {
        let plan = TodoPlan(items: [.init(id: "a", text: "A", status: .done), .init(id: "b", text: "B", status: .cancelled), .init(id: "c", text: "C", status: .active), .init(id: "d", text: "D", status: .active)])
        XCTAssertEqual(plan.done, 1)
        XCTAssertEqual(plan.total, 3)
        XCTAssertEqual(plan.active?.id, "c")
        XCTAssertFalse(plan.isFinished)
        XCTAssertTrue(TodoPlan(items: [plan.items[0], plan.items[1]]).isFinished)
        XCTAssertFalse(TodoPlan(items: []).isFinished)
        XCTAssertFalse(TodoPlan(items: [plan.items[1]]).isFinished)
    }

    func testTaskFoldCreatesUpdatesDeletesAndIgnoresUnknownIDs() throws {
        let messages = [
            activity("create", name: "mcp__claude__TaskCreate", input: #"{"subject":"Read","description":"Long description","activeForm":"Reading"}"#, output: #"{"task":{"id":"8"}}"#),
            activity("update", name: "TaskUpdate", input: #"{"taskId":"8","subject":"Read code","status":"in_progress","activeForm":"Reading code"}"#),
            activity("unknown", name: "TaskUpdate", input: #"{"taskId":"9","status":"done"}"#),
            activity("complete", name: "TaskUpdate", input: #"{"taskId":"8","status":"completed"}"#),
            activity("delete", name: "TaskUpdate", input: #"{"taskId":"8","status":"deleted"}"#),
        ]
        let states = planStates(messages)
        XCTAssertEqual(Set(states.keys), ["create", "update", "complete", "delete"])
        XCTAssertEqual(states["create"]?.items.first?.text, "Read")
        XCTAssertEqual(states["create"]?.items.first?.activeText, "Reading")
        XCTAssertEqual(states["update"]?.active?.text, "Read code")
        XCTAssertEqual(states["update"]?.active?.activeText, "Reading code")
        XCTAssertTrue(try XCTUnwrap(states["complete"]).isFinished)
        XCTAssertEqual(states["delete"]?.items, [])
    }

    func testTaskIDsPreferJSONThenFirstHashThenMonotonicFallback() {
        let messages = [
            activity("a", name: "TaskCreate", input: #"{"subject":"A"}"#, output: #"{"task":{"id":12}}"#),
            activity("b", name: "TaskCreate", input: #"{"subject":"B"}"#, output: "Created #4, related #99"),
            activity("c", name: "TaskCreate", input: #"{"subject":"C"}"#),
            activity("delete", name: "TaskUpdate", input: #"{"taskId":"13","status":"deleted"}"#),
            activity("d", name: "TaskCreate", input: #"{"subject":"D"}"#, output: #"{"task":{"id":false}}"#),
        ]
        XCTAssertEqual(planStates(messages)["d"]?.items.map(\.id), ["12", "4", "14"])
        let huge = "999999999999999999999999"
        let states = planStates([
            activity("huge", name: "TaskCreate", input: #"{"subject":"Huge"}"#, output: "Created #\(huge)"),
            activity("next", name: "TaskCreate", input: #"{"subject":"Next"}"#),
        ])
        XCTAssertEqual(states["next"]?.items.last?.id, "1000000000000000000000000")
    }

    func testSnapshotsReplaceTaskStateButDoNotResetSeenIDs() {
        let messages = [
            activity("create", name: "TaskCreate", input: #"{"subject":"Old"}"#, output: "Created #20"),
            activity("snapshot", name: "TodoWrite", input: #"{"todos":[{"content":"New","status":"pending"},"omitted"]}"#),
            activity("update", name: "TaskUpdate", input: #"{"taskId":"0","status":"running"}"#),
            activity("another", name: "TaskCreate", input: #"{"subject":"Next"}"#),
        ]
        let states = planStates(messages)
        XCTAssertEqual(states["snapshot"]?.items.map(\.text), ["New"])
        XCTAssertEqual(states["update"]?.active?.id, "0")
        XCTAssertEqual(states["another"]?.items.last?.id, "21")
        XCTAssertEqual(states["another"]?.truncated, true)
    }

    func testTaskFoldsAreThreadLocalAndOnlyActivityMessagesBearPlans() {
        let create = activity("a", name: "TaskCreate", input: #"{"subject":"A"}"#)
        let update = activity("b", name: "TaskUpdate", input: #"{"taskId":"1","status":"done"}"#)
        XCTAssertEqual(planStates([create])["a"]?.items.first?.id, "1")
        XCTAssertTrue(planStates([update]).isEmpty)
        XCTAssertEqual(planStates([create])["a"]?.items.first?.id, "1")
        var text = create
        text.kind = .text
        let invalid = activity("invalid", name: "TaskCreate", input: "{broken")
        let lookalike = activity("lookalike", name: "TaskCreateStatus", input: #"{"subject":"No"}"#)
        XCTAssertTrue(planStates([text, invalid, lookalike]).isEmpty)
    }
}
