package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.openmausbot.companion.core.toolCategory
import com.openmausbot.companion.core.ToolCategory
import com.openmausbot.companion.core.failedTurnCause

import androidx.compose.ui.res.stringResource

import android.content.ClipData
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import com.openmausbot.companion.core.Markdown
import com.openmausbot.companion.core.MarkdownBlock
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.audio.VoiceNoteController
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.AttachedMessageContent
import com.openmausbot.companion.core.attachedFiles
import com.openmausbot.companion.core.generatedImages
import com.openmausbot.companion.core.voiceNotes
import com.openmausbot.companion.core.DisplayedMessageAttachment
import com.openmausbot.companion.core.DownloadedFile
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.ThreadRef
import com.openmausbot.companion.core.ToolActivity
import com.openmausbot.companion.core.forTask
import com.openmausbot.companion.core.label
import com.openmausbot.companion.core.routineExecutionRef
import com.openmausbot.companion.core.TranscriptCard
import com.openmausbot.companion.core.TranscriptCards
import com.openmausbot.companion.core.webhookContent
import com.openmausbot.companion.core.StreamingText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the clipboard shows this came from. */
private const val MESSAGE_CLIP_LABEL = "OpenMausMobile message"

/**
 * One row of the transcript — the port of `MessageRow` in `ios/App/ChatView.swift`.
 *
 * [endsRun] is the last bubble of a run from the same side: the one that gets the
 * tail. [TranscriptLayout.endsRun] decides it, over the whole transcript, so a row
 * never has to look at its neighbours.
 */
@Composable
fun MessageRow(
    chat: Chat,
    message: Message,
    endsRun: Boolean = true,
    /** Where a tapped link in a bot reply goes; null leaves it to the system. */
    openLink: ((String, Message) -> Unit)? = null,
    /** Open one exact user attachment through the authenticated computer route. */
    openAttachment: ((DisplayedMessageAttachment, Message, DownloadedFile?) -> Unit)? = null,
    /** Where an "Opened thread" chip goes; null leaves the chip a receipt. */
    openThread: ((ThreadRef) -> Unit)? = null,
    /** A turn audit is reachable even when its slim line is switched off. */
    digest: Message? = null,
    /** An isolated row is a run start; transcript callers pass the actual boundary. */
    showSender: Boolean = true,
) {
    RecompositionProbe { "message:${message.id}" }
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val clipboard = LocalClipboard.current
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var selectingText by remember { mutableStateOf<String?>(null) }
    var showingMeta by remember(message.id) { mutableStateOf(false) }
    var showingDigest by remember(message.id) { mutableStateOf(false) }

    val bot = (chat as? Chat.BotChat)?.bot
    // Runtime deltas do not change versions. Filter the thread's source before
    // folding it, rather than subscribing every visible row to the whole fleet.
    val versions = if (message.role == Message.Role.USER && message.kind == Message.Kind.TEXT) {
        val initial = remember(session, chat.threadId, message.id, message.parentId) {
            session.state.value.versions(message, chat.threadId)
        }
        val current by remember(session, chat.threadId, message.id, message.parentId) {
            session.state.distinctUntilChangedBy { it.messages[chat.threadId] }
                .map { it.versions(message, chat.threadId) }
        }.collectAsState(initial = initial)
        current
    } else emptyList()
    val versionIndex = versions.indexOfFirst { it.id == message.id }
    // The stand-in for an edit the computer has not answered yet. It has no
    // server identity, so nothing may react to it or edit it again.
    val editPending by remember(session, chat.threadId) {
        session.state.map { it.pendingEdits[chat.threadId] }.distinctUntilChanged()
    }.collectAsState(initial = session.state.value.pendingEdits[chat.threadId])
    val isPendingEdit = editPending?.placeholderId == message.id
    val mine = message.role == Message.Role.USER
    val requestedReveal = LocalTextArrival.current
    val accessible = touchExplorationEnabled()
    val reveal = remember(message.id) { requestedReveal } && !accessible
    val paced = if (!mine && message.kind == Message.Kind.TEXT)
        key(message.id) { rememberPacedText(message.text.orEmpty(), reveal = reveal) } else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(when (message.kind) {
                Message.Kind.ACTIVITY -> "step-${message.id}"
                else -> "message-${message.id}"
            })
            .semantics { if (paced != null) pacedText(paced) }
            // Nested links keep their own taps; the bubble owns details and the menu.
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = { menuOpen = true },
                onClick = { if (message.kind == Message.Kind.TEXT) showingMeta = !showingMeta },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Only a thread the phone knows gets an "Open run" button.
            val runRef = message.routineRun?.let { run ->
                val ref by remember(session, run) {
                    session.state.distinctUntilChangedBy { it.bots }
                        .map { it.routineExecutionRef(run) }.distinctUntilChanged()
                }.collectAsState(initial = session.state.value.routineExecutionRef(run))
                ref
            }
            CompositionLocalProvider(LocalPacedMessage provides paced) {
            MessageContent(
                chat = chat,
                message = message,
                endsRun = endsRun,
                showSender = showSender,
                haptics = haptics,
                openLink = openLink,
                openAttachment = openAttachment,
                openThread = openThread,
                runRef = runRef,
            )
            }

            if (showingMeta && message.kind == Message.Kind.TEXT) {
                Row(Modifier.testTag("message-meta-${message.id}").heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(message.at.toLong())),
                        style = MaterialTheme.typography.labelSmall, color = secondaryTint)
                    if (digest != null) Text("· ${localizedMobileCopy("What I did")}", color = chatTint.ink,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable(role = Role.Button) { showingDigest = true }.padding(vertical = 12.dp))
                }
            }
            message.comm?.let {
                Text(
                    text = stringResource(R.string.mobile_messaged_it_withname_bd9371e7, it.withName),
                    fontSize = 12.sp,
                    color = secondaryTint,
                )
            }

            // A request the person spoke on a Live call; the harness labels it.
            if (mine && message.via == "call") {
                Text(text = "via call", fontSize = 12.sp, color = secondaryTint)
            }

            message.reactions?.takeIf { it.isNotEmpty() }?.let { reactions ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Reactions.group(reactions).forEach { group ->
                        val tint = if (group.mine) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            secondaryTint
                        }
                        Text(
                            text = "${group.emoji} ${group.count}",
                            fontSize = 13.sp,
                            color = tint,
                            modifier = Modifier
                                .border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
                                .clickable {
                                    haptics.play(TactileAction.TOGGLE_REACTION)
                                    scope.launch {
                                        session.react(message, chat.threadId, group.emoji)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Versions are a bot idea: a room has no branch to switch (§12).
            if (versions.size > 1 && versionIndex >= 0 && bot != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val busy = bot.busy == true
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.mobile_previous_version_989537a3),
                        tint = if (versionIndex == 0 || busy) {
                            secondaryTint.copy(alpha = 0.4f)
                        } else {
                            secondaryTint
                        },
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(enabled = versionIndex > 0 && !busy) {
                                scope.launch {
                                    session.switchVersion(versions[versionIndex - 1], bot)
                                }
                            },
                    )
                    Text(
                        text = stringResource(R.string.mobile_versionindex_1_of_versions_size_91d50e4a, versionIndex + 1, versions.size),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryTint,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.mobile_next_version_514439d0),
                        tint = if (versionIndex + 1 >= versions.size || busy) {
                            secondaryTint.copy(alpha = 0.4f)
                        } else {
                            secondaryTint
                        },
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(enabled = versionIndex + 1 < versions.size && !busy) {
                                scope.launch {
                                    session.switchVersion(versions[versionIndex + 1], bot)
                                }
                            },
                    )
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (!isPendingEdit) Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Reactions.CHOICES.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clickable {
                                menuOpen = false
                                haptics.play(TactileAction.TOGGLE_REACTION)
                                scope.launch { session.react(message, chat.threadId, emoji) }
                            }
                            .padding(8.dp),
                    )
                }
            }
            MessageActions.copyableText(message)?.let { text ->
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mobile_copy_af74f7c5)) },
                    onClick = {
                        menuOpen = false
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText(MESSAGE_CLIP_LABEL, text)),
                            )
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mobile_select_text_9d49219e)) },
                    onClick = {
                        menuOpen = false
                        selectingText = text
                    },
                )
            }
            if (digest != null) DropdownMenuItem(
                text = { Text(localizedMobileCopy("What I did")) },
                onClick = { menuOpen = false; showingDigest = true },
            )
            // Attachment messages cannot be reconstructed by a text-only edit.
            // The policy also keeps their private transport paths out of the UI.
            val editableText = MessageActions.editableText(message)
            if (editableText != null && bot != null && !isPendingEdit) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mobile_edit_and_retry_f683a3c2)) },
                    enabled = bot.busy != true && editPending == null,
                    onClick = {
                        menuOpen = false
                        editText = editableText
                        editing = true
                    },
                )
            }
        }
    }

    if (editing && bot != null) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.mobile_edit_and_retry_f683a3c2)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mobile_this_creates_a_new_version_and_con_9a5d779d), fontSize = 14.sp)
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        label = { Text(stringResource(R.string.mobile_message_68f4145f)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    // `bot` is re-read from the collected state on every frame,
                    // so a bot that starts running while this dialog is open
                    // disables Send rather than sending an edit the harness will
                    // refuse with 409.
                    enabled = bot.busy != true && editText.isNotBlank(),
                    onClick = {
                        val text = editText.trim()
                        editing = false
                        if (text.isEmpty()) return@TextButton
                        scope.launch { session.edit(message, bot, text) }
                    },
                ) { Text(stringResource(R.string.mobile_send_9bc2575c)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.mobile_cancel_77dfd213)) }
            },
        )
    }

    selectingText?.let { text ->
        SelectableTextDialog(text = text, onDismiss = { selectingText = null })
    }
    if (showingDigest && digest != null) DigestSheet(digest, chat.name, chat.color) { showingDigest = false }
}

