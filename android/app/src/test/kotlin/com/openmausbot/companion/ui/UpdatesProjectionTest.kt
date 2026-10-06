package com.openmausbot.companion.ui

import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.PendingEdit
import com.openmausbot.companion.core.ThreadProjectionCache
import com.openmausbot.companion.core.ToolActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UpdatesProjectionTest {
    @Test
    fun busyFleetFramesDoNotWalkUnchangedTranscriptsAgain() {
        val histories = List(24) { index -> CountingMessages(listOf(
            text("reply-$index", "Reply $index"),
            step("step-$index", "shell"),
            step("tail-$index", "read"),
        )) }
        val bots = List(24) { bot(id = "cached-$it", busy = it == 0).copy(unread = true) }
        val state = CompanionState(
            bots = bots,
            messages = bots.mapIndexed { index, bot -> bot.threadId to histories[index] }.toMap(),
        )
        val projections = ThreadProjectionCache()
        val faces = RosterFaceCache()
        val updates = state.updates(ActivityDetail.HIDDEN, projections)
        assertEquals(24, updates.size)
        assertEquals("Working…", updates.first().line)
        assertEquals("Reply 23", updates.last().line)
        bots.forEach { faces.face(Chat.BotChat(it), state, projections) }
        val reads = histories.map { it.reads }
        repeat(200) { frame ->
            val current = state.copy(streaming = mapOf(bots.first().threadId to "Streaming $frame"))
            val currentUpdates = current.updates(ActivityDetail.HIDDEN, projections)
            assertEquals("Streaming $frame", currentUpdates.first().line)
            assertEquals(updates.drop(1), currentUpdates.drop(1))
            bots.forEach { faces.face(Chat.BotChat(it), current, projections) }
        }
        assertEquals(reads, histories.map { it.reads }, "Stream frames must not re-walk any unchanged thread")
        for (detail in ActivityDetail.entries) {
            state.updates(detail, projections)
            val afterFirstDetail = histories.map { it.reads }
            repeat(20) { state.updates(detail, projections) }
            assertEquals(afterFirstDetail, histories.map { it.reads }, "A detail preview is derived once per thread")
        }
    }

    @Test
    fun cachedReviewLinesKeepHiddenReducedFullAndSummaryRules() {
        val problem = Message(
            "problem", Message.Role.BOT, Message.Kind.DIGEST, 3.0,
            text = "[digest] · tools: shell ×2 (1 failed)",
        )
        val healthy = problem.copy(id = "healthy", text = "[digest] · tools: shell ×2 (0 failed)")
        val thread = bot().threadId
        val state = CompanionState(
            bots = listOf(bot().copy(unread = true)),
            messages = mapOf(thread to listOf(text("reply", "Ready"), step("a", "shell"), problem, step("b", "read"), step("c", "write"), healthy)),
        )
        val cache = ThreadProjectionCache()
        assertEquals("Ready", state.updates(ActivityDetail.HIDDEN, cache).single().line)
        assertEquals("Ran 2 steps", state.updates(ActivityDetail.REDUCED, cache).single().line)
        assertEquals("write", state.updates(ActivityDetail.FULL, cache).single().line)
        for (detail in ActivityDetail.entries) {
            assertEquals(state.updates(detail), state.updates(detail, cache))
        }
    }

    @Test
    fun pendingCardsEditsAndLeafMovesInvalidateOnlyTheirThread() {
        val owner = bot().copy(unread = true, activeLeafId = "answer")
        val other = bot(id = "other").copy(unread = true)
        val approval = Message(
            "approval", Message.Role.BOT, Message.Kind.OPTIONS, 2.0, parentId = "root",
            card = OptionCard("Allow?", "Run it", listOf("Allow"), requestId = "request"),
        )
        val state = CompanionState(
            bots = listOf(owner, other),
            messages = mapOf(
                owner.threadId to listOf(text("root", "Question"), approval, text("answer", "Ready").copy(parentId = "root")),
                other.threadId to listOf(text("other", "Other")),
            ),
        )
        val cache = ThreadProjectionCache()
        val initial = cache.thread(state, owner.threadId)
        val unaffected = cache.thread(state, other.threadId)
        assertTrue(state.updates(ActivityDetail.HIDDEN, cache).all { it.kind == UpdateKind.TO_REVIEW })
        val moved = state.copy(activeLeafIds = mapOf(owner.threadId to "approval"))
        val waiting = moved.updates(ActivityDetail.HIDDEN, cache).first()
        assertEquals(UpdateKind.NEEDS_YOU, waiting.kind)
        assertEquals("Run it", waiting.line)
        assertEquals(approval.card, waiting.card)
        assertNotSame(initial, cache.thread(moved, owner.threadId))
        assertSame(unaffected, cache.thread(moved, other.threadId))
        val editing = moved.copy(pendingEdits = mapOf(owner.threadId to PendingEdit("root", "Edited", at = 4.0)))
        assertEquals("Edited", editing.updates(ActivityDetail.HIDDEN, cache).first().line)
        assertTrue(editing.updates(ActivityDetail.HIDDEN, cache).all { it.kind == UpdateKind.TO_REVIEW })
        assertSame(unaffected, cache.thread(editing, other.threadId))
    }

    @Test
    fun cachedFacesFollowRuntimeProfileAndLastMessageChanges() {
        val owner = bot().copy(title = "", description = "", busy = false, unread = false)
        val state = CompanionState(bots = listOf(owner), messages = mapOf(owner.threadId to listOf(text("reply", "Ready"))))
        val projections = ThreadProjectionCache()
        val faces = RosterFaceCache()
        fun face(current: CompanionState) = faces.face(Chat.BotChat(current.bots.single()), current, projections)
        assertEquals(MausState.IDLE, face(state))
        assertEquals(MausState.WORKING, face(state.copy(bots = listOf(owner.copy(busy = true)))))
        assertEquals(MausState.NOTIFYING, face(state.copy(bots = listOf(owner.copy(unread = true)))))
        val failed = state.copy(messages = mapOf(owner.threadId to listOf(step("failure", "shell").copy(tool = ToolActivity("shell", ok = false)))))
        assertEquals(MausState.ALERTING, face(failed))
        assertEquals(MausState.HAPPY, face(failed.copy(bots = listOf(owner.copy(mascotExpression = "friendly")))))
        assertEquals(MausState.SEARCHING, face(state.copy(bots = listOf(owner.copy(description = "researcher")))))
    }

    private class CountingMessages(private val messages: List<Message>) : AbstractList<Message>() {
        var reads = 0
            private set
        override val size: Int get() = messages.size
        override fun get(index: Int): Message {
            reads++
            return messages[index]
        }
    }

    private fun text(id: String, body: String) = Message(id, Message.Role.BOT, Message.Kind.TEXT, 1.0, text = body)
    private fun step(id: String, name: String) = Message(id, Message.Role.BOT, Message.Kind.ACTIVITY, 2.0, tool = ToolActivity(name, ok = true))
}
