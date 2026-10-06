package com.openmausbot.companion.core

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** How much of a bot's working activity the transcript shows. */
enum class ActivityDetail(val wireValue: String, val label: String, val caption: String) {
    FULL("full", "Full", "Every step a bot takes."),
    REDUCED("reduced", "Reduced", "Steps fold into one line. Failures always show."),
    HIDDEN("hidden", "Hidden", "No activity, only messages."),
    ;

    companion object {
        /**
         * What a phone starts with until the reader chooses: the phone should read
         * like a normal chat (Omkar, 2026-10-03), and the desktop likewise hides
         * tool calls until they are switched on. A stored choice wins.
         */
        val PHONE_DEFAULT: ActivityDetail = HIDDEN

        fun fromWire(value: String?): ActivityDetail =
            entries.firstOrNull { it.wireValue == value } ?: PHONE_DEFAULT
    }
}

/** One user-editable chip on the composer's quick-reply row. */
@Serializable
data class QuickReply(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val prompt: String,
    val icon: String,
) {
    companion object {
        val DEFAULTS: List<QuickReply> = listOf(
            QuickReply("default.diff", "Show diff", "Show latest git diff", "diff"),
            QuickReply("default.tests", "Run tests", "Run all automated tests", "tests"),
            QuickReply("default.explain", "Explain steps", "Explain the changes in detail", "explain"),
            QuickReply("default.next", "What's next?", "What should we do next?", "next"),
        )

        val ICON_CHOICES: List<String> = listOf(
            "next", "diff", "tests", "explain", "build", "bug", "document", "terminal",
            "send", "search", "history", "list",
        )

        fun encode(replies: List<QuickReply>): String =
            runCatching { CompanionJson.encodeToString(replies) }.getOrDefault("")

        /**
         * An empty or corrupt store falls back to defaults. An encoded empty list is a deliberate
         * choice and remains empty.
         */
        fun decode(json: String): List<QuickReply> {
            if (json.isEmpty()) return DEFAULTS
            val decoded = runCatching {
                CompanionJson.decodeFromString<List<QuickReply>>(json)
            }.getOrElse { return DEFAULTS }
            val ids = decoded.map { it.id.trim() }
            if (ids.any(String::isEmpty) || ids.toSet().size != ids.size) return DEFAULTS
            return decoded
        }
    }
}

/** A transcript item: one message, consecutive activity, or completed narration. */
sealed interface TranscriptRow {
    val head: Message
    val id: String
    val at: Double get() = head.at
    val endAt: Double
    val role: Message.Role get() = head.role
    val kind: Message.Kind get() = head.kind
    val senderName: String? get() = head.from?.name

    /** Search and pagination anchors can land inside a folded row. */
    fun containsMessage(messageId: String): Boolean = when (this) {
        is Single -> message.id == messageId
        is ActivityRun -> items.any { it.id == messageId }
        is AssistantTurn -> items.any { it.id == messageId }
        is Plan -> messageId in messageIds
    }

    data class Single(val message: Message) : TranscriptRow {
        override val head: Message get() = message
        override val id: String get() = message.id
        override val endAt: Double get() = message.at
    }

    data class ActivityRun(val items: List<Message>) : TranscriptRow {
        init {
            require(items.isNotEmpty()) { "An activity run must contain at least one message." }
        }

        override val head: Message get() = items.first()
        override val id: String get() = "run.${head.id}"
        override val endAt: Double get() = items.last().at
        val running: Boolean get() = items.any { it.tool?.ok == null }
    }

    data class AssistantTurn(val turnId: String, val items: List<Message>, val elapsed: Double) : TranscriptRow {
        init {
            require(items.isNotEmpty()) { "A turn fold must contain narration." }
        }

        override val head: Message get() = items.first()
        override val id: String get() = "turn.$turnId"
        override val endAt: Double get() = items.last().at
        val label: String get() {
            if (elapsed < 1_000) return "Worked"
            val seconds = (elapsed / 1_000).toLong()
            val duration = if (seconds < 60) "${seconds}s"
                else "${seconds / 60}m ${(seconds % 60).toString().padStart(2, '0')}s"
            return "Worked for $duration"
        }
    }

    data class Plan(
        override val id: String,
        val message: Message,
        val plan: TodoPlan,
        val messageIds: Set<String> = setOf(id.removePrefix("plan."), message.id),
        override val at: Double = message.at,
    ) : TranscriptRow {
        override val head: Message get() = message
        override val endAt: Double get() = message.at
    }
}

/**
 * The one line a roster row shows under a chat's name.
 *
 * Folded by the same rule as the transcript, and for the same reason: a reader who has turned
 * activity off has said they do not want to see tool calls, and the roster is where they see the
 * most of them — one per chat, on the screen they spend the most time on. Reading the preview off
 * the raw last message made "Hidden" mean "hidden in one place".
 */