/**
 * Raw message text in a separate surface where Android can own the long-press
 * selection gesture. The bubble's long press is intentionally reserved for
 * reactions and message actions.
 */
@Composable
private fun SelectableTextDialog(text: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(text) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mobile_select_text_9d49219e)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionContainer {
                    Text(
                        text = text,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                Text(
                    stringResource(R.string.mobile_touch_and_hold_the_text_to_select__efc64a9c),
                    fontSize = 12.sp,
                    color = secondaryTint,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        // "Copied" after the clipboard has it, not before: the
                        // label is a report, and `setClipEntry` suspends.
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText(MESSAGE_CLIP_LABEL, text)),
                        )
                        copied = true
                    }
                },
            ) { Text(if (copied) stringResource(R.string.mobile_copied_8e3df45a) else stringResource(R.string.mobile_copy_all_9da9f044)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mobile_done_e9b450d1)) } },
    )
}

@Composable
private fun MessageContent(
    chat: Chat,
    message: Message,
    endsRun: Boolean,
    showSender: Boolean,
    haptics: Haptics,
    openLink: ((String, Message) -> Unit)?,
    openAttachment: ((DisplayedMessageAttachment, Message, DownloadedFile?) -> Unit)?,
    openThread: ((ThreadRef) -> Unit)?,
    runRef: ThreadRef?,
) {
    when (message.kind) {
        Message.Kind.TEXT -> TextBubble(chat.threadId, message, endsRun, openLink, openAttachment, chat.color, showSender)
        // A structured ask draws its own card: its answers are the model's
        // questions, not an allow/deny a tap could stand for.
        Message.Kind.OPTIONS -> if (QuestionCardRules.drawsQuestionCard(message)) {
            QuestionCardView(chat, message, haptics)
        } else {
            CardView(chat, message, haptics)
        }
        Message.Kind.ACTIVITY -> {
            ActivityChip(message.tool, message.threadRef, openThread, teammateReport = message.threadRef != null || message.comm != null,
                busy = chat.busy)
            // Claude Code too old for the model: offer the update on the
            // engine this bot's thread runs on. Rooms have no single engine.
            val claudeInstance = (chat as? Chat.BotChat)?.bot
                ?.let { it.forTask(chat.threadId) ?: it }
                ?.modelSelection?.instanceId
            if (message.tool?.claudeUpdate == true && claudeInstance != null) {
                ClaudeUpdateCard(messageId = message.id, instanceId = claudeInstance)
            }
        }
        Message.Kind.COMPACTION -> ReceiptChip(
            label = message.compaction?.chipText ?: message.text.orEmpty(),
            detail = message.compaction?.summary ?: message.text.orEmpty(),
        )
        Message.Kind.SCREEN -> ScreenShot(chat.threadId, message)
        // Work summaries have their own preference, independent of activity detail.
        Message.Kind.DIGEST -> TurnDigestChip(message, chat.name, chat.color)
        Message.Kind.ROUTINE_RUN -> RoutineRunCardView(
            message = message,
            openRun = if (runRef != null && openThread != null) {
                { openThread(runRef) }
            } else {
                null
            },
        )
        // A message kind from a newer computer. Almost everything the harness
        // sends carries `text`, so showing it is usually the whole message and
        // always better than a gap in the transcript. When there is nothing to
        // show, show nothing — a placeholder saying "unsupported" is a worse gap
        // than the gap.
        Message.Kind.UNKNOWN -> if (!message.text.isNullOrEmpty()) {
            TextBubble(chat.threadId, message, endsRun, openLink, openAttachment, chat.color, showSender)
        }
    }
}

