package com.openmausbot.companion.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.*

@Composable
internal fun PlanCard(row: TranscriptRow.Plan) {
    val plan = row.plan
    val tint = chatTint
    val motion = motionEnabled()
    val haptics = rememberHaptics()
    var previousDone by remember(row.id) { mutableIntStateOf(plan.done) }
    LaunchedEffect(plan.done) {
        if (plan.done > previousDone) haptics.play(HapticCue.SELECT)
        previousDone = plan.done
    }
    var expanded by remember(row.id) { mutableStateOf(false) }
    val progress by animateFloatAsState(if (plan.total == 0) 0f else plan.done.toFloat() / plan.total,
        if (motion) spring(dampingRatio = 0.82f, stiffness = 380f) else snap())
    val activeIndex = plan.items.indexOfFirst { it.status == TodoStatus.ACTIVE }
    val start = if (expanded || plan.items.size <= 6) 0 else (activeIndex - 2).coerceIn(0, plan.items.size - 6)
    val visible = if (expanded) plan.items.indices else start until minOf(start + 6, plan.items.size)
    val planLabel = stringResource(R.string.mobile_chat_plan_a11y, plan.done, plan.total)
    Column(Modifier.fillMaxWidth(0.92f).testTag("plan-${row.id}")
        .background(tint.theirs, RoundedCornerShape(20.dp)).padding(14.dp)
        .semantics { contentDescription = planLabel },
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (plan.isFinished) PlanCircle(TodoStatus.DONE, tint.ink, Modifier.size(19.dp))
            else ToolGlyph(ToolCategory.PLAN, tint.ink, Modifier.size(19.dp))
            Text(localizedMobileCopy(if (plan.isFinished) "All done" else "Plan"),
                style = MaterialTheme.typography.titleSmall, color = if (plan.isFinished) tint.ink else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Text(stringResource(R.string.mobile_chat_plan_progress, plan.done, plan.total),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = secondaryTint)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp),
            color = tint.ink, trackColor = tint.inset, drawStopIndicator = {})
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (index in visible) {
                val item = plan.items[index]
                val label = localizedMobileCopy(when (item.status) {
                    TodoStatus.DONE -> "Done"; TodoStatus.ACTIVE -> "In progress"
                    TodoStatus.PENDING -> "To do"; TodoStatus.CANCELLED -> "Cancelled"
                })
                val text = if (item.status == TodoStatus.ACTIVE) item.activeText ?: item.text else item.text
                Row(Modifier.testTag("plan-${row.id}-item-$index").semantics(mergeDescendants = true) {
                    contentDescription = "$label, $text"
                }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    PlanCircle(item.status, tint.ink, Modifier.padding(top = 2.dp).size(20.dp))
                    PlanItemText(text, item.status, motion)
                }
            }
        }
        if (plan.items.size > 6) TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) localizedMobileCopy("Show less") else stringResource(R.string.mobile_chat_plan_show_all, plan.items.size), color = tint.ink)
        }
        if (plan.truncated) Text(localizedMobileCopy("More items not shown"), style = MaterialTheme.typography.labelSmall, color = secondaryTint)
    }
}

@Composable
private fun PlanItemText(text: String, status: TodoStatus, moving: Boolean) {
    val struck = status == TodoStatus.DONE || status == TodoStatus.CANCELLED
    val alpha by animateFloatAsState(if (struck) 1f else 0f, if (moving) tween(220) else snap())
    val muted = secondaryTint
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(text, style = MaterialTheme.typography.bodyLarge,
        color = if (struck) muted else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (status == TodoStatus.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            drawContent()
            if (alpha > 0f) layout?.let { result ->
                for (line in 0 until result.lineCount) {
                    val y = result.getLineBaseline(line) - result.size.height.toFloat() / result.lineCount * 0.25f
                    drawLine(muted.copy(alpha = muted.alpha * alpha), Offset(result.getLineLeft(line), y), Offset(result.getLineRight(line), y), 1.dp.toPx())
                }
            }
        })
}

@Composable
internal fun PlanCircle(status: TodoStatus, ink: Color, modifier: Modifier = Modifier) {
    val moving = motionEnabled()
    var shown by remember { mutableStateOf(true) }
    val angle = if (moving && shown && status == TodoStatus.ACTIVE) {
        rememberInfiniteTransition().animateFloat(
            0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)),
        )
    } else null
    val done = status == TodoStatus.DONE
    val check by animateFloatAsState(if (done) 1f else 0f, if (moving) tween(280) else snap())
    val fill by animateFloatAsState(if (done) 1f else 0.6f, if (moving) spring(dampingRatio = 0.82f, stiffness = 380f) else snap())
    val foreground = remember(ink) { if (1.05f / (ink.luminance() + 0.05f) >= 3f) Color.White else Color.Black }
    Canvas(modifier.onGloballyPositioned { shown = !it.boundsInWindow().isEmpty }) {
        val stroke = 1.5.dp.toPx()
        when (status) {
            TodoStatus.DONE -> {
                drawCircle(ink, radius = size.minDimension * 0.5f * fill)
                val first = (check / 0.4f).coerceIn(0f, 1f)
                val second = ((check - 0.4f) / 0.6f).coerceIn(0f, 1f)
                drawLine(foreground, Offset(size.width * 0.26f, size.height * 0.51f),
                    Offset(size.width * (0.26f + 0.18f * first), size.height * (0.51f + 0.18f * first)), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                if (second > 0f) drawLine(foreground, Offset(size.width * 0.44f, size.height * 0.69f),
                    Offset(size.width * (0.44f + 0.31f * second), size.height * (0.69f - 0.39f * second)), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            TodoStatus.ACTIVE -> { drawCircle(ink.copy(alpha = 0.25f), style = Stroke(stroke))
                drawArc(ink, angle?.value ?: -90f, 90f, false, style = Stroke(stroke)) }
            TodoStatus.PENDING -> drawCircle(ink.copy(alpha = 0.35f), style = Stroke(stroke))
            TodoStatus.CANCELLED -> {
                drawLine(ink.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.3f), androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height * 0.7f), stroke)
                drawLine(ink.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height * 0.3f), androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.7f), stroke)
            }
        }
    }
}

@Composable
internal fun LivePlanStrip(row: TranscriptRow.Plan, status: String?, onOpen: () -> Unit) {
    val plan = row.plan
    val tint = chatTint
    Column(Modifier.fillMaxWidth().testTag("live-plan-strip").background(tint.theirs, RoundedCornerShape(20.dp))
        .clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(progress = { if (plan.total == 0) 0f else plan.done.toFloat() / plan.total },
                modifier = Modifier.size(18.dp), color = tint.ink, trackColor = tint.inset, strokeWidth = 2.dp)
            Text(stringResource(R.string.mobile_chat_plan_progress, plan.done, plan.total), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
            Text("·", color = secondaryTint)
            Text(plan.active?.let { it.activeText ?: it.text } ?: plan.items.firstOrNull { it.status == TodoStatus.PENDING }?.text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium, color = secondaryTint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowUp, null, tint = tint.ink, modifier = Modifier.size(18.dp))
        }
        status?.trim()?.takeIf(String::isNotEmpty)?.let { LiveStatusLine(it) }
    }
}
