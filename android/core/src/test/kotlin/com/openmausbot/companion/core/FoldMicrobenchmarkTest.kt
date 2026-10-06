package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fixed JVM workload: 20 turns, 200 messages, 120 tool inputs of about 4 KiB. */
class FoldMicrobenchmarkTest {
    @Test
    fun largeThreadFolds() {
        val messages = foldBenchmarkMessages()
        assertEquals(200, messages.size)
        assertEquals(120, messages.count { it.kind == Message.Kind.ACTIVITY })
        assertTrue(messages.filter { it.kind == Message.Kind.ACTIVITY }.all { it.tool!!.input!!.length >= 4_096 })
        for (detail in ActivityDetail.entries) {
            val expected = transcriptRows(messages, detail)
            assertEquals(20, expected.count { it.role == Message.Role.USER })
            assertEquals(4, expected.count { it is TranscriptRow.Plan })
            assertEquals("Still checking 19", rosterPreview(messages, detail))
            benchmark("transcriptRows.${detail.wireValue}") { transcriptRows(messages, detail) }
            assertEquals(expected, sink)
            benchmark("rosterPreview.${detail.wireValue}") { rosterPreview(messages, detail) }
            assertEquals("Still checking 19", sink)
        }
        val expected = LiveNarration(setOf("191", "199"), "Still checking 19")
        assertEquals(expected, liveNarration(messages, busy = true, ActivityDetail.HIDDEN))
        benchmark("liveNarration.hidden") { liveNarration(messages, busy = true, ActivityDetail.HIDDEN) }
        assertEquals(expected, sink)
    }

    private fun benchmark(name: String, operation: () -> Any) {
        repeat(WARMUP) { sink = operation() }
        val samples = LongArray(SAMPLES) {
            val start = System.nanoTime()
            repeat(ITERATIONS) { sink = operation() }
            (System.nanoTime() - start) / ITERATIONS
        }
        samples.sort()
        println("FOLD_BENCH name=$name messages=200 tool_padding_chars=4096 warmup=$WARMUP samples=$SAMPLES iterations=$ITERATIONS median_ns=${samples[SAMPLES / 2]}")
    }

    companion object {
        private const val WARMUP = 100
        private const val SAMPLES = 15
        private const val ITERATIONS = 50
        @Volatile private var sink: Any? = null
    }
}

// Keep this fixed workload identical to UpdatesMicrobenchmarkTest's generator.
private fun foldBenchmarkMessages(): List<Message> = List(200) { index ->
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