@Composable
private fun TextBubble(
    threadId: String,
    message: Message,
    endsRun: Boolean,
    openLink: ((String, Message) -> Unit)?,
    openAttachment: ((DisplayedMessageAttachment, Message, DownloadedFile?) -> Unit)?,
    color: String,
    showSender: Boolean,
) {
    val mine = message.role == Message.Role.USER
    val tint = conversationTint(message.from?.color ?: color)
    val tail = TranscriptLayout.tail(message, endsRun)
    val paced = LocalPacedMessage.current
    val visible = paced?.visible ?: message.text.orEmpty()
    val revealEnd = remember(message.text, visible) {
        if (paced != null) StreamingText.revealedEnd(message.text.orEmpty(), visible.length, final = true) else visible.length
    }
    val markdown = remember(message.id) { Markdown.Incremental() }
    val revealBubble = LocalTextArrival.current && !touchExplorationEnabled()
    val fade = remember(message.id) { Animatable(if (revealBubble) 0f else 1f) }
    LaunchedEffect(message.id, paced?.motion, revealBubble) {
        if (paced?.motion == false || !revealBubble) fade.snapTo(1f)
        else if (fade.value < 1f) fade.animateTo(1f, tween(120))
    }
    // A patch still has its dedicated card; Markdown owns both embedded and whole tables.
    val card = remember(message.id, message.role, message.text) { if (mine) null else TranscriptCards.diff(message.text.orEmpty()) }
    val blocks = remember(message.text, revealEnd, paced?.revealing, mine, card) {
        if (mine || card != null) emptyList()
        else if (paced != null) markdown.blocks(message.text.orEmpty(), revealEnd, closePartial = paced.revealing)
        else Markdown.blocks(visible)
    }
    val hasTable = blocks.any { it is MarkdownBlock.Table }
    // Shared attachments are protocol tags in stored user text. They are not
    // prose, and a server-controlled path must never be presented as a link.
    val attached = remember(message.id, message.text) { AttachedMessageContent.parse(message.text.orEmpty()) }
    val webhook = remember(message) { message.webhookContent }
    // A card brings its own surface, so it drops the bubble — and with it the
    // tail, which is a bubble's chin and not a card's.
    val bubble = card == null
    // No face beside the bubble: the bot's face is in the header, and in a room
    // the name line says who spoke. The bubble sits at the edge.
    androidx.compose.runtime.CompositionLocalProvider(LocalConversationTint provides tint) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        // iOS spaces the far side with `Spacer(minLength:)`; a non-filling weight
        // lets the bubble shrink to its text while never crossing that gutter.
        if (mine) Spacer(Modifier.fillMaxWidth(0.22f))
        Column(
            modifier = Modifier.semantics { this[BubbleMotionAlpha] = fade.value }.graphicsLayer { alpha = fade.value }
                .weight(1f, fill = false)
                .widthIn(max = 640.dp)
                // Room for the tail below, so the next row does not sit on it.
                .padding(bottom = if (bubble && endsRun) SpeechBubble.tailDrop() else 0.dp)
                .then(
                    if (bubble) {
                        Modifier
                            .background(
                                if (mine) BubbleColor.mine else tint.theirs,
                                SpeechBubbleShape.of(tail),
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Rooms attribute each line to the member who said it. Only theirs:
            // your own bubble is already on your side.
            if (!mine && showSender) {
                message.from?.let {
                    Text(
                        text = it.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint.ink,
                    )
                }
            }
            // Voice notes sit above the attachment gallery, as they do on the
            // desktop transcript: the note is the message, not an appendix to it.
            message.voiceNotes.forEach { note ->
                VoiceNoteAttachmentView(threadId, message, note)
            }
            message.generatedImages.forEach { attachment ->
                SharedAttachmentView(threadId, message, attachment, openAttachment)
            }
            // Documents, audio and video a bot sent with attach_file (MOCA-155).
            // The card opens the file sheet; Open hands video to the player.
            message.attachedFiles.forEach { attachment ->
                SharedAttachmentView(
                    threadId, message, attachment, openAttachment,
                )
            }
            // Bots get markdown, you do not — the same split the desktop makes.
            // Markdown you did not intend is worse than markdown you did: a
            // message about `**` should show the asterisks.
            // Settled text is selectable, so a command, a URL or a paragraph can
            // be copied — as it can on iOS. The live bubble below is deliberately
            // left out: selecting text that is still growing fights the reader.
            when (card) {
                is TranscriptCard.Diff -> DiffCard(card)
                null -> if (webhook != null) {
                    WebhookMessageBody(webhook)
                } else if (mine) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        attached.attachments.forEach { attachment ->
                            SharedAttachmentView(
                                threadId = threadId,
                                message = message,
                                attachment = attachment,
                                onOpen = openAttachment,
                            )
                        }
                        if (attached.text.isNotEmpty()) {
                            SelectionContainer {
                                Text(
                                    text = attached.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = BubbleColor.mineText,
                                )
                            }
                        }
                    }
                } else {
                    MarkdownBlocks(blocks = blocks, settling = paced?.revealing == true && paced.motion,
                        openLink = openLink?.let { open -> { url -> open(url, message) } })
                }
            }
        }
        if (!mine) Spacer(Modifier.fillMaxWidth(if (hasTable || card != null) 0.08f else 0.20f))
    }
    }
}

