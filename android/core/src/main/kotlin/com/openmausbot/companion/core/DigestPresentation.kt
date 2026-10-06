package com.openmausbot.companion.core

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class DigestTool(val name: String, val count: Int, val failed: Int, val sample: String? = null)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DigestFiles(
    @EncodeDefault
    val changed: List<String> = emptyList(),
    @EncodeDefault
    val added: List<String> = emptyList(),
    @EncodeDefault
    val deleted: List<String> = emptyList(),
    val truncated: Int? = null,
) {
    val count: Int get() = changed.size + added.size + deleted.size
}

@Serializable
data class DigestMemory(val path: String, val kind: String)

@Serializable
data class DigestUsage(
    val input: Long,
    val output: Long,
    val cachedInput: Long? = null,
    val costUsd: Double? = null,
)

/** The server's evidence, distinct from the legacy [TurnDigest] text parser. */
@Serializable
data class StructuredTurnDigest(
    val turnId: String,
    val botId: String,
    val threadId: String,
    val at: Double,
    val durationMs: Double,
    val tools: List<DigestTool>,
    val memory: List<DigestMemory>,
    val reply: String,
    val hookCoverage: String,
    val toolCalls: Int? = null,
    val toolsDropped: Int? = null,
    val files: DigestFiles? = null,
    val memoryDropped: Int? = null,
    val usage: DigestUsage? = null,
)

/** Optional evidence can be rejected without rejecting the enclosing wire message. */
object OptionalStructuredDigestSerializer : KSerializer<StructuredTurnDigest?> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): StructuredTurnDigest? {
        val input = decoder as JsonDecoder
        val element = input.decodeJsonElement()
        if (!hasDigestShape(element)) return null
        return runCatching { input.json.decodeFromJsonElement<StructuredTurnDigest>(element) }.getOrNull()
    }

    override fun serialize(encoder: Encoder, value: StructuredTurnDigest?) {
        val output = encoder as JsonEncoder
        output.encodeJsonElement(value?.let { output.json.encodeToJsonElement(it) } ?: JsonNull)
    }
}

/** Tree decoding can coerce primitives to strings, so check the wire types before decoding. */
private fun hasDigestShape(element: JsonElement): Boolean {
    val value = element as? JsonObject ?: return false
    fun optional(objectValue: JsonObject, key: String, accepts: (JsonElement) -> Boolean): Boolean =
        objectValue[key].let { it == null || it == JsonNull || accepts(it) }
    fun array(objectValue: JsonObject, key: String, accepts: (JsonElement) -> Boolean): Boolean =
        (objectValue[key] as? JsonArray)?.all(accepts) == true
    if (!DIGEST_STRING_FIELDS.all { value[it].isWireString() } ||
        !value["at"].isWireNumber() || !value["durationMs"].isWireNumber() ||
        (value["hookCoverage"] as JsonPrimitive).content !in DIGEST_COVERAGES) return false
    if (!array(value, "tools") {
            val tool = it as? JsonObject
            tool != null && tool["name"].isWireString() && tool["count"].isWireInteger() &&
                tool["failed"].isWireInteger() && optional(tool, "sample") { sample -> sample.isWireString() }
        }) return false
    if (!array(value, "memory") {
            val memory = it as? JsonObject
            memory != null && memory["path"].isWireString() && memory["kind"].isWireString() &&
                (memory["kind"] as JsonPrimitive).content in DIGEST_MEMORY_KINDS
        }) return false
    if (!DIGEST_COUNT_FIELDS.all { key ->
            optional(value, key) { it.isWireInteger() }
        }) return false
    if (!optional(value, "files") {
            val files = it as? JsonObject
            files != null && DIGEST_FILE_FIELDS.all { key ->
                array(files, key) { path -> path.isWireString() }
            } && optional(files, "truncated") { count -> count.isWireInteger() }
        }) return false
    return optional(value, "usage") {
        val usage = it as? JsonObject
        usage != null && usage["input"].isWireInteger() && usage["output"].isWireInteger() &&
            optional(usage, "cachedInput") { count -> count.isWireInteger() } &&
            optional(usage, "costUsd") { cost -> cost.isWireNumber() }
    }
}

