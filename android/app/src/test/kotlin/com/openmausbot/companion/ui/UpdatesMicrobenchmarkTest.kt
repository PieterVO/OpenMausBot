package com.openmausbot.companion.ui

import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.ToolActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fixed fleet of 24 unread threads, each carrying the same 200-message workload. */
class UpdatesMicrobenchmarkTest {
    @Test
    fun largeFleetUpdates() {
        val messages = updatesBenchmarkMessages()
        assertEquals(200, messages.size)
        assertEquals(120, messages.count { it.kind == Message.Kind.ACTIVITY })
        assertTrue(messages.filter { it.kind == Message.Kind.ACTIVITY }.all { it.tool!!.input!!.length >= 4_096 })
        val bots = List(24) { bot(id = "bench-$it").copy(unread = true) }
        val state = CompanionState(bots = bots, messages = bots.associate { it.threadId to messages })
        for (detail in ActivityDetail.entries) {
            val expected = state.updates(detail)
            assertEquals(bots.map { it.threadId }, expected.map { it.chat.threadId })
            assertTrue(expected.all { it.kind == UpdateKind.TO_REVIEW && it.line == "Still checking 19" })
            repeat(WARMUP) { sink = state.updates(detail) }
            val samples = LongArray(SAMPLES) {
                val start = System.nanoTime()
                repeat(ITERATIONS) { sink = state.updates(detail) }
                (System.nanoTime() - start) / ITERATIONS
            }
            samples.sort()
            assertEquals(expected, sink)
            println("FOLD_BENCH name=updates.${detail.wireValue} threads=24 messages=200 tool_padding_chars=4096 warmup=$WARMUP samples=$SAMPLES iterations=$ITERATIONS median_ns=${samples[SAMPLES / 2]}")
        }
    }

    companion object {
        private const val WARMUP = 100
        private const val SAMPLES = 15
        private const val ITERATIONS = 50
        @Volatile private var sink: Any? = null
    }
}

// Keep this fixed workload identical to FoldMicrobenchmarkTest's generator.
private fun updatesBenchmarkMessages(): List<Message> = List(200) { index ->
    val turn = index / 10
    val offset = index % 10
    val input = if (offset == 2 && turn % 5 == 0) {
        """{"todos":[{"content":"Review turn $turn","status":"in_progress"}],"padding":"${"x".repeat(4_096)}"}"""
    } else {
        """{"command":"${"x".repeat(4_096)}"}"""
    }
    Message(
        id = index.toString(),
        role = if (offset == 0) Message.Role.USER else Message.Role.BOT,
        kind = when (offset) {
            in 2..7 -> Message.Kind.ACTIVITY
            8 -> Message.Kind.DIGEST
            else -> Message.Kind.TEXT
        },
        at = index * 1_000.0,
        text = when (offset) {
            0 -> "Review turn $turn"
            1 -> "Checking turn $turn"
            8 -> "[digest] · tools: shell ×6 (0 failed)"
            else -> if (turn == 19) "Still checking $turn" else "Finished turn $turn"
        },
        tool = if (offset in 2..7) ToolActivity(
            name = if (offset == 2 && turn % 5 == 0) "TodoWrite" else "shell",
            ok = true,
            input = input,
        ) else null,
        turnId = if (offset == 0) null else "turn-$turn",
        turnTerminal = if (offset == 9 && turn != 19) true else null,
    )
}