fun rosterPreview(messages: List<Message>, detail: ActivityDetail): String {
    // Only the tail is needed. A completed narration fold always precedes its
    // visible final reply, so it cannot be the last non-summary row. Plans stay
    // ordinary activity here, rather than the transcript's persistent card.
    var steps = 0
    var running = false
    var lastStep: Message? = null
    for (index in messages.indices.reversed()) {
        val message = messages[index]
        if (message.kind == Message.Kind.DIGEST) {
            // A visible problem summary breaks a reduced run even though the
            // preview itself reads past summaries. Hidden healthy summaries do not.
            if (steps > 0 && shouldShowDigest(message, showSummaries = false)) break
            continue
        }
        if (detail == ActivityDetail.HIDDEN &&
            (message.kind == Message.Kind.ACTIVITY || message.kind == Message.Kind.COMPACTION) &&
            !isStatusNotice(message) && !isFailedTurn(message)
        ) continue
        if (detail == ActivityDetail.REDUCED && message.kind == Message.Kind.ACTIVITY &&
            message.tool?.ok != false && !isStatusNotice(message)
        ) {
            if (lastStep == null) lastStep = message
            steps++
            if (message.tool?.ok == null) running = true
        } else {
            if (steps > 0) break
            return previewText(message)
        }
    }
    return when (steps) {
        0 -> ""
        1 -> previewText(checkNotNull(lastStep))
        else -> "${if (running) "Running" else "Ran"} $steps steps"
    }
}

/** What a single message reads as in a roster row. */
internal fun previewText(message: Message): String = when (message.kind) {
    Message.Kind.TEXT -> message.webhookContent?.task
        ?: message.text?.takeIf { it.isNotEmpty() }
        // A bot that only sent a file says so by its name.
        ?: message.attachedFiles.firstOrNull()?.name.orEmpty()
    // a pending card's question is the preview; the roster row already says
    // "waiting on you" beside it
    Message.Kind.OPTIONS -> {
        val card = message.card
        when {
            card == null -> ""
            card.isPending && card.subtitle.isNotEmpty() -> card.subtitle
            else -> card.title
        }
    }
    Message.Kind.ACTIVITY -> message.tool?.label.orEmpty()
    Message.Kind.SCREEN -> "Screenshot"
    Message.Kind.DIGEST -> ""
    Message.Kind.COMPACTION -> message.compaction?.chipText ?: message.text.orEmpty()
    Message.Kind.ROUTINE_RUN -> message.routineRunPreview
    Message.Kind.UNKNOWN -> message.text.orEmpty()
}

/**
 * A status row the server writes while a turn runs ("notice: Qwen hit a rate
 * limit and is retrying"). Port of `isStatusNotice` in `ChatPreferences.swift`:
 * it tells the reader what the bot is doing, so it is never hidden or folded.
 */
fun isStatusNotice(message: Message): Boolean =
    message.kind == Message.Kind.ACTIVITY && message.tool?.name?.startsWith("notice:") == true

/**
 * A turn that failed is stored as an activity row named "error: <what went wrong>"
 * (shared/failed-turn.ts on the computer). The cause without that marker; null for any
 * other row. Port of `failedTurnCause` in `ChatPreferences.swift`: the chip and the
 * roster preview both read it here, so neither shows the marker.
 */
fun failedTurnCause(name: String): String? =
    if (name.startsWith("error:")) name.removePrefix("error:").trim() else null

/**
 * A failed turn's row. Like a status notice it is never hidden: it is the only sign the
 * bot did not answer, and desktop always shows it too.
 */
fun isFailedTurn(message: Message): Boolean =
    message.kind == Message.Kind.ACTIVITY && message.tool?.let { failedTurnCause(it.name) } != null

/** What the chip and the roster say: a failed turn's cause, or the step. */
val ToolActivity.label: String get() = failedTurnCause(name) ?: name

/** Plans tell the reader what the bot is doing; summaries have their own visibility rule. */
fun isActivityReceipt(message: Message): Boolean = when (message.kind) {
    Message.Kind.ACTIVITY -> message.tool?.let { TodoPlan.parse(it) } == null
    Message.Kind.COMPACTION -> true
    else -> false
}

/** The messages a bot has written so far in the turn it is still working on. Port of iOS `LiveNarration`. */
data class LiveNarration(
    /** Rows the transcript leaves out while the turn runs. */
    val hiddenIds: Set<String>,
    /** The newest of them: the one grey status line shown instead. */
    val latest: String?,
) {
    companion object {
        val NONE = LiveNarration(emptySet(), null)
    }
}

/**
 * At Hidden, a working bot's in-between messages ("Let me check the logs") are
 * one grey status line rather than a pile of bubbles (Omkar, 2026-10-03).
 *
 * Only the turn answering the latest message, only while the bot works, and only
 * until the server marks the turn's final reply: then the turn folds into its
 * "Worked for" row as at every other level. A turn that ends without that mark (an
 * older desktop, a crash) is no longer busy, so its messages show as bubbles and
 * nothing it said is lost.
 */