private fun JsonElement?.isWireString(): Boolean = this is JsonPrimitive && isString
private fun JsonElement?.isWireInteger(): Boolean = this is JsonPrimitive && !isString && longOrNull != null
private fun JsonElement?.isWireNumber(): Boolean =
    this is JsonPrimitive && !isString && doubleOrNull?.isFinite() == true

private val DIGEST_STRING_FIELDS = listOf("turnId", "botId", "threadId", "reply", "hookCoverage")
private val DIGEST_COUNT_FIELDS = listOf("toolCalls", "toolsDropped", "memoryDropped")
private val DIGEST_FILE_FIELDS = listOf("changed", "added", "deleted")
private val DIGEST_COVERAGES = setOf("full", "preview", "none")
private val DIGEST_MEMORY_KINDS = setOf("created", "updated", "deleted")

/** One model for the slim line and sheet, including older computers' text-only receipts. */
data class DigestPresentation(
    val duration: String?,
    val toolCalls: Int,
    val failedCalls: Int,
    val files: DigestFiles,
    val memory: List<DigestMemory>,
    val tools: List<DigestTool>,
    val tokens: Long?,
    val costUsd: Double?,
    val coverageNote: String?,
    val sections: List<TurnDigest.Section>,
    val hasProblem: Boolean,
    val isEmpty: Boolean,
) {
    val slimText: String
        get() = buildList {
            if (failedCalls > 0) add("$failedCalls failed ${if (failedCalls == 1) "step" else "steps"}")
            else if (hasProblem) add("Turn failed")
            duration?.let { add("Worked $it") }
            if (toolCalls > 0) add("$toolCalls ${if (toolCalls == 1) "tool" else "tools"}")
            if (files.count > 0) add("${files.count} ${if (files.count == 1) "file" else "files"}")
        }.joinToString(" · ").ifEmpty { TurnDigest.CHIP_TITLE }

    companion object {
        const val PREVIEW_COVERAGE_NOTE = "Based on tool previews; some activity may not be included."

        fun from(message: Message): DigestPresentation {
            val digest = message.digest
            if (digest != null) {
                val files = digest.files ?: DigestFiles()
                val calls = digest.toolCalls ?: digest.tools.sumOf { it.count }
                val failed = digest.tools.sumOf { it.failed }
                val sections = buildList {
                    if (digest.tools.isNotEmpty()) add(TurnDigest.Section("tools", digest.tools.map {
                        "${it.name} ×${it.count}" + if (it.failed > 0) " (${it.failed} failed)" else ""
                    }))
                    val paths = files.changed.map { "changed $it" } + files.added.map { "added $it" } +
                        files.deleted.map { "deleted $it" } +
                        listOfNotNull(files.truncated?.takeIf { it > 0 }?.let { "+$it more paths" })
                    if (paths.isNotEmpty()) add(TurnDigest.Section("files", paths))
                    if (digest.memory.isNotEmpty()) add(TurnDigest.Section("memory", digest.memory.map {
                        "${it.kind} ${it.path}"
                    }))
                    digest.toolsDropped?.takeIf { it > 0 }?.let {
                        add(TurnDigest.Section(null, listOf("+$it more tools")))
                    }
                    digest.memoryDropped?.takeIf { it > 0 }?.let {
                        add(TurnDigest.Section(null, listOf("+$it more memory changes")))
                    }
                }
                return DigestPresentation(
                    duration = digest.durationMs.takeIf { it > 0 }?.let(::formatDuration),
                    toolCalls = calls,
                    failedCalls = failed,
                    files = files,
                    memory = digest.memory,
                    tools = digest.tools,
                    tokens = digest.usage?.let { it.input + it.output },
                    costUsd = digest.usage?.costUsd,
                    coverageNote = PREVIEW_COVERAGE_NOTE.takeIf { digest.hookCoverage == "preview" },
                    sections = sections,
                    hasProblem = message.turnSucceeded == false || failed > 0,
                    isEmpty = !digest.hasWork(),
                )
            }
            val parsed = TurnDigest.parse(message.text)
            val tools = parsed.sections.filter { it.label == "tools" }.flatMap { section ->
                section.items.mapNotNull { item ->
                    val match = TEXT_TOOL.matchEntire(item) ?: return@mapNotNull null
                    DigestTool(
                        name = match.groupValues[1],
                        count = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null,
                        failed = match.groupValues[3].toIntOrNull() ?: 0,
                    )
                }
            }
            val changed = mutableListOf<String>()
            val added = mutableListOf<String>()
            val deleted = mutableListOf<String>()
            var truncated: Int? = null
            val memory = mutableListOf<DigestMemory>()
            for (section in parsed.sections) {
                for (item in section.items) {
                    when (section.label) {
                        "files" -> when {
                            item.startsWith("changed ") -> changed.addAll(item.removePrefix("changed ").split(", "))
                            item.startsWith("added ") -> added.addAll(item.removePrefix("added ").split(", "))
                            item.startsWith("deleted ") -> deleted.addAll(item.removePrefix("deleted ").split(", "))
                            else -> MORE_PATHS.matchEntire(item)?.let { truncated = it.groupValues[1].toIntOrNull() }
                        }
                        "memory" -> MEMORY.matchEntire(item)?.let {
                            memory += DigestMemory(path = it.groupValues[2], kind = it.groupValues[1])
                        }
                    }
                }
            }
            return DigestPresentation(
                duration = null,
                toolCalls = parsed.toolCalls,
                failedCalls = parsed.failedCalls,
                files = DigestFiles(changed, added, deleted, truncated),
                memory = memory,
                tools = tools,
                tokens = null,
                costUsd = null,
                coverageNote = PREVIEW_COVERAGE_NOTE.takeIf {
                    parsed.sections.any { it.label == "tools" && "from tool previews" in it.items }
                },
                sections = parsed.sections,
                hasProblem = message.turnSucceeded == false || parsed.failedCalls > 0,
                isEmpty = parsed.sections.isEmpty(),
            )
        }

        private fun formatDuration(durationMs: Double): String {
            val seconds = (durationMs / 1_000).toLong()
            val hours = seconds / 3_600
            val minutes = seconds / 60 % 60
            val remainder = seconds % 60
            return when {
                hours > 0 -> "${hours}h" + if (minutes > 0) " ${minutes}m" else ""
                minutes > 0 -> "${minutes}m" + if (remainder > 0) " ${remainder}s" else ""
                else -> "${seconds}s"
            }
        }

        private val TEXT_TOOL = Regex("^(.*) ×(\\d+)(?: \\((\\d+) failed\\))?$")
        private val MORE_PATHS = Regex("^\\+(\\d+) more paths$")
        private val MEMORY = Regex("^(created|updated|deleted) (.+)$")
    }
}

