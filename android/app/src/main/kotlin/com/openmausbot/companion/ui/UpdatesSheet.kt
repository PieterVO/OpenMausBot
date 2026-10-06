package com.openmausbot.companion.ui

import com.openmausbot.companion.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.Chat
import kotlinx.coroutines.launch

/**
 * The Updates pill and what it opens — the port of `UpdatesPill`/`MascotStack` in
 * `ios/App/ChatListView.swift:474-550` and of `ios/App/UpdatesSheet.swift`.
 *
 * Needs you first, with the answer right there — the phone exists so that a
 * stopped bot on the laptop can be un-stopped from wherever you are. Then what is
 * working, then what finished while you were not looking.
 */

/** The floating pill: who is doing what right now, at a glance. */
@Composable
internal fun UpdatesBar(updates: List<ChatUpdate>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val first = updates.firstOrNull()
    val stack = remember(updates) { updates.take(UpdatesSummary.MASCOTS).map { it.chat } }
    val needsYou = updates.count { it.kind == UpdateKind.NEEDS_YOU }
    val working = updates.count { it.kind == UpdateKind.WORKING }
    Row(
        modifier = modifier
            .testTag("updates-bar")
            .chromeCapsule()
            .clip(CircleShape)
            .clickable(onClickLabel = stringResource(R.string.mobile_open_updates_2c80d633), role = Role.Button, onClick = onOpen)
            .heightIn(min = 52.dp)
            .padding(start = if (first == null) 16.dp else 7.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (first != null) {
            Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                stack.forEach { chat ->
                    Box(
                        Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp),
                    ) {
                        ChatAvatar(chat = chat, size = 28.dp, animated = false)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = localizedUpdatesHeadline(updates),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (first == null) secondaryTint else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = localizedUpdatesSubline(updates),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (needsYou > 0) {
                    val label = "${localizedMobileCopy(UpdatesSummary.section(UpdateKind.NEEDS_YOU))}: $needsYou"
                    Text(
                        text = needsYou.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                        color = rosterNeedsYouInk,
                        modifier = Modifier
                            .testTag("updates-needs-you-count")
                            .background(rosterNeedsYouContainer, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clearAndSetSemantics { contentDescription = label },
                    )
                }
                if (working > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("updates-working-count"),
                    ) {
                        RosterWorkingIndicator(
                            size = 10.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            label = localizedMobileCopy(UpdatesSummary.section(UpdateKind.WORKING)),
                        )
                        Text(
                            text = working.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = secondaryTint,
                        )
                    }
                }
            }
        }

        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = secondaryTint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun localizedUpdatesHeadline(updates: List<ChatUpdate>): String {
    val first = updates.firstOrNull() ?: return localizedMobileCopy(UpdatesSummary.headline(updates))
    return when (first.kind) {
        UpdateKind.NEEDS_YOU -> stringResource(R.string.mobile_updates_headline_needs_you, first.chat.name)
        UpdateKind.WORKING -> stringResource(R.string.mobile_updates_headline_working, first.chat.name)
        UpdateKind.TO_REVIEW -> stringResource(R.string.mobile_updates_headline_review, first.chat.name)
    }
}

@Composable
private fun localizedUpdatesSubline(updates: List<ChatUpdate>): String {
    val first = updates.firstOrNull() ?: return localizedMobileCopy(UpdatesSummary.subline(updates))
    val remaining = updates.size - 1
    if (remaining == 1) return stringResource(R.string.mobile_updates_one_more)
    if (remaining > 1) return stringResource(R.string.mobile_updates_more_count, remaining)
    return localizedUpdateLine(first.line.ifEmpty { " " })
}

@Composable
private fun localizedUpdatesCount(updates: List<ChatUpdate>): String =
    if (updates.isEmpty()) localizedMobileCopy(UpdatesSummary.count(updates))
    else stringResource(R.string.mobile_updates_active_count, updates.size)

@Composable
private fun localizedUpdateLine(line: String): String {
    if (line == "Queued — waiting for an available slot") {
        return stringResource(R.string.mobile_updates_queued_waiting)
    }
    val queued = QUEUED_MESSAGES.matchEntire(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (queued != null) return stringResource(R.string.mobile_updates_queued_count, queued)
    return localizedMobileCopy(line)
}

private val QUEUED_MESSAGES = Regex("^(\\d+) messages queued$")

/** What the pill opens: the active chats, grouped by what they need. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdatesSheet(
    updates: List<ChatUpdate>,
    faces: Map<String, MausState>,
    onOpen: (Chat) -> Unit,
    onDismiss: () -> Unit,
) {
    val sections = remember(updates) {
        UpdateKind.entries.mapNotNull { kind ->
            val items = updates.filter { it.kind == kind }
            if (items.isEmpty()) null else kind to items
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            modifier = Modifier.testTag("updates-list"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.mobile_updates_c76d1807), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(localizedUpdatesCount(updates), style = MaterialTheme.typography.labelMedium, color = secondaryTint)
                }
            }

            if (sections.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = UpdatesSummary.EMPTY_TITLE,
                        description = UpdatesSummary.EMPTY_DESCRIPTION,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }

            sections.forEach { (kind, items) ->
                item(key = "section-$kind") {
                    Text(
                        text = localizedMobileCopy(UpdatesSummary.section(kind)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 0.dp)
                            .semantics { heading() },
                    )
                }
                items(items, key = { it.id }) { update ->
                    UpdateRow(
                        update = update,
                        face = faces[update.id] ?: MausState.IDLE,
                        onOpen = { onOpen(update.chat) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(update: ChatUpdate, face: MausState, onOpen: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var answering by remember(update.id) { mutableStateOf(false) }
    val card = update.card

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("update-row.${update.id}")
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = MIN_TOUCH_TARGET)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RosterFace(
            size = 40.dp,
            color = update.chat.color,
            working = update.kind == UpdateKind.WORKING,
            badge = when (update.kind) {
                UpdateKind.NEEDS_YOU -> RosterFaceBadge.WAITING
                UpdateKind.WORKING -> null
                UpdateKind.TO_REVIEW -> RosterFaceBadge.UNREAD
            },
        ) {
            ChatAvatar(chat = update.chat, size = 40.dp, state = face, animated = false)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(update.chat.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = update.chat.threadTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = secondaryTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = localizedUpdateLine(update.line),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTint,
                maxLines = if (update.kind == UpdateKind.NEEDS_YOU) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (update.kind == UpdateKind.NEEDS_YOU && card != null && card.isPending) {
                if (card.skillRequest != null) {
                    Text(
                        stringResource(R.string.mobile_open_the_chat_to_review_skill_md_6225775b),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = secondaryTint,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    // The answers are the card's own options, exactly as the chat
                    // screen draws them — never a choice invented here.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        card.options.forEach { option ->
                            val refusal = ApprovalChoices.emphasis(option) == OptionEmphasis.SECONDARY
                            Button(
                                onClick = {
                                    haptics.play(TactileAction.CHOOSE_APPROVAL)
                                    answering = true
                                    scope.launch {
                                        ApprovalAnswers.choose(session, update.chat, card, option)
                                        answering = false
                                    }
                                },
                                enabled = !answering,
                                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                                colors = if (refusal) {
                                    ButtonDefaults.filledTonalButtonColors()
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                            ) {
                                Text(option, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp).size(18.dp),
        )
    }
}
