package com.openmausbot.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.BotTask
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.displayTitle

/** One thread that needs the person, from any bot in the fleet. */
@Immutable
internal data class AttentionEntry(
    val botId: String,
    val botName: String,
    val task: BotTask,
    /** A send of the person's is waiting behind the turn (client-side only). */
    val queued: Boolean,
) {
    val id: String get() = "$botId-${task.threadId}"

    /** The one status word the row carries, in the order the person cares. */
    fun statusLine(): String = when {
        task.activity == "waiting-on-you" -> "Waiting on you"
        task.busy == true || task.activity == "working" -> "Working"
        queued -> "Queued"
        else -> "Unread"
    }
}

/**
 * The Needs attention section above the roster — the port of the desktop's
 * cross-bot SidebarBotActivity list. Attention is not history browsing: idle
 * conversations never enter, and each row reads the sibling thread's own
 * flags rather than the bot's aggregate busy/waiting.
 */
internal fun CompanionState.crossBotAttention(exceptBotId: String? = null): List<AttentionEntry> {
    val entries = bots
        // Hidden bots stay out entirely; the excluded bot is wherever the
        // person already is.
        .filter { it.id != exceptBotId && it.hidden != true }
        .flatMap { bot ->
            // A send waiting behind the turn counts as attention only once it
            // is attached — the desktop filters on its client-side boolean the
            // same way, so a queued-only thread enters the list.
            attentionTasks(bot)
                .map { it to (pendingQueued[it.threadId].orEmpty().isNotEmpty()) }
                .filter { (task, queued) ->
                    task.activity == "waiting-on-you" || task.activity == "working" ||
                        task.busy == true || queued || task.unread == true
                }
                .map { (task, queued) -> AttentionEntry(bot.id, bot.name, task, queued) }
        }
    // Flatten first, then order once (7fedb278): sorting each bot on its own
    // would let the unread reply of an earlier bot outrank the waiting
    // approval of a later one. Stable, so equal ranks keep fleet order.
    return entries.sortedBy(::attentionRank)
}

/** The bot's own addressable threads, legacy aggregate included. */
private fun attentionTasks(bot: Bot): List<BotTask> {
    // Old desktops expose only the selected conversation; new ones expose
    // each task's own runtime flags.
    val tasks = bot.tasks ?: listOf(
        BotTask(
            threadId = bot.threadId, title = "", createdAt = bot.createdAt,
            busy = bot.busy, activity = bot.activity, unread = bot.unread,
        ),
    )
    // Routine runs are reachable through their run receipt, never a menu.
    return tasks.filter { it.routineRunId == null }
}

/** Waiting needs the person most, then work, then a queued send, then unread. */
private fun attentionRank(entry: AttentionEntry): Int = when {
    entry.task.activity == "waiting-on-you" -> 0
    entry.task.busy == true || entry.task.activity == "working" -> 1
    entry.queued -> 2
    else -> 3
}

/** Selection only: the row jumps; menus and management stay in the sheet. */
@Composable
internal fun AttentionRow(entry: AttentionEntry, bot: Bot, compact: Boolean, onOpen: () -> Unit) {
    val status = when {
        entry.task.activity == "waiting-on-you" -> stringResource(R.string.mobile_waiting_on_you_edab5b72)
        entry.task.busy == true || entry.task.activity == "working" -> stringResource(R.string.mobile_thread_working)
        entry.queued -> stringResource(R.string.mobile_queued_6a599877)
        else -> stringResource(R.string.mobile_unread_07b032b5)
    }
    val badge = when {
        entry.task.activity == "waiting-on-you" -> RosterFaceBadge.WAITING
        entry.task.busy == true || entry.task.activity == "working" -> RosterFaceBadge.WORKING
        entry.queued -> RosterFaceBadge.QUEUED
        else -> RosterFaceBadge.UNREAD
    }
    val faceSize = if (compact) 32.dp else 40.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attention-row.${entry.id}")
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = MIN_TOUCH_TARGET)
            .padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RosterFace(size = faceSize, color = bot.color, badge = badge) {
            BotAvatar(bot = bot, size = faceSize, animated = false, state = when (badge) {
                RosterFaceBadge.WAITING -> MausState.IDLE
                RosterFaceBadge.WORKING -> MausState.WORKING
                else -> MausState.IDLE
            })
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.task.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.botName} · $status",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
