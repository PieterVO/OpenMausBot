package com.openmausbot.companion.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun TurnDigestChip(message: Message, botName: String = "Bot", color: String = "blue", showTitle: Boolean = false) {
    val presentation = remember(message) { DigestPresentation.from(message) }
    var open by remember(message.id) { mutableStateOf(false) }
    val tint = chatTint
    // Problem text sits on the plain transcript ground, where mascot orange is
    // ~2.5:1 in light: a deeper orange there (5:1 on white), system orange in dark.
    val warning = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF9F0A) else Color(0xFFB35400)
    val line = if (showTitle) localizedMobileCopy("What I did") else localizedDigestLine(presentation)
    val label = androidx.compose.ui.text.buildAnnotatedString {
        if (presentation.hasProblem && !showTitle) {
            val end = line.indexOf(" · ").takeIf { it >= 0 } ?: line.length
            pushStyle(androidx.compose.ui.text.SpanStyle(color = warning))
            append(line.substring(0, end))
            pop()
            append(line.substring(end))
        } else append(line)
    }
    Row(Modifier.testTag("digest-line-${message.id}").heightIn(min = 48.dp).clickable(role = Role.Button) { open = true },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (presentation.hasProblem) Icon(Icons.Filled.Warning, null, tint = warning, modifier = Modifier.size(14.dp))
        else ToolGlyph(ToolCategory.PLAN, tint.ink, Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = secondaryTint, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = secondaryTint, modifier = Modifier.size(14.dp))
    }
    if (open) DigestSheet(message, botName, color) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DigestSheet(message: Message, botName: String, color: String, onDismiss: () -> Unit) {
    val model = remember(message) { DigestPresentation.from(message) }
    val tint = conversationTint(color)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var copied by remember { mutableStateOf(false) }
    val plain = message.text?.takeIf(String::isNotBlank) ?: model.sections.joinToString("\n\n") { section ->
        listOfNotNull(section.label?.replaceFirstChar { it.uppercase() }, section.items.joinToString("\n")).joinToString("\n")
    }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("digest-sheet"), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("OpenMausBot work summary", plain))) }
                haptics.play(HapticCue.SELECT); copied = true
            }) { Text(localizedMobileCopy(if (copied) "Copied" else "Copy"), color = tint.ink) }
            TextButton(onClick = onDismiss) { Text(localizedMobileCopy("Done"), color = tint.ink) }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MausAvatar(color, size = 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.mobile_chat_what_bot_did, botName), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(listOfNotNull(model.duration?.let { stringResource(R.string.mobile_chat_worked_duration, it) }, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.at.toLong()))).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = secondaryTint)
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (model.toolCalls > 0) StatPill(pluralStringResource(R.plurals.mobile_chat_tool_count, model.toolCalls, model.toolCalls), tint)
                if (model.files.count > 0) StatPill(pluralStringResource(R.plurals.mobile_chat_file_count, model.files.count, model.files.count), tint)
                if (model.memory.isNotEmpty()) StatPill(pluralStringResource(R.plurals.mobile_chat_memory_count, model.memory.size, model.memory.size), tint)
                model.tokens?.let { StatPill(stringResource(R.string.mobile_chat_tokens, if (it >= 1000) String.format(Locale.getDefault(), "%.1fk", it / 1000.0) else it.toString()), tint) }
                model.costUsd?.let { StatPill(String.format(Locale.US, "$%.2f", it), tint) }
            }
            if (message.digest == null) {
                model.sections.forEach { section -> DigestSection(section.label?.replaceFirstChar { it.uppercase() }) {
                    section.items.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                } }
            } else {
                if (model.files.count > 0 || (model.files.truncated ?: 0) > 0) DigestSection(localizedMobileCopy("Files")) {
                    model.files.added.forEach { DigestFile(it, "added", Color(MausPalette.argb("green"))) }
                    model.files.changed.forEach { DigestFile(it, "changed", BubbleColor.mine) }
                    model.files.deleted.forEach { DigestFile(it, "deleted", MaterialTheme.colorScheme.error) }
                    model.files.truncated?.takeIf { it > 0 }?.let { Text("+$it more", style = MaterialTheme.typography.labelSmall, color = secondaryTint) }
                }
                if (model.tools.isNotEmpty()) DigestSection(localizedMobileCopy("Tools")) {
                    model.tools.forEach { tool -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolGlyph(toolCategory(tool.name), tint.ink, Modifier.size(18.dp))
                            Text(tool.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("×${tool.count}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"))
                            if (tool.failed > 0) Text("${tool.failed} failed", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        tool.sample?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = secondaryTint) }
                    } }
                }
                if (model.memory.isNotEmpty()) DigestSection(localizedMobileCopy("Memory")) {
                    model.memory.forEach { entry -> Text("${entry.path} · ${entry.kind}", style = MaterialTheme.typography.bodyMedium) }
                }
                model.sections.filter { it.label == null }.flatMap { it.items }.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryTint) }
            }
            model.coverageNote?.let { Text(localizedMobileCopy(it), style = MaterialTheme.typography.bodySmall, color = secondaryTint) }
        }
    }
}

@Composable
private fun StatPill(text: String, tint: ConversationTint) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = tint.ink,
        modifier = Modifier.background(tint.glyphFill, CircleShape).padding(horizontal = 10.dp, vertical = 7.dp))
}

@Composable
private fun DigestSection(title: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
    }
}

@Composable
private fun DigestFile(path: String, kind: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(when (kind) { "added" -> Icons.Filled.Add; "deleted" -> Icons.Filled.Close; else -> Icons.Filled.Edit }, kind,
            tint = color, modifier = Modifier.size(22.dp))
        Column {
            Text(path.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge)
            path.substringBeforeLast('/', "").takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = secondaryTint) }
        }
    }
}
