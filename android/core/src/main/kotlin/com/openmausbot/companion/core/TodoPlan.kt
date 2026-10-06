package com.openmausbot.companion.core

import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** Provider spellings become one presentation-independent task state. */
enum class TodoStatus {
    PENDING, ACTIVE, DONE, CANCELLED;

    companion object {
        fun fromWire(value: String): TodoStatus = when (
            value.lowercase(Locale.ROOT).replace('-', '_').replace(' ', '_')
        ) {
            "completed", "complete", "done", "finished" -> DONE
            "in_progress", "active", "running", "doing", "current" -> ACTIVE
            "cancelled", "canceled", "skipped", "deleted" -> CANCELLED
            else -> PENDING
        }
    }
}

data class TodoItem(
    val id: String,
    val text: String,
    val activeText: String? = null,
    val status: TodoStatus = TodoStatus.PENDING,
)

data class TodoPlan(val items: List<TodoItem>, val truncated: Boolean = false) {
    val done: Int get() = items.count { it.status == TodoStatus.DONE }
    val total: Int get() = items.count { it.status != TodoStatus.CANCELLED }
    val active: TodoItem? get() = items.firstOrNull { it.status == TodoStatus.ACTIVE }
    val isFinished: Boolean get() = total > 0 && done == total

    companion object {
        fun parse(tool: ToolActivity): TodoPlan? =
            if (mayCarryPlan(tool)) tool.inputObject()?.let { parseSnapshot(tool, it) } else null

        /**
         * Only a todo, plan or task tool can carry a plan. The bulk of a busy
         * transcript is shell, read and edit calls with kilobyte inputs; those
         * are skipped by name, or by their input's first key for an engine that
         * titles its todo call in words, and never parsed (iOS `TodoPlan.mayCarryPlan`).
         */
        internal fun mayCarryPlan(tool: ToolActivity): Boolean {
            val name = tool.name
            if (name.contains("todo", ignoreCase = true) || name.contains("plan", ignoreCase = true) ||
                name.endsWith("TaskCreate", ignoreCase = true) || name.endsWith("TaskUpdate", ignoreCase = true)
            ) return true
            return firstKey(tool.input ?: return false) in PLAN_FIRST_KEYS
        }

        private val PLAN_FIRST_KEYS = setOf("todos", "plan", "entries")

        /** The first key of a JSON object preview, read off its characters without parsing. */
        internal fun firstKey(input: String): String? {
            var index = 0
            fun skipSpace() { while (index < input.length && input[index].isWhitespace()) index++ }
            skipSpace()
            if (index >= input.length || input[index] != '{') return null
            index++
            skipSpace()
            if (index >= input.length || input[index] != '"') return null
            val end = input.indexOf('"', index + 1).takeIf { it > 0 && it - index <= 33 } ?: return null
            return input.substring(index + 1, end)
        }
    }
}

private fun parseSnapshot(tool: ToolActivity, input: JsonObject): TodoPlan? {
    val keys = if (tool.name.contains("todo", ignoreCase = true)) {
        SNAPSHOT_KEYS_WITH_ITEMS
    } else {
        SNAPSHOT_KEYS
    }
    val key = keys.firstOrNull { input[it] is JsonArray } ?: return null
    val array = input[key] as JsonArray
    var truncated = false
    val items = array.mapIndexedNotNull { index, element ->
        if (element is JsonPrimitive && element.isString) {
            truncated = true
            return@mapIndexedNotNull null
        }
        val item = element as? JsonObject ?: return@mapIndexedNotNull null
        val textKeys = when (key) {
            "plan" -> PLAN_TEXT_KEYS
            "entries" -> ENTRY_TEXT_KEYS
            else -> TODO_TEXT_KEYS
        }
        val text = textKeys.firstNotNullOfOrNull { item.string(it) }?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
        val status = item.string("status")?.let { TodoStatus.fromWire(it) }
            ?: when (item.boolean("completed") ?: item.boolean("done")) {
                true -> TodoStatus.DONE
                else -> TodoStatus.PENDING
            }
        TodoItem(
            id = index.toString(),
            text = text,
            activeText = (item.string("activeForm") ?: item.string("active_form"))
                ?.trim()?.takeIf { it.isNotEmpty() },
            status = status,
        )
    }
    return items.takeIf { it.isNotEmpty() }?.let { TodoPlan(it, truncated) }
}