@Composable
private fun SharedAttachmentView(
    threadId: String,
    message: Message,
    attachment: DisplayedMessageAttachment,
    onOpen: ((DisplayedMessageAttachment, Message, DownloadedFile?) -> Unit)?,
) {
    val foreground = MaterialTheme.colorScheme.onSurface
    if (attachment.kind == DisplayedMessageAttachment.Kind.IMAGE) {
        SharedImageAttachment(threadId, message, attachment, onOpen)
        return
    }
    val family = attachment.fileFamily
    Row(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(chatTint.theirs)
            .clickable(enabled = onOpen != null, role = Role.Button) {
                onOpen?.invoke(attachment, message, null)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .localizedSemantics(contentDescription = {
                stringResource(R.string.mobile_a11y_file_attachment, attachment.name)
            }),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = when (family) {
            DisplayedMessageAttachment.FileFamily.VIDEO -> R.string.mobile_file_video
            DisplayedMessageAttachment.FileFamily.AUDIO -> R.string.mobile_file_audio
            DisplayedMessageAttachment.FileFamily.DOCUMENT -> R.string.mobile_file_b4915d3a
        }
        val hint = if (family == DisplayedMessageAttachment.FileFamily.DOCUMENT) {
            R.string.mobile_tap_to_preview_fa5ce0ea
        } else {
            R.string.mobile_tap_to_play
        }
        Text(stringResource(label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = foreground.copy(alpha = 0.68f))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                attachment.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(hint), fontSize = 12.sp, color = foreground.copy(alpha = 0.68f))
        }
    }
}

private sealed interface AttachmentThumbnailState {
    data object Loading : AttachmentThumbnailState
    data class Ready(val file: DownloadedFile, val image: ImageBitmap) : AttachmentThumbnailState
    data object Failed : AttachmentThumbnailState
}

@Composable
private fun SharedImageAttachment(
    threadId: String,
    message: Message,
    attachment: DisplayedMessageAttachment,
    onOpen: ((DisplayedMessageAttachment, Message, DownloadedFile?) -> Unit)?,
) {
    val foreground = MaterialTheme.colorScheme.onSurface
    val session = LocalCompanion.current.session
    var attempt by remember(message.id, attachment.path) { mutableStateOf(0) }
    var state by remember(message.id, attachment.path) {
        mutableStateOf<AttachmentThumbnailState>(AttachmentThumbnailState.Loading)
    }
    LaunchedEffect(threadId, message.id, attachment.path, attempt) {
        state = AttachmentThumbnailState.Loading
        // A failed inline preview owns its own retry UI; it must not replace an
        // unrelated composer or account alert while this row scrolls on screen.
        val downloaded = session.downloadFile(
            threadId,
            message.id,
            attachment.path,
            reportError = false,
            cacheResult = true,
        )
        if (downloaded == null) {
            state = AttachmentThumbnailState.Failed
            return@LaunchedEffect
        }
        val bitmap = withContext(Dispatchers.Default) {
            decodeAttachmentImage(downloaded.data, AttachmentImageRules.THUMBNAIL_EDGE)
        }
        state = bitmap?.let { AttachmentThumbnailState.Ready(downloaded, it) }
            ?: AttachmentThumbnailState.Failed
    }

    val ready = state as? AttachmentThumbnailState.Ready
    Column(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(chatTint.theirs)
            .clickable(enabled = ready != null && onOpen != null, role = Role.Button) {
                ready?.let { onOpen?.invoke(attachment, message, it.file) }
            }
            .localizedSemantics(contentDescription = {
                stringResource(R.string.mobile_a11y_image_attachment, attachment.name)
            }),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = state) {
                AttachmentThumbnailState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                AttachmentThumbnailState.Failed -> AttachmentLoadFailure(
                    label = stringResource(R.string.mobile_image_unavailable),
                    foreground = foreground,
                    onRetry = { attempt += 1 },
                )
                is AttachmentThumbnailState.Ready -> Image(
                    bitmap = current.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)),
                )
            }
        }
        Text(
            attachment.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AttachmentLoadFailure(label: String, foreground: Color = BubbleColor.mineText, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = foreground.copy(alpha = 0.70f),
            modifier = Modifier.size(20.dp),
        )
        Text(label, fontSize = 13.sp, color = foreground.copy(alpha = 0.80f))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.mobile_retry_9f5cd8a2)) }
    }
}

/** Where the bubble's clip bytes are: fetched on first play, then kept for replay. */
private sealed interface VoiceNoteClipState {
    data object NotLoaded : VoiceNoteClipState
    data object Loading : VoiceNoteClipState
    data class Ready(val data: ByteArray) : VoiceNoteClipState
    data object Failed : VoiceNoteClipState
}

/** The desktop bubble's clock: m:ss, and 0:00 for anything not yet audible. */
private fun voiceNoteClock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val wholeSeconds = ms / 1000
    return (wholeSeconds / 60).toString() + ":" + (wholeSeconds % 60).toString().padStart(2, '0')
}

