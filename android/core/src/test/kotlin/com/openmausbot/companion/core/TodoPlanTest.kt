package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoPlanTest {
    private fun parse(input: String?, name: String = "TodoWrite"): TodoPlan? =
        TodoPlan.parse(ToolActivity(name, input = input))

    private fun activity(id: String, name: String, input: String?, output: String? = null): Message = Message(
        id, Message.Role.BOT, Message.Kind.ACTIVITY, 1.0,
        tool = ToolActivity(name, input = input, output = output),
    )

    @Test
    fun readsEveryProviderSnapshotShapeAndTextFallback() {
        val todos = assertNotNull(parse("""{"todos":[
            {"content":"Content"},{"subject":"Subject"},{"title":"Title"},
            {"text":"Text"},{"description":"Description"}
        ]}"""))
        assertEquals(listOf("Content", "Subject", "Title", "Text", "Description"), todos.items.map { it.text })
        assertEquals(listOf("0", "1", "2", "3", "4"), todos.items.map { it.id })
        for (name in listOf("TodoWrite", "todowrite", "todo_write")) {
            assertEquals(todos, parse("""{"todos":[
                {"content":"Content"},{"subject":"Subject"},{"title":"Title"},
                {"text":"Text"},{"description":"Description"}
            ]}""", name))
        }
        assertEquals(
            listOf("Step", "Content", "Text"),
            assertNotNull(parse("""{"plan":[{"step":"Step"},{"content":"Content"},{"text":"Text"}]}""", "update_plan"))
                .items.map { it.text },
        )
        assertEquals("ACP", assertNotNull(parse("""{"entries":[{"content":"ACP","priority":"high"}]}""", "acp_plan")).items.single().text)
        assertEquals(TodoStatus.DONE, assertNotNull(parse("""{"items":[{"text":"Exec","completed":true}]}""", "TODO_list")).items.single().status)
        assertNull(parse("""{"items":[{"text":"Not a plan"}]}""", "search"))
        assertNull(parse("""{"entries":[{"text":"ACP requires content"}]}""", "acp_plan"))
        assertNull(parse("""{"plan":[{"title":"Codex requires step/content/text"}]}""", "update_plan"))
    }

    @Test
    fun normalizesEveryStatusSpellingAndBooleanFallback() {
        val spellings = mapOf(
            TodoStatus.DONE to listOf("completed", "complete", "done", "finished"),
            TodoStatus.ACTIVE to listOf("in_progress", "active", "running", "doing", "current", "IN-PROGRESS", "in progress"),
            TodoStatus.PENDING to listOf("pending", "todo", "not_started", "open", "queued", "future-state"),
            TodoStatus.CANCELLED to listOf("cancelled", "canceled", "skipped", "deleted"),
        )
        for ((status, names) in spellings) for (name in names) {
            assertEquals(status, assertNotNull(parse("""{"todos":[{"content":"Task","status":"$name"}]}""")).items.single().status, name)
        }
        val plan = assertNotNull(parse("""{"todos":[
            {"content":"completed","completed":true},
            {"content":"done","done":true},
            {"content":"pending","completed":false,"done":true},
            {"content":"status wins","status":"active","completed":true},
            {"content":"malformed status","status":{},"done":true},
            {"content":"quoted boolean","completed":"true"}
        ]}"""))
        assertEquals(
            listOf(TodoStatus.DONE, TodoStatus.DONE, TodoStatus.PENDING, TodoStatus.ACTIVE, TodoStatus.DONE, TodoStatus.PENDING),
            plan.items.map { it.status },
        )
    }

    @Test
    fun skipsEmptyAndMalformedItemsWithoutRenumberingAndMarksStringOmissions() {
        val plan = assertNotNull(parse("""{"todos":[
            {"content":"  First  ","activeForm":"Working","status":"in_progress"},
            {"content":"  "},"[additional items omitted]",null,42,
            {"content":"Last","active_form":"Finishing","status":"active"}
        ]}"""))
        assertEquals(listOf("0", "5"), plan.items.map { it.id })
        assertEquals(listOf("First", "Last"), plan.items.map { it.text })
        assertEquals(listOf("Working", "Finishing"), plan.items.map { it.activeText })
        assertEquals("First", plan.active?.text)
        assertTrue(plan.truncated)
        for (input in listOf(null, "", "{", "[]", "null", "{}", """{"todos":{}}""", """{"todos":[]}""", """{"todos":["[additional items omitted]",{"content":""}]}""")) {
            assertNull(parse(input), input)
        }
        assertNull(parse("""{"todos":[{"content":"Task"}]}""" + "\n[… preview shortened]"))
    }

    @Test
    fun derivedProgressExcludesCancelledAndNeverFinishesAnEmptyPlan() {
        val plan = TodoPlan(listOf(
            TodoItem("1", "Done", status = TodoStatus.DONE),
            TodoItem("2", "Cancelled", status = TodoStatus.CANCELLED),
            TodoItem("3", "Active", status = TodoStatus.ACTIVE),
        ))
        assertEquals(1, plan.done)
        assertEquals(2, plan.total)
        assertEquals("3", plan.active?.id)
        assertFalse(plan.isFinished)
        assertTrue(plan.copy(items = plan.items.map { if (it.status == TodoStatus.ACTIVE) it.copy(status = TodoStatus.DONE) else it }).isFinished)
        assertFalse(TodoPlan(emptyList()).isFinished)
        assertFalse(TodoPlan(listOf(TodoItem("1", "Cancelled", status = TodoStatus.CANCELLED))).isFinished)
    }

    @Test
    fun foldsClaudeCreatesUpdatesDeletesAndAllIdSourcesInTranscriptOrder() {
        val messages = listOf(
            activity("json", "mcp__claude__TaskCreate", """{"subject":"First","description":"Details","activeForm":"Starting"}""", """{"task":{"id":"7"}}"""),
            activity("text", "TaskCreate", """{"subject":"Second"}""", "Task #12 created; related #99"),
            activity("auto", "TaskCreate", """{"subject":"Third"}"""),
            activity("active", "TaskUpdate", """{"taskId":"7","status":"in_progress"}"""),
            activity("rename", "mcp__TaskUpdate", """{"taskId":"7","subject":"Renamed","activeForm":"Renaming"}"""),
            activity("done", "TaskUpdate", """{"taskId":"7","status":"completed"}"""),
            activity("delete", "TaskUpdate", """{"taskId":"13","status":"deleted"}"""),
            activity("next", "TaskCreate", """{"subject":"Fourth"}""", "not JSON and no id"),
        )
        val states = planStates(messages)
        assertEquals(messages.map { it.id }, states.keys.toList())
        assertEquals(listOf("7"), states.getValue("json").items.map { it.id })
        assertEquals(listOf("7", "12", "13"), states.getValue("auto").items.map { it.id })
        assertEquals("Starting", states.getValue("active").active?.activeText)
        assertEquals("Renamed", states.getValue("rename").active?.text)
        assertEquals("Renaming", states.getValue("rename").active?.activeText)
        assertEquals(TodoStatus.DONE, states.getValue("done").items.first().status)
        assertEquals(listOf("7", "12"), states.getValue("delete").items.map { it.id })
        assertEquals(listOf("7", "12", "14"), states.getValue("next").items.map { it.id })
        // Stored states are immutable snapshots, not references to the final list.
        assertEquals("First", states.getValue("json").items.single().text)
        assertEquals(TodoStatus.PENDING, states.getValue("json").items.single().status)
    }

    @Test
    fun snapshotsReplaceTaskStateAndUserTurnsDoNotResetIt() {
        val create = activity("create", "TaskCreate", """{"subject":"Old"}""", "Task #9")
        val user = Message("user", Message.Role.USER, Message.Kind.TEXT, 2.0, text = "Continue")
        val update = activity("update", "TaskUpdate", """{"taskId":"9","status":"active"}""").copy(turnId = "new-turn")
        val snapshot = activity("snapshot", "update_plan", """{"plan":[{"step":"Replacement","status":"completed"}]}""")
        val after = activity("after", "TaskCreate", """{"subject":"Next"}""")
        val states = planStates(listOf(create, user, update, snapshot, after))
        assertEquals(listOf("create", "update", "snapshot", "after"), states.keys.toList())
        assertEquals("9", states.getValue("update").active?.id)
        assertEquals(listOf("0"), states.getValue("snapshot").items.map { it.id })
        assertEquals(listOf("0", "10"), states.getValue("after").items.map { it.id })
        assertEquals("1", planStates(listOf(after)).getValue("after").items.single().id)
    }

    @Test
    fun ignoredOrMalformedTaskOperationsDoNotBecomePlanBearing() {
        val create = activity("create", "TaskCreate", """{"subject":"One"}""")
        val bad = listOf(
            activity("unknown", "TaskUpdate", """{"taskId":"999","status":"done"}"""),
            activity("empty", "TaskUpdate", """{"taskId":"1"}"""),
            activity("missing-id", "TaskUpdate", """{"status":"done"}"""),
            activity("numeric-id", "TaskUpdate", """{"taskId":1,"status":"done"}"""),
            activity("bad-create", "TaskCreate", """{"subject":" "}"""),
            activity("invalid-json", "TaskUpdate", "{broken"),
            activity("not-suffix", "TaskUpdateReport", """{"taskId":"1","status":"done"}"""),
            activity("ordinary", "read", """{"path":"file"}"""),
            create.copy(id = "not-activity", kind = Message.Kind.TEXT),
        )
        assertEquals(listOf("create"), planStates(listOf(create) + bad).keys.toList())
        val deleted = activity("deleted", "TaskUpdate", """{"taskId":"1","status":"deleted"}""")
        assertEquals(emptyList(), planStates(listOf(create, deleted)).getValue("deleted").items)
    }

    @Test
    fun taskIdUsesJsonThenTextThenLargestNumericIdAndCancellationKeepsTheItem() {
        val messages = listOf(
            activity("numeric", "TaskCreate", """{"subject":"Numeric"}""", """{"task":{"id":5},"note":"#99"}"""),
            activity("string", "TaskCreate", """{"subject":"String"}""", """{"task":{"id":"external"}}"""),
            activity("invalid", "TaskCreate", """{"subject":"Invalid id"}""", """{"task":{"id":true}}"""),
            activity("cancelled", "TaskUpdate", """{"taskId":"5","status":"cancelled"}"""),
        )
        val states = planStates(messages)
        val final = states.getValue("cancelled")
        assertEquals(listOf("5", "external", "6"), final.items.map { it.id })
        assertEquals(TodoStatus.CANCELLED, final.items.first().status)
        assertEquals(2, final.total)
        assertEquals(0, final.done)
        assertFalse(final.isFinished)
        assertNull(final.active)
    }
}