fun digestHasProblem(message: Message): Boolean = message.turnSucceeded == false ||
    (message.digest?.tools?.any { it.failed > 0 } ?: (TurnDigest.parse(message.text).failedCalls > 0))

/** Hidden summaries remain reachable from a turn's terminal reply. Last receipt wins. */
fun digestsByTurn(messages: List<Message>): Map<String, Message> = buildMap {
    for (message in messages) {
        if (message.kind != Message.Kind.DIGEST || !hasDigestWork(message)) continue
        val turn = message.turnId?.takeIf { it.isNotEmpty() }
            ?: message.digest?.turnId?.takeIf { it.isNotEmpty() } ?: continue
        put(turn, message)
    }
}

/** Visibility and lookup need evidence presence, not an allocated sheet presentation. */
internal fun hasDigestWork(message: Message): Boolean =
    message.digest?.hasWork() ?: TurnDigest.parse(message.text).sections.isNotEmpty()

private fun StructuredTurnDigest.hasWork(): Boolean = (toolCalls ?: 0) > 0 ||
    tools.any { it.count > 0 || it.failed > 0 } || memory.isNotEmpty() ||
    (files?.count ?: 0) > 0 || (files?.truncated ?: 0) > 0 ||
    (toolsDropped ?: 0) > 0 || (memoryDropped ?: 0) > 0

/** Text-only receipts are parsed once when choosing their transcript visibility. */
internal fun shouldShowDigest(message: Message, showSummaries: Boolean): Boolean {
    val digest = message.digest
    if (digest != null) {
        return digest.hasWork() && (showSummaries || message.turnSucceeded == false || digest.tools.any { it.failed > 0 })
    }
    val parsed = TurnDigest.parse(message.text)
    return parsed.sections.isNotEmpty() && (showSummaries || message.turnSucceeded == false || parsed.failedCalls > 0)
}