/**
 * One voice note in the transcript, matching the desktop VoiceNoteBubble:
 * a play button, a scrub bar, and the clip's length. Playback is app-scoped
 * (CompanionEnvironment.voiceNotes), so a note keeps playing while its row
 * scrolls away, and the one-voice rule rides the same audio-focus gate the
 * TTS preview uses: starting a note (or a preview) pauses any other voice
 * rather than talking over it. The clip's bytes are fetched through the same
 * authenticated file route as image thumbnails, but only on first play — a
 * note nobody opens costs no request, and a replay never refetches.
 *
 * While this phone is on a Live call the play button is off, with the reason
 * under the bubble: a note asks for the audio focus the call holds, and the
 * call ends when it loses it (as the profile sheet keeps its voice preview off).
 */
@Composable
private fun VoiceNoteAttachmentView(
    threadId: String,
    message: Message,
    note: DisplayedMessageAttachment,
) {
    val foreground = MaterialTheme.colorScheme.onSurface
    val session = LocalCompanion.current.session
    val player = LocalCompanion.current.voiceNotes
    val liveCall by LocalCompanion.current.liveCalls.state.collectAsState()
    val callHoldsAudio = liveCall.holdsMedia
    val scope = rememberCoroutineScope()
    val key = remember(message.id, note.path) { message.id + ":" + note.path }
    var clip by remember(message.id, note.path) { mutableStateOf<VoiceNoteClipState>(VoiceNoteClipState.NotLoaded) }
    // The scrub position while the slider is held; null when it tracks playback.
    var scrub by remember(key) { mutableStateOf<Float?>(null) }

    fun startPlayback(data: ByteArray) {
        val failure = player.play(key, data) ?: return
        // A Live call took the audio while the clip downloaded: the player
        // refused it, and the clip waits, ready, for the call to end.
        if (failure != VoiceNoteController.DURING_LIVE_CALL) clip = VoiceNoteClipState.Failed
    }

    fun loadAndPlay() {
        clip = VoiceNoteClipState.Loading
        scope.launch {
            val downloaded = session.downloadFile(
                threadId,
                message.id,
                note.path,
                reportError = false,
                cacheResult = true,
            )
            if (downloaded == null) {
                clip = VoiceNoteClipState.Failed
            } else {
                clip = VoiceNoteClipState.Ready(downloaded.data)
                startPlayback(downloaded.data)
            }
        }
    }

    val active = player.playback.collectAsState().value?.takeIf { it.key == key }
    val playing = active?.playing == true

    // Late engine failures park the clip; the bubble's retry row is its UI.
    LaunchedEffect(key) {
        player.playbackErrors.collectLatest {
            if (it.key == key) clip = VoiceNoteClipState.Failed
        }
    }
    // Pull the engine's position while it plays; the clock reads it back.
    LaunchedEffect(key, playing) {
        while (playing) {
            player.refresh()
            delay(200)
        }
    }

    if (clip is VoiceNoteClipState.Failed) {
        AttachmentLoadFailure(
            label = "Voice note unavailable",
            foreground = foreground,
            onRetry = { clip = VoiceNoteClipState.NotLoaded },
        )
        return
    }

    // The wire's estimate until the engine loads metadata, then the real length.
    val durationMs = active?.durationMs ?: note.durationMs?.toLong()?.takeIf { it > 0 }
    val durationSeconds = durationMs?.let { it / 1000f } ?: 0f
    val positionMs = scrub?.toLong() ?: (active?.positionMs ?: 0L)

    // Pausing never takes the audio; starting or resuming would.
    val playable = playing || !callHoldsAudio
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(chatTint.theirs)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(chatTint.ink.copy(alpha = if (playable) 1f else 0.38f))
                    .clickable(role = Role.Button, enabled = playable) {
                        when {
                            playing -> player.pause()
                            // Disabled is how it looks; this is what stops a tap
                            // that reaches the click action anyway.
                            callHoldsAudio -> Unit
                            clip is VoiceNoteClipState.Loading -> Unit
                            active != null && player.resumable(key) ->
                                player.resume()?.let { if (it != VoiceNoteController.DURING_LIVE_CALL) clip = VoiceNoteClipState.Failed }
                            clip is VoiceNoteClipState.Ready ->
                                startPlayback((clip as VoiceNoteClipState.Ready).data)
                            else -> loadAndPlay()
                        }
                    }
                    .semantics {
                        contentDescription = if (playing) "Pause voice note" else "Play voice note"
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    clip is VoiceNoteClipState.Loading && active == null ->
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = chatTint.actionText,
                        )
                    playing -> VoiceNotePauseGlyph(chatTint.actionText)
                    else -> Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = chatTint.actionText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Slider(
                // The slider works in seconds; without an explicit range Compose clamps
                // it to 0f..1f and scrubs can only land inside the first second.
                value = if (durationSeconds > 0f) (positionMs / 1000f).coerceIn(0f, durationSeconds) else 0f,
                valueRange = if (durationSeconds > 0f) 0f..durationSeconds else 0f..1f,
                onValueChange = { scrub = it * 1000f },
                onValueChangeFinished = {
                    val target = scrub
                    scrub = null
                    if (target != null && active != null) player.seek(key, target.toLong())
                },
                // Like the desktop range input: no scrubbing until the length is known.
                enabled = active != null && durationMs != null,
                colors = SliderDefaults.colors(thumbColor = chatTint.ink, activeTrackColor = chatTint.ink, inactiveTrackColor = chatTint.inset),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Seek voice note" },
            )
            Text(
                voiceNoteClock(positionMs) + " / " + (durationMs?.let(::voiceNoteClock) ?: "--:--"),
                fontSize = 11.sp,
                color = foreground.copy(alpha = 0.80f),
            )
        }
        if (!playable) {
            Text(
                LiveCallRules.VOICE_NOTE_DURING_CALL,
                fontSize = 11.sp,
                color = foreground.copy(alpha = 0.80f),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/** The pause glyph the core icon set does not carry, drawn at the button's scale. */
@Composable
private fun VoiceNotePauseGlyph(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val bar = size.width / 5f
        val gap = size.width / 5f
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(bar, size.height),
            cornerRadius = CornerRadius(bar / 2f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(bar + gap, 0f),
            size = Size(bar, size.height),
            cornerRadius = CornerRadius(bar / 2f),
        )
    }
}

/** A step keeps its thread link and teammate report; raw tool logs stay on the computer. */
@Composable
private fun ActivityChip(
    tool: ToolActivity?,
    threadRef: ThreadRef? = null,
    openThread: ((ThreadRef) -> Unit)? = null,
    teammateReport: Boolean = false,
    busy: Boolean = true,
) {
    if (tool == null) return
    val failed = tool.ok == false
    val status = ActivityReceipt.status(tool.ok)
    val ink = if (failed) MaterialTheme.colorScheme.error else chatTint.ink
    val haptics = rememberHaptics()
    if (tool.name.startsWith("notice:")) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = secondaryTint, modifier = Modifier.size(14.dp))
            Text(tool.label.removePrefix("notice:").trim(), style = MaterialTheme.typography.bodySmall,
                color = secondaryTint, modifier = Modifier.padding(start = 6.dp))
        }
        return
    }
    val failedTurn = failedTurnCause(tool.name) != null
    Column(Modifier.then(if (failedTurn)
        Modifier.fillMaxWidth(0.92f).background(ink.copy(alpha = 0.10f), RoundedCornerShape(20.dp)).padding(14.dp)
        else Modifier.padding(vertical = 8.dp)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.then(if (threadRef != null && openThread != null)
            Modifier.heightIn(min = MIN_TOUCH_TARGET).clickable(role = Role.Button) {
                haptics.play(TactileAction.OPEN_THREAD_CHIP); openThread(threadRef)
            } else Modifier).semantics(mergeDescendants = true) {
                contentDescription = ActivityReceipt.announcement(tool.label, status)
            }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            if (failedTurn) Icon(Icons.Filled.Warning, null, tint = ink, modifier = Modifier.size(22.dp))
            else StepBadge(toolCategory(tool.name), failed)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tool.label, style = MaterialTheme.typography.bodyMedium, maxLines = if (failedTurn) Int.MAX_VALUE else 2,
                    color = MaterialTheme.colorScheme.onSurface)
                tool.summary?.takeIf(String::isNotBlank)?.let { summary ->
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = secondaryTint,
                        fontFamily = if (toolCategory(tool.name) == ToolCategory.SHELL) FontFamily.Monospace else null,
                        maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            }
            if (failed) StepFailure(Modifier.size(16.dp))
            else if (tool.ok == null && busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = ink)
        }
        tool.output?.trim()?.takeIf { teammateReport && it.isNotEmpty() }?.let { output ->
            var expanded by remember(output) { mutableStateOf(false) }
            Text(output, style = MaterialTheme.typography.bodySmall, color = secondaryTint,
                maxLines = if (expanded) Int.MAX_VALUE else TOOL_OUTPUT_LINES, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 32.dp).heightIn(min = 48.dp).clickable(role = Role.Button) {
                    haptics.play(TactileAction.TOGGLE_ACTIVITY_RUN); expanded = !expanded
                }.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" })
        }
    }
}

