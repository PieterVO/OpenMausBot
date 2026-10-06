package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatPreferencesTest {
    private fun text(id: String, at: Double = 1.0): Message = Message(
        id = id,
        role = Message.Role.BOT,
        kind = Message.Kind.TEXT,
        at = at,
        text = "hello",
    )

    private fun activity(id: String, at: Double = 1.0, ok: Boolean? = true): Message = Message(
        id = id,
        role = Message.Role.BOT,
        kind = Message.Kind.ACTIVITY,
        at = at,
        tool = ToolActivity(name = "run", ok = ok),
    )

    private fun digest(id: String, at: Double = 1.0): Message = Message(
        id = id,
        role = Message.Role.BOT,
        kind = Message.Kind.DIGEST,
        at = at,
        text = "[digest] Bash ×2",
    )

    private fun compaction(id: String, at: Double = 1.0): Message = Message(
        id = id,
        role = Message.Role.BOT,
        kind = Message.Kind.COMPACTION,
        at = at,
        text = "[compaction] summary",
        compaction = Compaction(summary = "summary", tokensBefore = 10),
    )

    // Compaction follows activity detail; work summaries have their own opt-in.

    @Test
    fun statusNoticeIsNeverHiddenOrFolded() {
        // "Qwen hit a rate limit and is retrying" answers "is it stuck?"; it is not tool noise.
        val notice = activity("n").copy(tool = ToolActivity(name = "notice: Qwen is waiting on its model", ok = true))
        val messages = listOf(activity("a"), activity("b"), notice, activity("c"), activity("d"))
        assertEquals(listOf("n"), transcriptRows(messages, ActivityDetail.HIDDEN).map { it.id })
        val rows = transcriptRows(messages, ActivityDetail.REDUCED)
        assertEquals(3, rows.size)
        assertEquals("n", assertIs<TranscriptRow.Single>(rows[1]).id)
    }

    @Test
    fun failedTurnIsNeverHiddenButAFailedStepStillIs() {
        // The turn's own failure is the only sign the bot did not answer; a
        // failed step inside a turn that went on is tool noise like any other.
        val failedTurn = activity("e", ok = false).copy(
            tool = ToolActivity(name = "error: Not logged in · Please run /login", ok = false, setup = true),
        )
        val messages = listOf(text("a"), activity("b", ok = false), failedTurn)
        assertEquals(listOf("a", "e"), transcriptRows(messages, ActivityDetail.HIDDEN).map { it.id })
        assertTrue(isFailedTurn(failedTurn))
        assertFalse(isFailedTurn(activity("b", ok = false)))
        assertEquals("Not logged in · Please run /login", failedTurn.tool!!.label)
        assertEquals("run", activity("b").tool!!.label)
    }

    @Test
    fun hiddenDropsCompactionButShowsOptedInDigests() {
        val messages = listOf(text("a"), activity("b"), digest("c"), text("d"), compaction("e"))
        assertEquals(listOf("a", "d"), transcriptRows(messages, ActivityDetail.HIDDEN).map { it.id })
        assertEquals(listOf("a", "c", "d"), transcriptRows(messages, ActivityDetail.HIDDEN, showSummaries = true).map { it.id })
    }

    @Test
    fun theDigestIsItsOwnRowAndNeverAStep() {
        val messages = listOf(activity("a"), activity("b"), digest("c"), text("d"))
        val reduced = transcriptRows(messages, ActivityDetail.REDUCED, showSummaries = true)
        assertEquals(listOf("run.a", "c", "d"), reduced.map { it.id })
        assertEquals(2, (reduced[0] as TranscriptRow.ActivityRun).items.size)
        assertEquals(Message.Kind.DIGEST, reduced[1].kind)
        assertEquals(listOf("a", "b", "c", "d"), transcriptRows(messages, ActivityDetail.FULL, showSummaries = true).map { it.id })
        assertEquals(listOf("run.a", "d"), transcriptRows(messages, ActivityDetail.REDUCED).map { it.id })
        assertEquals(listOf("a", "b", "d"), transcriptRows(messages, ActivityDetail.FULL).map { it.id })
    }

    @Test
    fun theRosterPreviewReadsPastTheDigestToTheReply() {
        val messages = listOf(text("a"), digest("b"))
        assertEquals("hello", rosterPreview(messages, ActivityDetail.FULL))
        assertEquals("hello", rosterPreview(messages, ActivityDetail.REDUCED))
    }

    @Test
    fun quietTurnsDoNotLeaveAnEmptyDigestRowAtAnyActivityLevel() {
        val quiet = digest("quiet").copy(text = "[digest] · no tool activity observed in this turn · files: none changed · reply: hello")
        for (detail in ActivityDetail.entries) {
            assertEquals(listOf("answer"), transcriptRows(listOf(text("answer"), quiet), detail).map { it.id })
            assertEquals(listOf("answer"), transcriptRows(listOf(text("answer"), quiet), detail, showSummaries = true).map { it.id })
        }
        val changed = quiet.copy(text = "[digest] · no tool calls · files: changed notes.txt")
        for (detail in ActivityDetail.entries) {
            assertEquals(listOf("answer", "quiet"), transcriptRows(listOf(text("answer"), changed), detail, showSummaries = true).map { it.id })
            assertEquals(listOf("answer"), transcriptRows(listOf(text("answer"), changed), detail).map { it.id })
        }
    }

    @Test
    fun reducedKeepsACompactionAsItsOwnRowAndBreaksTheRun() {
        val messages = listOf(activity("a"), activity("b"), compaction("c"), activity("d"), activity("e"))
        val rows = transcriptRows(messages, ActivityDetail.REDUCED)
        assertEquals(listOf("run.a", "c", "run.d"), rows.map { it.id })
        assertEquals(Message.Kind.COMPACTION, rows[1].kind)
    }

    @Test
    fun fullKeepsEveryMessageAndHiddenDropsEveryActivity() {
        val messages = listOf(text("a"), activity("b"), activity("c", ok = false), text("d"))
        assertEquals(listOf("a", "b", "c", "d"), transcriptRows(messages, ActivityDetail.FULL).map { it.id })
        assertEquals(listOf("a", "d"), transcriptRows(messages, ActivityDetail.HIDDEN).map { it.id })
    }

    @Test
    fun reducedFoldsRunsButNeverFoldsFailuresOrSingleSteps() {
        val rows = transcriptRows(
            listOf(
                activity("a", at = 100.0),
                activity("b", at = 200.0),
                activity("failed", at = 300.0, ok = false),
                activity("single", at = 400.0),
                text("tail", at = 500.0),
            ),
            ActivityDetail.REDUCED,
        )

        val run = assertIs<TranscriptRow.ActivityRun>(rows[0])
        assertEquals(listOf("a", "b"), run.items.map(Message::id))
        assertEquals(100.0, run.at)
        assertEquals(200.0, run.endAt)
        assertFalse(run.running)
        assertEquals(listOf("run.a", "failed", "single", "tail"), rows.map { it.id })
        assertIs<TranscriptRow.Single>(rows[1])
        assertIs<TranscriptRow.Single>(rows[2])
    }

    @Test
    fun reducedRunReportsRunningWhenAReceiptHasNoVerdict() {
        val row = transcriptRows(
            listOf(activity("a"), activity("b", ok = null)),
            ActivityDetail.REDUCED,
        ).single()
        assertTrue(assertIs<TranscriptRow.ActivityRun>(row).running)
    }

    @Test
    fun quickRepliesRoundTripAndDistinguishEmptyListFromEmptyStore() {
        val mine = listOf(
            QuickReply(title = "Deploy", prompt = "Deploy to staging", icon = "send"),
            QuickReply(title = "Logs", prompt = "Show logs", icon = "document"),
        )
        assertEquals(mine, QuickReply.decode(QuickReply.encode(mine)))
        assertEquals(QuickReply.DEFAULTS, QuickReply.decode(""))
        assertEquals(QuickReply.DEFAULTS, QuickReply.decode("{not json"))
        assertEquals(emptyList(), QuickReply.decode(QuickReply.encode(emptyList())))
    }

    @Test
    fun invalidQuickReplyIdsFallBackToDefaults() {
        assertEquals(
            QuickReply.DEFAULTS,
            QuickReply.decode(QuickReply.encode(listOf(QuickReply(id = " ", title = "A", prompt = "A", icon = "next")))),
        )
        assertEquals(
            QuickReply.DEFAULTS,
            QuickReply.decode(
                QuickReply.encode(
                    listOf(
                        QuickReply(id = "same", title = "A", prompt = "A", icon = "next"),
                        QuickReply(id = "same", title = "B", prompt = "B", icon = "next"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun unknownActivityDetailUsesIosDefault() {
        // The phone reads like a normal chat until the reader chooses (2026-10-03).
        assertEquals(ActivityDetail.HIDDEN, ActivityDetail.fromWire("unknown"))
        assertEquals(ActivityDetail.HIDDEN, ActivityDetail.fromWire(null))
    }

    @Test
    fun summariesAreIndependentOfActivityAndProblemsAlwaysShow() {
        val healthy = digest("healthy").copy(text = "[digest] · tools: shell ×2 (0 failed)")
        val failedStep = digest("failed-step").copy(text = "[digest] · tools: shell ×2 (1 failed)")
        val failedTurn = digest("failed-turn").copy(turnSucceeded = false)
        val structuredProblem = digest("structured").copy(
            digest = StructuredTurnDigest(
                "turn", "bot", "thread", 1.0, 1_000.0, listOf(DigestTool("read", 1, 1)),
                emptyList(), "Done", "full",
            ),
        )
        for (detail in ActivityDetail.entries) {
            assertEquals(emptyList(), transcriptRows(listOf(healthy), detail))
            assertEquals(listOf("healthy"), transcriptRows(listOf(healthy), detail, showSummaries = true).map { it.id })
            for (problem in listOf(failedStep, failedTurn, structuredProblem)) {
                assertEquals(listOf(problem.id), transcriptRows(listOf(problem), detail).map { it.id })
            }
            val quiet = digest("quiet").copy(
                text = "[digest] · no tool calls · reply: Hello",
                turnSucceeded = false,
            )
            assertEquals(emptyList(), transcriptRows(listOf(quiet), detail, showSummaries = true))
        }
    }

    private fun planMessage(
        id: String,
        status: String = "pending",
        turn: String? = "turn",
        at: Double = 1.0,
    ): Message = activity(id, at).copy(
        turnId = turn,
        tool = ToolActivity("TodoWrite", ok = true, input = """{"todos":[{"content":"Task","status":"$status"}]}"""),
    )

    @Test
    fun planCardStaysAtFirstActivityWithLatestStateAtEveryDetailLevel() {
        val first = planMessage("first", at = 20.0)
        val later = planMessage("later", "active", at = 40.0)
        val latest = planMessage("latest", "completed", at = 60.0)
        val messages = listOf(text("before", 10.0), first, text("between", 30.0), later, latest, text("after", 70.0))
        for (detail in ActivityDetail.entries) {
            val rows = transcriptRows(messages, detail)
            assertEquals(listOf("before", "plan.first", "between", "after"), rows.map { it.id })
            val card = assertIs<TranscriptRow.Plan>(rows[1])
            assertEquals(latest, card.message)
            assertEquals(1, card.plan.done)
            assertTrue(card.plan.isFinished)
            assertEquals(20.0, card.at)
            assertEquals(60.0, card.endAt)
            assertTrue(card.containsMessage("first"))
            assertTrue(card.containsMessage("later"))
            assertTrue(card.containsMessage("latest"))
            assertFalse(card.containsMessage("between"))
        }
        assertFalse(isActivityReceipt(first))
        assertTrue(isActivityReceipt(activity("ordinary")))
        assertFalse(isActivityReceipt(digest("summary")))
    }

    @Test
    fun snapshotsAndIncrementalTasksShareOneTurnCard() {
        val snapshot = planMessage("snapshot")
        val created = activity("created").copy(turnId = "turn", tool = ToolActivity(
            "TaskCreate", input = """{"subject":"Second"}""", output = """{"task":{"id":"4"}}""",
        ))
        val updated = activity("updated").copy(turnId = "turn", tool = ToolActivity(
            "TaskUpdate", input = """{"taskId":"4","status":"in_progress","activeForm":"Working on second"}""",
        ))
        val ignored = updated.copy(id = "ignored", tool = ToolActivity("TaskUpdate", input = """{"taskId":"missing","status":"done"}"""))
        for (detail in ActivityDetail.entries) {
            val card = assertIs<TranscriptRow.Plan>(transcriptRows(listOf(snapshot, created, updated), detail).single())
            assertEquals("plan.snapshot", card.id)
            assertEquals(updated, card.message)
            assertEquals(2, card.plan.total)
            assertEquals("Working on second", card.plan.active?.activeText)
        }
        assertEquals(listOf("plan.snapshot"), transcriptRows(listOf(snapshot, ignored), ActivityDetail.HIDDEN).map { it.id })
        assertEquals(listOf("plan.snapshot", "ignored"), transcriptRows(listOf(snapshot, ignored), ActivityDetail.FULL).map { it.id })
    }

    @Test
    fun separateTurnsAndLegacyUserBoundariesGetSeparateCards() {
        val user1 = text("user1").copy(role = Message.Role.USER)
        val user2 = text("user2").copy(role = Message.Role.USER)
        val messages = listOf(
            planMessage("before-users", turn = null), user1,
            planMessage("legacy-first", turn = null), planMessage("legacy-update", "done", turn = null),
            planMessage("turn-a", turn = "a"), user2, planMessage("turn-a-update", "done", turn = "a"),
            planMessage("legacy-second", turn = null), planMessage("turn-b", turn = "b"),
        )
        for (detail in ActivityDetail.entries) {
            val rows = transcriptRows(messages, detail)
            assertEquals(
                listOf("plan.before-users", "user1", "plan.legacy-first", "plan.turn-a", "user2", "plan.legacy-second", "plan.turn-b"),
                rows.map { it.id },
            )
            assertEquals("legacy-update", assertIs<TranscriptRow.Plan>(rows[2]).message.id)
            assertEquals("turn-a-update", assertIs<TranscriptRow.Plan>(rows[3]).message.id)
        }
    }

    @Test
    fun everyPlanActivityBreaksReducedRunsEvenWhenItsLaterRowIsSuppressed() {
        val messages = listOf(
            activity("a"), activity("b"), planMessage("first"), activity("c"), activity("d"),
            planMessage("later", "done"), activity("e"), activity("f"),
        )
        val rows = transcriptRows(messages, ActivityDetail.REDUCED)
        assertEquals(listOf("run.a", "plan.first", "run.c", "run.e"), rows.map { it.id })
        for (row in rows.filterIsInstance<TranscriptRow.ActivityRun>()) assertEquals(2, row.items.size)
        assertEquals("later", assertIs<TranscriptRow.Plan>(rows[1]).message.id)
    }

    @Test
    fun plansDoNotChangeActivityPreviewsOrLiveNarration() {
        val user = text("user").copy(role = Message.Role.USER)
        val narration = text("narration").copy(turnId = "turn", text = "Checking the task")
        val plans = listOf(planMessage("first"), planMessage("latest", "active"))
        val messages = listOf(user, narration) + plans
        assertEquals("TodoWrite", rosterPreview(messages, ActivityDetail.FULL))
        assertEquals("Ran 2 steps", rosterPreview(messages, ActivityDetail.REDUCED))
        assertEquals("Checking the task", rosterPreview(messages, ActivityDetail.HIDDEN))
        assertEquals(LiveNarration(setOf("narration"), "Checking the task"), liveNarration(messages, busy = true, detail = ActivityDetail.HIDDEN))
        assertEquals(listOf("user", "narration", "plan.first"), transcriptRows(messages, ActivityDetail.HIDDEN).map { it.id })
    }
}