private val SNAPSHOT_KEYS = listOf("todos", "plan", "entries")
private val SNAPSHOT_KEYS_WITH_ITEMS = SNAPSHOT_KEYS + "items"
private val PLAN_TEXT_KEYS = listOf("step", "content", "text")
private val ENTRY_TEXT_KEYS = listOf("content")
private val TODO_TEXT_KEYS = listOf("content", "subject", "title", "text", "description")

/**
 * One thread's activities, in transcript order. State survives user/turn boundaries;
 * a snapshot replaces it, while Claude Tasks patch it without losing earlier tasks.
 */
fun planStates(messages: List<Message>): Map<String, TodoPlan> {
    val states = linkedMapOf<String, TodoPlan>()
    var current = TodoPlan(emptyList())
    var largestId = 0L
    fun remember(id: String) {
        id.toLongOrNull()?.let { largestId = maxOf(largestId, it) }
    }
    for (message in messages) {
        if (message.kind != Message.Kind.ACTIVITY) continue
        val tool = message.tool ?: continue
        if (!TodoPlan.mayCarryPlan(tool)) continue
        val input = tool.inputObject() ?: continue
        val snapshot = parseSnapshot(tool, input)
        if (snapshot != null) {
            current = snapshot
            snapshot.items.forEach { remember(it.id) }
        } else if (tool.name.endsWith("TaskCreate", ignoreCase = true)) {
            val text = input.string("subject")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val output = tool.output
            val explicitId = output?.let { value ->
                val parsed = runCatching { CompanionJson.parseToJsonElement(value) as? JsonObject }.getOrNull()
                val task = parsed?.get("task") as? JsonObject
                (task?.get("id") as? JsonPrimitive)?.takeIf { it.isString || it.longOrNull != null }
                    ?.content?.takeIf { it.isNotBlank() }
            } ?: output?.let { TASK_NUMBER.find(it)?.groupValues?.get(1) }
            val id = explicitId ?: (largestId + 1).toString()
            remember(id)
            current = current.copy(items = current.items + TodoItem(
                id = id,
                text = text,
                activeText = input.string("activeForm")?.trim()?.takeIf { it.isNotEmpty() },
            ))
        } else if (tool.name.endsWith("TaskUpdate", ignoreCase = true)) {
            val id = input.string("taskId") ?: continue
            val index = current.items.indexOfFirst { it.id == id }
            if (index < 0) continue
            val status = input.string("status")
            val subject = input.string("subject")?.trim()?.takeIf { it.isNotEmpty() }
            val activeText = input.string("activeForm")?.trim()?.takeIf { it.isNotEmpty() }
            if (status == null && subject == null && activeText == null) continue
            val items = current.items.toMutableList()
            if (status?.lowercase(Locale.ROOT) == "deleted") {
                items.removeAt(index)
            } else {
                val old = items[index]
                items[index] = old.copy(
                    text = subject ?: old.text,
                    activeText = activeText ?: old.activeText,
                    status = status?.let { TodoStatus.fromWire(it) } ?: old.status,
                )
            }
            current = current.copy(items = items)
        } else {
            continue
        }
        states[message.id] = current
    }
    return states
}

private val TASK_NUMBER = Regex("#(\\d+)")

private fun ToolActivity.inputObject(): JsonObject? = input?.let {
    runCatching { CompanionJson.parseToJsonElement(it) as? JsonObject }.getOrNull()
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.boolean(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
