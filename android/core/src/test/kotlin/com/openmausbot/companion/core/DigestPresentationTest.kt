package com.openmausbot.companion.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DigestPresentationTest {
    private fun evidence() = StructuredTurnDigest(
        turnId = "turn", botId = "bot", threadId = "thread", at = 1.0, durationMs = 72_000.0,
        tools = listOf(DigestTool("shell", 3, 0, "git status"), DigestTool("edit", 1, 0)),
        memory = listOf(DigestMemory("MEMORY.md", "updated")), reply = "Done", hookCoverage = "full",
        files = DigestFiles(changed = listOf("src/a.kt"), added = listOf("src/b.kt")),
        usage = DigestUsage(input = 10_000, output = 2_300, cachedInput = 4_000, costUsd = 0.04),
    )

    private fun message(digest: StructuredTurnDigest? = evidence(), text: String? = null) = Message(
        "digest", Message.Role.BOT, Message.Kind.DIGEST, 1.0, text = text, digest = digest,
    )

    @Test
    fun structuredEvidenceSuppliesEveryStatAndTheSlimLine() {
        val digest = evidence()
        val presentation = DigestPresentation.from(message(digest, "[digest] · tools: incorrect ×99 (9 failed)"))
        assertEquals("1m 12s", presentation.duration)
        assertEquals(4, presentation.toolCalls)
        assertEquals(0, presentation.failedCalls)
        assertEquals(digest.files, presentation.files)
        assertEquals(digest.memory, presentation.memory)
        assertEquals(digest.tools, presentation.tools)
        assertEquals(12_300L, presentation.tokens)
        assertEquals(0.04, presentation.costUsd)
        assertNull(presentation.coverageNote)
        assertEquals("Worked 1m 12s · 4 tools · 2 files", presentation.slimText)
        assertFalse(presentation.hasProblem)
        assertFalse(presentation.isEmpty)
        assertEquals(listOf("tools", "files", "memory"), presentation.sections.map { it.label })
    }

    @Test
    fun failureComesFirstAndExplicitCallCountIncludesDroppedTools() {
        val digest = evidence().copy(
            durationMs = 40_000.0, tools = listOf(DigestTool("shell", 1, 1, "false")),
            toolCalls = 3, toolsDropped = 2, files = null, memory = emptyList(), memoryDropped = 4,
            hookCoverage = "preview", usage = null,
        )
        val presentation = DigestPresentation.from(message(digest))
        assertEquals("1 failed step · Worked 40s · 3 tools", presentation.slimText)
        assertEquals(1, presentation.failedCalls)
        assertEquals(3, presentation.toolCalls)
        assertTrue(presentation.hasProblem)
        assertEquals(DigestPresentation.PREVIEW_COVERAGE_NOTE, presentation.coverageNote)
        assertNull(presentation.tokens)
        assertNull(presentation.costUsd)
        assertEquals("+2 more tools", presentation.sections[1].items.single())
        assertEquals("+4 more memory changes", presentation.sections[2].items.single())
        assertEquals("2 failed steps · Worked 40s · 3 tools", DigestPresentation.from(message(digest.copy(tools = listOf(DigestTool("shell", 2, 2))))).slimText)
    }

    @Test
    fun durationSupportsSecondsMinutesHoursAndZeroPartsAreOmitted() {
        val base = evidence().copy(files = null, memory = emptyList())
        for ((milliseconds, duration) in listOf(
            40_000.0 to "40s", 72_000.0 to "1m 12s", 7_380_000.0 to "2h 3m",
            3_600_000.0 to "1h", 60_000.0 to "1m", 999.0 to "0s",
        )) {
            assertEquals(duration, DigestPresentation.from(message(base.copy(durationMs = milliseconds))).duration)
        }
        val noDuration = DigestPresentation.from(message(base.copy(durationMs = 0.0, tools = listOf(DigestTool("read", 1, 0)))))
        assertNull(noDuration.duration)
        assertEquals("1 tool", noDuration.slimText)
        val fileOnly = DigestPresentation.from(message(base.copy(durationMs = 0.0, tools = emptyList(), files = DigestFiles(deleted = listOf("old.kt")))))
        assertEquals("1 file", fileOnly.slimText)
        assertFalse(fileOnly.isEmpty)
        assertEquals("Turn failed · 1 tool", DigestPresentation.from(message(base.copy(durationMs = 0.0, tools = listOf(DigestTool("read", 1, 0)))).copy(turnSucceeded = false)).slimText)
    }

    @Test
    fun quietStructuredReceiptsStayEmptyDespiteReplyDurationAndTokenUsage() {
        val quiet = evidence().copy(tools = emptyList(), memory = emptyList(), files = null)
        assertTrue(DigestPresentation.from(message(quiet)).isEmpty)
        assertTrue(DigestPresentation.from(message(quiet).copy(turnSucceeded = false)).isEmpty)
        for (digest in listOf(
            quiet.copy(toolCalls = 1), quiet.copy(toolsDropped = 1), quiet.copy(memoryDropped = 1),
            quiet.copy(files = DigestFiles(truncated = 3)), quiet.copy(memory = listOf(DigestMemory("note", "created"))),
            quiet.copy(tools = listOf(DigestTool("failed", 0, 1))),
        )) assertFalse(DigestPresentation.from(message(digest)).isEmpty)
    }

    @Test
    fun fallbackPreservesTextSectionsAndDerivesFilesMemoryFailureAndCoverage() {
        val presentation = DigestPresentation.from(message(null,
            "[digest] · tools: ls -la /tmp, ~/x ×2 (1 failed), read ×1 +2 more (from tool previews) · " +
                "files: changed src/a.kt, src/b.kt; added new.kt; deleted old.kt; +3 more paths · " +
                "memory: created notes/start.md, updated MEMORY.md, deleted notes/old.md · +2 more memory changes · reply: Done",
        ))
        assertNull(presentation.duration)
        assertEquals(3, presentation.toolCalls)
        assertEquals(1, presentation.failedCalls)
        assertEquals(listOf("ls -la /tmp, ~/x", "read"), presentation.tools.map { it.name })
        assertEquals(DigestFiles(listOf("src/a.kt", "src/b.kt"), listOf("new.kt"), listOf("old.kt"), 3), presentation.files)
        assertEquals(listOf("created", "updated", "deleted"), presentation.memory.map { it.kind })
        assertEquals(DigestPresentation.PREVIEW_COVERAGE_NOTE, presentation.coverageNote)
        assertEquals("1 failed step · 3 tools · 4 files", presentation.slimText)
        assertEquals(listOf("+2 more memory changes"), presentation.sections.last().items)
        assertNull(presentation.tokens)
        assertNull(presentation.costUsd)
    }

    @Test
    fun problemDetectionUsesOutcomeOrEvidenceAndNeverReplyText() {
        assertTrue(digestHasProblem(message().copy(turnSucceeded = false)))
        assertTrue(digestHasProblem(message(evidence().copy(tools = listOf(DigestTool("shell", 1, 1))))))
        assertTrue(digestHasProblem(message(null, "[digest] · tools: shell ×2 (1 failed) · reply: Done")))
        assertTrue(digestHasProblem(message(null, "[digest] · tools: unknown tools format (2 failed)")))
        assertFalse(digestHasProblem(message(null, "[digest] · tools: shell ×2 (0 failed)")))
        assertFalse(digestHasProblem(message(null, "[digest] · files: changed note (2 failed) · reply: tools: shell ×2 (1 failed)")))
        assertFalse(digestHasProblem(message(evidence(), "[digest] · tools: shell ×2 (1 failed)")))
        assertFalse(digestHasProblem(message(null, "[digest] · tools: shell ×2 (1 failed) extra")))
    }

    @Test
    fun turnLookupIncludesHiddenDigestsSkipsEmptyAndUsesLatestNonemptyReceipt() {
        val old = message().copy(id = "old", turnId = "turn")
        val latest = old.copy(id = "latest")
        val other = message(null, "[digest] · tools: shell ×1").copy(id = "other", turnId = "other-turn")
        val quiet = message(null, "[digest] · no tool calls · reply: Hello").copy(id = "quiet", turnId = "turn")
        val noTurn = other.copy(id = "no-turn", turnId = null)
        val notDigest = latest.copy(id = "text", kind = Message.Kind.TEXT)
        assertEquals(mapOf("turn" to latest, "other-turn" to other), digestsByTurn(listOf(old, other, latest, quiet, noTurn, notDigest)))
        assertEquals(mapOf("turn" to message()), digestsByTurn(listOf(message())))
    }

    @Test
    fun structuredWireFieldsRoundTripWithoutChangingOldMessageConstructors() {
        val original = message().copy(
            tool = ToolActivity("TodoWrite", input = "{}", summary = "redacted", itemId = "item", output = "result"),
            turnSucceeded = false,
        )
        val decoded = CompanionJson.decodeFromString<Message>(CompanionJson.encodeToString(original))
        assertEquals(original, decoded)
        assertEquals(4_000L, decoded.digest?.usage?.cachedInput)
        val old = CompanionJson.decodeFromString<Message>("""{"id":"old","role":"bot","kind":"text","at":1,"tool":{"name":"read"}}""")
        assertNull(old.digest)
        assertNull(old.turnSucceeded)
        assertNull(old.tool?.input)
        assertNull(old.tool?.summary)
        assertNull(old.tool?.itemId)
    }

    @Test
    fun optionalDroppedCountsFilesAndNullableCostSurviveWireDecoding() {
        val original = message(evidence().copy(
            toolCalls = 9, toolsDropped = 2, memoryDropped = 3,
            files = DigestFiles(changed = listOf("a"), added = listOf("b"), deleted = listOf("c"), truncated = 4),
            memory = listOf(DigestMemory("a", "created"), DigestMemory("b", "updated"), DigestMemory("c", "deleted")),
            usage = DigestUsage(1, 2, costUsd = null), hookCoverage = "none",
        ))
        val decoded = CompanionJson.decodeFromString<Message>(CompanionJson.encodeToString(original))
        assertEquals(original, decoded)
        val presentation = DigestPresentation.from(decoded)
        assertEquals(9, presentation.toolCalls)
        assertEquals(3, presentation.files.count)
        assertEquals(4, presentation.files.truncated)
        assertEquals(3L, presentation.tokens)
        assertNull(presentation.costUsd)
        assertNull(presentation.coverageNote)
    }

    @Test
    fun malformedOptionalFieldDoesNotLoseValidSiblingFields() {
        val decoded = CompanionJson.decodeFromString<Message>("""{
            "id":"plan","role":"bot","kind":"activity","at":1,"turnSucceeded":false,
            "tool":{"name":"TodoWrite","input":"{\"todos\":[{\"content\":\"Task\"}]}","summary":{},"itemId":"item","output":"ok"}
        }""")
        assertNull(decoded.tool?.summary)
        assertEquals("item", decoded.tool?.itemId)
        assertEquals("ok", decoded.tool?.output)
        assertEquals("Task", TodoPlan.parse(assertNotNull(decoded.tool))?.items?.single()?.text)
        assertEquals(false, decoded.turnSucceeded)
    }

    @Test
    fun malformedOptionalPrimitiveFieldsNeverFailTheWholeMessage() {
        for (bad in listOf("{}", "[]", "42", "true", "null")) {
            val decoded = CompanionJson.decodeFromString<Message>("""{
                "id":"message","role":"bot","kind":"activity","at":1,
                "tool":{"name":"TodoWrite","input":$bad,"summary":$bad,"itemId":$bad,"output":$bad},
                "digest":$bad,"turnSucceeded":$bad
            }""")
            assertEquals("message", decoded.id)
            assertNull(decoded.tool?.input)
            assertNull(decoded.tool?.summary)
            assertNull(decoded.tool?.itemId)
            assertNull(decoded.tool?.output)
            assertNull(decoded.digest)
            assertEquals(if (bad == "true") true else null, decoded.turnSucceeded)
        }
        val quoted = CompanionJson.decodeFromString<Message>("""{"id":"message","role":"bot","kind":"text","at":1,"turnSucceeded":"false"}""")
        assertNull(quoted.turnSucceeded)
    }

    @Test
    fun malformedNestedDigestFieldsRejectOnlyTheOptionalReceipt() {
        val good = CompanionJson.encodeToJsonElement(evidence()) as JsonObject
        fun replace(key: String, value: kotlinx.serialization.json.JsonElement): JsonObject = JsonObject(good + (key to value))
        fun value(json: String) = CompanionJson.parseToJsonElement(json)
        val badDigests = listOf(
            JsonObject(good - "turnId"),
            replace("turnId", JsonPrimitive(7)),
            replace("durationMs", JsonPrimitive("72000")),
            replace("tools", value("""[{"name":"read"}]""")),
            replace("tools", value("""[{"name":7,"count":1,"failed":0}]""")),
            replace("tools", value("""[{"name":"read","count":1,"failed":0,"sample":false}]""")),
            replace("files", JsonObject(emptyMap())),
            replace("files", value("""{"changed":[7],"added":[],"deleted":[]}""")),
            replace("files", value("""{"changed":[],"added":[],"deleted":[],"truncated":"4"}""")),
            replace("memory", value("""[{"path":"note","kind":"future"}]""")),
            replace("usage", value("""{"input":1,"output":2,"costUsd":"cheap"}""")),
            replace("usage", value("""{"input":1,"output":2,"cachedInput":{}}""")),
            replace("toolCalls", JsonObject(emptyMap())),
            replace("toolsDropped", JsonPrimitive("2")),
            replace("memoryDropped", value("[]")),
            replace("hookCoverage", JsonPrimitive("future")),
            JsonNull,
        )
        for (bad in badDigests) {
            val wire = JsonObject(mapOf(
                "id" to JsonPrimitive("message"), "role" to JsonPrimitive("bot"), "kind" to JsonPrimitive("digest"),
                "at" to JsonPrimitive(1), "text" to JsonPrimitive("[digest] · tools: shell ×1 (1 failed)"), "digest" to bad,
            ))
            val decoded = CompanionJson.decodeFromString<Message>(wire.toString())
            assertEquals("message", decoded.id)
            assertNull(decoded.digest)
            assertTrue(digestHasProblem(decoded))
        }
        assertNotNull(CompanionJson.decodeFromString<Message>(CompanionJson.encodeToString(message())).digest)
    }
}