/** How much of a teammate's report shows before a tap opens the rest. */
private const val TOOL_OUTPUT_LINES = 3

/**
 * A quiet chip under a reply for the harness's receipts (the work digest, a
 * compaction record): one line, and the full text on tap. Port of
 * `ReceiptChip` in `ios/App/ChatView.swift`.
 */
@Composable
private fun ReceiptChip(label: String, detail: String) {
    if (label.isEmpty()) return
    var expanded by remember(label) { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Column(
        modifier = Modifier
            .padding(start = 4.dp)
            .heightIn(min = MIN_TOUCH_TARGET)
            .clickable(role = Role.Button) {
                haptics.play(TactileAction.TOGGLE_ACTIVITY_RUN)
                expanded = !expanded
            }
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_tool_layers), null, modifier = Modifier.size(14.dp), tint = secondaryTint)
            Text(text = label, fontSize = 13.sp, maxLines = 1, color = secondaryTint)
        }
        if (expanded && detail.isNotEmpty() && detail != label) {
            Text(text = detail, fontSize = 12.sp, color = secondaryTint)
        }
    }
}

/** A reversible trail; the stable first receipt owns expansion while later steps arrive. */
@Composable
fun ActivityRunChip(items: List<Message>, openThread: ((ThreadRef) -> Unit)? = null, busy: Boolean = true) {
    if (items.isEmpty()) return
    val haptics = rememberHaptics()
    var expanded by remember(items.first().id) { mutableStateOf(false) }
    val running = busy && items.any { it.tool?.ok == null }
    val summary = stringResource(if (running) R.string.mobile_running_steps else R.string.mobile_ran_steps, items.size)
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "Step disclosure")
    val connector = MaterialTheme.colorScheme.outlineVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.testTag("step-run-run.${items.first().id}").animateContentSize(spring(dampingRatio = 0.82f, stiffness = 380f))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
            expanded = !expanded; haptics.play(TactileAction.TOGGLE_ACTIVITY_RUN)
        }.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width((22 + (minOf(3, items.size) - 1) * 16).dp).height(22.dp)) {
                items.take(3).forEachIndexed { index, item ->
                    StepBadge(toolCategory(item.tool?.name.orEmpty()), modifier = Modifier.padding(start = (index * 16).dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape))
                }
            }
            Text(if (running) "$summary · ${items.last().tool?.label.orEmpty()}" else summary,
                style = MaterialTheme.typography.bodyMedium, color = secondaryTint, maxLines = 2, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = secondaryTint, modifier = Modifier.size(18.dp).rotate(angle))
        }
        AnimatedVisibility(expanded) {
            Column {
                items.forEachIndexed { index, item ->
                    StepReveal(index) {
                        Row(Modifier.testTag("step-${item.id}").drawBehind {
                            val x = if (rtl) size.width - 11.dp.toPx() else 11.dp.toPx()
                            if (index > 0) drawLine(connector, androidx.compose.ui.geometry.Offset(x, 0f),
                                androidx.compose.ui.geometry.Offset(x, 8.dp.toPx()), 1.dp.toPx())
                            if (index < items.lastIndex) drawLine(connector, androidx.compose.ui.geometry.Offset(x, 30.dp.toPx()),
                                androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
                        }) {
                            ActivityChip(item.tool, item.threadRef, openThread,
                                teammateReport = item.threadRef != null || item.comm != null, busy = busy)
                        }
                    }
                }
            }
        }
    }
}