fun liveNarration(messages: List<Message>, busy: Boolean, detail: ActivityDetail): LiveNarration {
    if (!busy || detail != ActivityDetail.HIDDEN) return LiveNarration.NONE
    var start = 0
    var turn: String? = null
    var latest: String? = null
    for (index in messages.indices.reversed()) {
        val message = messages[index]
        if (message.role == Message.Role.USER) {
            start = index + 1
            break
        }
        if (turn == null && message.role == Message.Role.BOT && message.kind == Message.Kind.TEXT &&
            !message.turnId.isNullOrEmpty()
        ) {
            turn = message.turnId
            latest = message.text
        }
    }
    if (turn == null) return LiveNarration.NONE
    val ids = linkedSetOf<String>()
    for (index in start until messages.size) {
        val message = messages[index]
        if (message.role != Message.Role.BOT || message.kind != Message.Kind.TEXT || message.turnId != turn) continue
        if (message.turnTerminal == true) return LiveNarration.NONE
        ids.add(message.id)
    }
    return LiveNarration(ids, latest)
}

/**
 * Fold tool steps to the selected detail without hiding plans or opted-in/problem summaries.
 * Failed steps remain separate at Reduced; notices and failed turns survive every level.
 */
fun transcriptRows(
    messages: List<Message>,
    detail: ActivityDetail,
    showSummaries: Boolean = false,
): List<TranscriptRow> {
    val plans = planStates(messages)
    val planRows = mutableMapOf<String, TranscriptRow.Plan>()
    if (plans.isNotEmpty()) {
        val planGroups = mutableMapOf<String, MutableList<Message>>()
        var userGroup = "before-user"
        for (message in messages) {
            if (message.role == Message.Role.USER) userGroup = "user.${message.id}"
            if (message.id !in plans) continue
            val group = message.turnId?.takeIf { it.isNotEmpty() }?.let { "turn.$it" } ?: userGroup
            planGroups.getOrPut(group) { mutableListOf() }.add(message)
        }
        for (items in planGroups.values) {
            val first = items.first()
            val latest = items.last()
            planRows[first.id] = TranscriptRow.Plan(
                id = "plan.${first.id}",
                message = latest,
                plan = plans.getValue(latest.id),
                messageIds = items.mapTo(mutableSetOf()) { it.id },
                at = first.at,
            )
        }
    }
    // Only a server completion marker makes narration foldable. Legacy and
    // unfinished turns stay visible, matching desktop and iOS.
    val narration = mutableMapOf<String, MutableList<Message>>()
    val startedAt = mutableMapOf<String, Double>()
    var lastUserAt: Double? = null
    val folds = mutableMapOf<String, TranscriptRow.AssistantTurn>()
    val hiddenIds = mutableSetOf<String>()
    for (message in messages) {
        if (message.role == Message.Role.USER) lastUserAt = message.at
        val turnId = message.turnId?.takeIf { it.isNotEmpty() } ?: continue
        if (message.role != Message.Role.BOT || message.kind != Message.Kind.TEXT) continue
        if (message.turnTerminal == true) {
            val items = narration.remove(turnId)?.takeIf { it.isNotEmpty() } ?: continue
            folds[items.first().id] = TranscriptRow.AssistantTurn(
                turnId, items, (message.at - (startedAt[turnId] ?: items.first().at)).coerceAtLeast(0.0),
            )
            for (item in items) hiddenIds.add(item.id)
        } else {
            if (turnId !in narration) startedAt[turnId] = lastUserAt ?: message.at
            narration.getOrPut(turnId) { mutableListOf() }.add(message)
        }
    }
    return buildList {
        val run = mutableListOf<Message>()
        fun flush() {
            when (run.size) {
                0 -> Unit
                1 -> add(TranscriptRow.Single(run.single()))
                else -> add(TranscriptRow.ActivityRun(run.toList()))
            }
            run.clear()
        }

        messages.forEach { message ->
            if (message.id in plans) {
                // Every plan-bearing activity breaks the step run, even a later update
                // whose latest state is already carried by the group's first card.
                flush()
                planRows[message.id]?.let { add(it) }
                return@forEach
            }
            if (message.kind == Message.Kind.DIGEST) {
                if (shouldShowDigest(message, showSummaries)) {
                    flush()
                    add(TranscriptRow.Single(message))
                }
                return@forEach
            }
            val turn = folds[message.id]
            if (turn != null) {
                flush()
                add(turn)
            } else if (message.id in hiddenIds || (detail == ActivityDetail.HIDDEN &&
                    (message.kind == Message.Kind.ACTIVITY || isActivityReceipt(message)) &&
                    !isStatusNotice(message) && !isFailedTurn(message))) {
                // The reversible turn fold owns narration; Hidden owns machinery.
            } else if (detail != ActivityDetail.REDUCED || message.kind != Message.Kind.ACTIVITY) {
                flush()
                add(TranscriptRow.Single(message))
            } else if (message.tool?.ok == false || isStatusNotice(message)) {
                flush()
                add(TranscriptRow.Single(message))
            } else {
                run += message
            }
        }
        flush()
    }
}
