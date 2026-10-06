package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChatSummaryTest {
    @Test
    fun sortsPinnedThenUnreadThenActivityAndHidesHiddenBots() {
        val state = CompanionState(
            bots = listOf(
                sampleBot("hidden", "th", hidden = true, unread = true, pinned = true),
                sampleBot("pinned", "tp", pinned = true, unread = false, at = 10.0),
                sampleBot("unread", "tu", pinned = false, unread = true, at = 5.0),
                sampleBot("old", "to", pinned = false, unread = false, at = 1.0),
                sampleBot("new", "tn", pinned = false, unread = false, at = 20.0),
            ),
            rooms = listOf(
                Room(
                    id = "r1",
                    threadId = "tr",
                    name = "Room",
                    memberIds = listOf("a", "b"),
                    defaultResponder = GroupResponder("bot", "a"),
                    bulletin = "",
                    unread = true,
                    createdAt = 1.0,
                ),
            ),
            messages = mapOf(
                "tp" to listOf(text("m1", 10.0, "pinned preview")),
                "tu" to listOf(text("m2", 5.0, "unread preview")),
                "to" to listOf(text("m3", 1.0, "old preview")),
                "tn" to listOf(text("m4", 20.0, "new preview")),
                "tr" to listOf(
                    Message(
                        id = "m5",
                        role = Message.Role.BOT,
                        kind = Message.Kind.OPTIONS,
                        at = 15.0,
                        card = OptionCard(
                            title = "Allow shell?",
                            subtitle = "",
                            options = listOf("Allow", "Deny"),
                            requestId = "req",
                        ),
                    ),
                ),
            ),
        )

        val summaries = state.chatSummaries()
        // pinned → unread (by activity: room@15 before bot@5) → read (new@20 before old@1)
        assertEquals(
            listOf("pinned", "r1", "unread", "new", "old"),
            summaries.map { it.id },
        )
        assertEquals("Allow shell?", summaries.first { it.id == "r1" }.preview)
        assertEquals(false, summaries.first { it.id == "r1" }.pinned)
        assertTrue(summaries.none { it.id == "hidden" })
    }

    @Test
    fun optionPreviewUsesThePendingQuestionOtherwiseTheTitle() {
        assertEquals(
            "Run the shell command?",
            optionPreview(OptionCard(
                title = "Allow shell?",
                subtitle = "Run the shell command?",
                options = listOf("Allow", "Deny"),
                requestId = "req",
            )),
        )
        assertEquals(
            "Allow shell?",
            optionPreview(OptionCard(
                title = "Allow shell?",
                subtitle = "",
                options = listOf("Allow", "Deny"),
                requestId = "req",
            )),
        )
        assertEquals(
            "   ",
            optionPreview(OptionCard(
                title = "Allow shell?",
                subtitle = "   ",
                options = listOf("Allow", "Deny"),
                requestId = "req",
            )),
        )
        assertEquals(
            "Choose a mode",
            optionPreview(OptionCard(
                title = "Choose a mode",
                subtitle = "Which mode should run?",
                options = listOf("Fast", "Safe"),
                answered = "Safe",
                requestId = "req",
            )),
        )
        assertEquals(
            "Dismissed question",
            optionPreview(OptionCard(
                title = "Dismissed question",
                subtitle = "Question details",
                options = listOf("Allow", "Deny"),
                dismissed = true,
                requestId = "req",
            )),
        )
        assertEquals("", optionPreview(null))
    }

    @Test
    fun previewRulesMatchIos() {
        val textState = CompanionState(
            bots = listOf(sampleBot("b", "t")),
            messages = mapOf("t" to listOf(text("m", 1.0, "hello"))),
        )
        assertEquals("hello", textState.chatSummaries().single().preview)

        val activity = CompanionState(
            bots = listOf(sampleBot("b", "t")),
            messages = mapOf(
                "t" to listOf(
                    Message(
                        id = "m",
                        role = Message.Role.BOT,
                        kind = Message.Kind.ACTIVITY,
                        at = 1.0,
                        tool = ToolActivity(name = "Bash"),
                    ),
                ),
            ),
        )
        assertEquals("Bash", activity.chatSummaries().single().preview)

        val screen = CompanionState(
            bots = listOf(sampleBot("b", "t")),
            messages = mapOf(
                "t" to listOf(
                    Message(
                        id = "m",
                        role = Message.Role.BOT,
                        kind = Message.Kind.SCREEN,
                        at = 1.0,
                    ),
                ),
            ),
        )
        assertEquals("Screenshot", screen.chatSummaries().single().preview)
    }

    @Test
    fun unchangedThreadsReuseProjectionsAcrossFleetFramesAndActivityChanges() {
        val first = listOf(text("first", 1.0, "First"), text("tail", 2.0, "Tail"))
        val second = listOf(text("other", 3.0, "Other"))
        val state = CompanionState(
            bots = listOf(sampleBot("a", "first"), sampleBot("b", "second")),
            messages = mapOf("first" to first, "second" to second),
        )
        val cache = ThreadProjectionCache()
        val firstProjection = cache.thread(state, "first")
        val secondProjection = cache.thread(state, "second")
        var current = state
        repeat(200) { frame ->
            current = state.copy(streaming = mapOf("second" to "Streaming $frame"))
            for (detail in ActivityDetail.entries) {
                assertEquals(current.chatSummaries(detail), current.chatSummaries(detail, cache))
            }
            assertSame(firstProjection, cache.thread(current, "first"))
            assertSame(secondProjection, cache.thread(current, "second"))
        }
        val changed = current.copy(messages = current.messages + ("second" to second + text("new", 4.0, "New")))
        assertSame(firstProjection, cache.thread(changed, "first"))
        assertNotSame(secondProjection, cache.thread(changed, "second"))
        assertEquals("New", changed.chatSummaries(ActivityDetail.HIDDEN, cache).first().preview)
    }

    @Test
    fun projectionInvalidatesOnLeafOverridesAndPendingEditsWithoutRawListChanges() {
        val raw = listOf(
            text("root", 1.0, "Root"),
            text("left", 2.0, "Left").copy(parentId = "root"),
            text("right", 3.0, "Right").copy(parentId = "root"),
        )
        val state = CompanionState(
            bots = listOf(sampleBot("a", "thread").copy(activeLeafId = "left")),
            messages = mapOf("thread" to raw),
        )
        val cache = ThreadProjectionCache()
        val left = cache.thread(state, "thread")
        assertEquals(listOf("root", "left"), left.messages.map { it.id })
        val rightState = state.copy(activeLeafIds = mapOf("thread" to "right"))
        val right = cache.thread(rightState, "thread")
        assertNotSame(left, right)
        assertEquals("Right", right.preview(ActivityDetail.HIDDEN))
        // A present null overrides the bot's leaf and exposes the full loaded history.
        val full = cache.thread(state.copy(activeLeafIds = mapOf("thread" to null)), "thread")
        assertEquals(raw, full.messages)
        assertNotSame(right, full)
        val edit = PendingEdit("root", "Edited", at = 4.0)
        val editing = state.copy(pendingEdits = mapOf("thread" to edit))
        val edited = cache.thread(editing, "thread")
        assertNotSame(full, edited)
        assertEquals(listOf(edit.placeholderId), edited.messages.map { it.id })
        assertEquals("Edited", edited.preview(ActivityDetail.HIDDEN))
        assertSame(edited, cache.thread(editing.copy(streaming = mapOf("thread" to "Next")), "thread"))
        assertEquals("Left", cache.thread(state, "thread").preview(ActivityDetail.HIDDEN))
    }

    @Test
    fun cachedApprovalsKeepHiddenAndInternalThreadsAndPruneRemovedThreads() {
        fun approval(id: String, at: Double) = Message(
            id, Message.Role.BOT, Message.Kind.OPTIONS, at,
            card = OptionCard("Allow?", "", listOf("Allow"), requestId = id),
        )
        val state = CompanionState(
            bots = listOf(sampleBot("hidden", "selected", hidden = true).copy(
                tasks = listOf(BotTask("internal", "Internal", 1.0, routineRunId = "run")),
            )),
            messages = mapOf(
                "selected" to listOf(approval("old", 1.0)),
                "internal" to listOf(approval("new", 2.0)),
            ),
        )
        val cache = ThreadProjectionCache()
        val internal = cache.thread(state, "internal")
        assertEquals(state.pendingApprovals, cache.pendingApprovals(state))
        assertEquals(listOf("new", "old"), cache.pendingApprovals(state).map { it.message.id })
        assertEquals(emptyList(), cache.pendingApprovals(CompanionState()))
        assertNotSame(internal, cache.thread(state, "internal"))
    }

    @Test
    fun inheritedLeavesFollowTheSelectedThreadWithoutLeakingIntoSiblings() {
        val raw = listOf(
            text("root", 1.0, "Root"),
            text("left", 2.0, "Left").copy(parentId = "root"),
            text("right", 3.0, "Right").copy(parentId = "root"),
        )
        val owner = sampleBot("owner", "selected").copy(
            activeLeafId = "left",
            tasks = listOf(BotTask("selected", "Selected", 1.0), BotTask("sibling", "Sibling", 2.0)),
        )
        val state = CompanionState(
            bots = listOf(owner),
            messages = mapOf("selected" to raw, "sibling" to raw),
        )
        val cache = ThreadProjectionCache()
        assertEquals(listOf("root", "left"), cache.thread(state, "selected").messages.map { it.id })
        assertEquals(raw, cache.thread(state, "sibling").messages)
        val switched = state.copy(bots = listOf(owner.copy(threadId = "sibling", activeLeafId = "right")))
        assertEquals(raw, cache.thread(switched, "selected").messages)
        assertEquals(listOf("root", "right"), cache.thread(switched, "sibling").messages.map { it.id })
        for (threadId in listOf("selected", "sibling")) {
            assertEquals(switched.visibleTranscript(threadId), cache.thread(switched, threadId).messages)
        }
    }
}

private fun sampleBot(
    id: String,
    threadId: String,
    hidden: Boolean? = null,
    unread: Boolean = false,
    pinned: Boolean? = null,
    at: Double = 0.0,
) = Bot(
    id = id,
    threadId = threadId,
    name = id,
    title = "role",
    description = "",
    notifications = true,
    color = "green",
    unread = unread,
    modelSelection = ModelSelection("i", "m"),
    createdAt = at,
    pinned = pinned,
    hidden = hidden,
)

private fun text(id: String, at: Double, body: String) = Message(
    id = id,
    role = Message.Role.USER,
    kind = Message.Kind.TEXT,
    at = at,
    text = body,
)

private fun optionPreview(card: OptionCard?): String {
    val state = CompanionState(
        bots = listOf(sampleBot("b", "t")),
        messages = mapOf(
            "t" to listOf(Message(
                id = "m",
                role = Message.Role.BOT,
                kind = Message.Kind.OPTIONS,
                at = 1.0,
                card = card,
            )),
        ),
    )
    return state.chatSummaries().single().preview
}