/**
 * An option card. When it still has a request behind it, this is the screen the
 * companion exists for — a bot stopped, and only a person can let it continue.
 */
@Composable
private fun CardView(chat: Chat, message: Message, haptics: Haptics) {
    val card = message.card ?: return
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var answering by remember(message.id) { mutableStateOf(false) }
    var wasPending by remember(message.id) { mutableStateOf(card.isPending) }
    LaunchedEffect(card.answered, card.dismissed) {
        if (wasPending && (card.answered != null || card.dismissed == true)) haptics.play(HapticCue.SUCCESS)
        wasPending = card.isPending
    }
    val skillRequest = card.skillRequest

    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .background(chatTint.theirs, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(card.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        // The detail of what is being approved is the thing worth copying.
        SelectionContainer {
            Text(card.subtitle, fontSize = 15.sp, color = secondaryTint)
        }

        card.held?.let {
            Text(it, fontSize = 13.sp, color = Color(MausPalette.argb("orange")))
        }

        skillRequest?.let { skill ->
            val reviewed = skill.reviewedSha256
            if (reviewed != null) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.mobile_review_the_complete_skill_md_61fb9a9a),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.mobile_sha256_reviewed_take_8_d4013810, reviewed.take(8)),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = secondaryTint,
                        )
                    }
                    SelectionContainer {
                        Text(
                            stringResource(
                                R.string.mobile_source_skill_source_unknown_6370895d,
                                skill.source ?: stringResource(R.string.mobile_unknown_bc7819b3),
                            ),
                            fontSize = 11.sp,
                            color = secondaryTint,
                        )
                    }
                    SelectionContainer {
                        Text(
                            skill.preview.orEmpty(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .background(
                                    chatTint.inset,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(10.dp),
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(MausPalette.argb("orange")),
                    )
                    Text(
                        stringResource(R.string.mobile_old_proposal_hint),
                        fontSize = 12.sp,
                        color = Color(MausPalette.argb("orange")),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (card.isPending) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // The buttons are the card's own options, never a string
                // invented here. Session maps the choice to allow/deny/answer.
                card.options.forEach { option ->
                    val refusal = ApprovalChoices.emphasis(option) == OptionEmphasis.SECONDARY
                    Button(
                        onClick = {
                            haptics.play(TactileAction.CHOOSE_APPROVAL)
                            answering = true
                            scope.launch {
                                ApprovalAnswers.choose(session, chat, card, option)
                                answering = false
                            }
                        },
                        enabled = !answering && (
                            skillRequest == null ||
                                refusal ||
                                skillRequest.reviewedSha256 != null
                            ),
                        // Same `isRefusal` that picks the allow choice picks the
                        // weight, so the most sensible action on the most
                        // sensitive screen is not the same shape as the refusal.
                        colors = if (refusal) {
                            ButtonDefaults.filledTonalButtonColors(containerColor = chatTint.inset, contentColor = chatTint.ink)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = chatTint.ink, contentColor = chatTint.actionText)
                        },
                    ) {
                        Text(option)
                    }
                }
            }

            // The grant key comes from the card. The phone never derives its
            // own, so it cannot permit something subtly wider than the computer
            // would have. The same goes for the answer: it is one of the options
            // the card offered, never a string invented here.
            val alwaysAllow = ApprovalChoices.alwaysAllowChoice(card)
            if (alwaysAllow != null && chat is Chat.BotChat) {
                TextButton(
                    onClick = {
                        haptics.play(TactileAction.GRANT_APPROVAL)
                        answering = true
                        scope.launch {
                            ApprovalAnswers.grant(session, chat, card, alwaysAllow)
                            answering = false
                        }
                    },
                    enabled = !answering,
                ) {
                    Text(stringResource(R.string.mobile_always_allow_this_tool_2ce82a7e), fontSize = 14.sp)
                }
            }
        } else {
            val answered = card.answered
            if (answered != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = secondaryTint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(answered, fontSize = 14.sp, color = secondaryTint)
                }
            } else if (card.expired == true) {
                Text("Expired — ask for a fresh proposal", fontSize = 14.sp, color = secondaryTint)
            }
        }
    }
}

/**
 * A frame of the bot's computer. In the paged shape the pixels are not in the
 * transcript — they are fetched here, once, when the row appears.
 */
@Composable
private fun ScreenShot(threadId: String, message: Message) {
    val session = LocalCompanion.current.session
    var attempt by remember(message.id) { mutableStateOf(0) }
    var state by remember(message.id) { mutableStateOf<ScreenShotState>(ScreenShotState.Loading) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth(0.92f)) {
        val renderedWidthPixels = with(LocalDensity.current) { maxWidth.toPx().toInt().coerceAtLeast(1) }
        LaunchedEffect(threadId, message.id, attempt, renderedWidthPixels) {
            state = ScreenShotState.Loading
            val bytes = try {
                message.png?.let { encoded ->
                    withContext(Dispatchers.Default) {
                        runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
                    }
                } ?: if (message.hasImage == true) session.image(threadId, message.id) else null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            val bitmap = bytes?.let {
                withContext(Dispatchers.Default) {
                    decodeScreenShotImage(it, renderedWidthPixels)
                }
            }
            state = bitmap?.let(ScreenShotState::Ready) ?: ScreenShotState.Failed
        }

        val aspectRatio = (state as? ScreenShotState.Ready)?.image?.let { image ->
            image.width.toFloat() / image.height.coerceAtLeast(1).toFloat()
        } ?: (16f / 10f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(20.dp))
                .background(chatTint.theirs)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = state) {
                ScreenShotState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                ScreenShotState.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(stringResource(R.string.mobile_screenshot_unavailable_cfe6fcbe), fontSize = 13.sp, color = secondaryTint)
                    TextButton(onClick = { attempt += 1 }) { Text(stringResource(R.string.mobile_retry_9f5cd8a2)) }
                }
                is ScreenShotState.Ready -> Image(
                    bitmap = current.image,
                    contentDescription = stringResource(R.string.mobile_a_frame_of_this_bot_s_computer_39b6a5bb),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

private sealed interface ScreenShotState {
    data object Loading : ScreenShotState
    data class Ready(val image: ImageBitmap) : ScreenShotState
    data object Failed : ScreenShotState
}

/**
 * The reply as it is being typed, styled to match the settled bubble it is about
 * to become — the handover should be invisible, and any difference in padding or
 * corner radius reads as the message jumping on arrival.
 *
 * A caret rather than a spinner: a spinner says "something is happening
 * somewhere", which the reader already knows. A caret at the end of real text
 * says how far along it is.
 */
@Composable
fun StreamingBubble(text: String?, reasoning: String?) {
    val paced = rememberPacedText(text.orEmpty(), streamOpen = !text.isNullOrEmpty())
    val recordVisible = LocalLiveTextVisibility.current
    DisposableEffect(recordVisible) { onDispose { recordVisible(false) } }
    val markdown = remember { Markdown.Incremental() }
    val revealEnd = remember(text, paced.visible) {
        StreamingText.revealedEnd(text.orEmpty(), paced.visible.length, final = false)
    }
    val hasVisibleText = remember(text, revealEnd) { (0 until revealEnd).any { !text.orEmpty()[it].isWhitespace() } }
    SideEffect { recordVisible(hasVisibleText) }
    val blocks = remember(text, revealEnd) { markdown.blocks(text.orEmpty(), revealEnd, closePartial = true) }
    val hasTable = blocks.any { it is MarkdownBlock.Table }
    Column(Modifier.fillMaxWidth().testTag("streaming-bubble").semantics { pacedText(paced) }) {
        if (!reasoning.isNullOrEmpty()) ThinkingView(reasoning, answering = !text.isNullOrEmpty())
        if (!text.isNullOrEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f, fill = false).widthIn(max = 640.dp).padding(bottom = SpeechBubble.tailDrop())
                .background(chatTint.theirs, SpeechBubbleShape.of(BubbleTail.LEADING)).padding(horizontal = 14.dp, vertical = 9.dp)) {
                MarkdownBlocks(blocks, caret = true, settling = paced.revealing && paced.motion, caretAlpha = paced.caretOpacity)
            }
            Spacer(Modifier.fillMaxWidth(if (hasTable) 0.08f else 0.20f))
        }
    }
}

/**
 * The beat between "go" and the first token — the port of the `else if
 * current.busy` branch of `ChatView.swift` and of `TypingIndicatorView`.
 *
 * The reason this exists is the sentence in the semantics block, not the dots.
 * Busy already reaches a sighted reader twice over — the mascot wears a working
 * face and the composer offers an interrupt — and reached a TalkBack reader
 * through neither. The row is a polite live region, so it is spoken when it
 * appears, and it carries a name of its own, so it can also be found by swiping
 * to the end of the transcript.
 *
 * Drawn in the same bubble as the reply that will replace it, and in the bot's
 * own colour, so the handover is the text arriving rather than the shape
 * changing. Everything Apple about the original — the capsule, the secondary
 * fill, the `TimelineView`, the scale wave — is left where it was; see
 * [WorkingDots].
 */
@Composable
fun WorkingBubble(name: String, color: String) {
    val clock = remember { MausFrameClock() }
    // Android says "reduce motion" through the animator duration scale, and this
    // reads it the way MausAvatar does — through a snapshotFlow, so turning the
    // setting off while a turn is running stops the dots on the next frame
    // rather than at the end of the turn. At zero they hold their rest alpha:
    // still three dots, just still ones.
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val durationScale = coroutineContext[MotionDurationScale]
        snapshotFlow { (durationScale?.scaleFactor ?: 1f) > 0f }
            .collectLatest { live ->
                moving = live
                if (!live) return@collectLatest
                while (true) withFrameNanos(clock.onFrame)
            }
    }

    val dots = conversationTint(color).ink
    val label = LiveTail.workingLabel(name)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Box(
            modifier = Modifier
                .padding(bottom = SpeechBubble.tailDrop())
                .background(BubbleColor.theirs, SpeechBubbleShape.of(BubbleTail.LEADING))
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .semantics {
                    contentDescription = label
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Canvas(modifier = Modifier.size(WORKING_DOTS_WIDTH, WORKING_DOT)) {
                // Read in the draw phase: a tick repaints the dots without
                // recomposing the bubble, let alone the transcript around it.
                val elapsed = clock.nanos.longValue
                val live = moving
                val radius = size.height * 0.5f
                val step = size.height + WORKING_DOT_GAP.toPx()
                for (index in 0 until WorkingDots.COUNT) {
                    drawCircle(
                        color = dots,
                        radius = radius * if (live) (0.75f + 0.25f * WorkingDots.alpha(index, elapsed, true)) else 1f,
                        center = Offset(radius + index * step, radius),
                        alpha = WorkingDots.alpha(index, elapsed, live),
                    )
                }
            }
        }
        Spacer(Modifier.width(44.dp))
    }
}

private val WORKING_DOT = 7.dp
private val WORKING_DOT_GAP = 5.dp
private val WORKING_DOTS_WIDTH =
    WORKING_DOT * WorkingDots.COUNT + WORKING_DOT_GAP * (WorkingDots.COUNT - 1)
