package com.openmausbot.companion.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Reasoning
import kotlinx.coroutines.delay

/** Reasoning whispers outside the answer, and remains reversible until its stream ends. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ThinkingView(reasoning: String, answering: Boolean = false) {
    val tint = chatTint
    var expanded by remember { mutableStateOf(false) }
    val start = remember { android.os.SystemClock.elapsedRealtime() }
    var seconds by remember { mutableLongStateOf(0L) }
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(answering) {
        // Capture elapsed time even when the clipped row's timer was paused;
        // later scrolling must not change the finished duration or disclosure.
        if (answering) {
            expanded = false
            seconds = (android.os.SystemClock.elapsedRealtime() - start) / 1000
        }
    }
    LaunchedEffect(answering, shown) {
        while (!answering && shown) { seconds = (android.os.SystemClock.elapsedRealtime() - start) / 1000; delay(1000) }
    }
    val moving = motionEnabled()
    val pulse = if (moving && shown && !answering) rememberInfiniteTransition(label = "Thinking") else null
    val alpha = pulse?.animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "Thinking dot")
    val shimmer = pulse?.animateFloat(-1f, 2f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "Thinking shimmer")
    val retained = remember(reasoning) { Reasoning.steps(reasoning).joinToString("\n\n") }
    val scroll = rememberScrollState()
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            withFrameNanos { }
            bringIntoView.bringIntoView()
        }
    }
    LaunchedEffect(scroll, expanded) {
        if (expanded) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
    }
    Column(Modifier.fillMaxWidth(0.8f).onGloballyPositioned { shown = !it.boundsInWindow().isEmpty }
        .animateContentSize(spring(dampingRatio = 0.82f, stiffness = 380f)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("thinking-row")
            .clickable(role = Role.Button) { expanded = !expanded }.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!answering) androidx.compose.foundation.Canvas(Modifier.size(8.dp)) {
                drawCircle(tint.ink.copy(alpha = alpha?.value ?: 1f))
            }
            Text(if (answering) stringResource(R.string.mobile_chat_thought_for, seconds.toInt()) else localizedMobileCopy("Thinking"),
                style = if (answering) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (answering) secondaryTint else if (shimmer != null) Color.White else tint.ink,
                modifier = Modifier.weight(1f).then(if (shimmer != null) Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val phase = shimmer.value
                        drawRect(Brush.linearGradient(listOf(tint.ink.copy(alpha = 0.65f), tint.ink, tint.ink.copy(alpha = 0.65f)),
                            start = androidx.compose.ui.geometry.Offset(phase * 130f, 0f),
                            end = androidx.compose.ui.geometry.Offset((phase + 1f) * 130f, 0f)), blendMode = BlendMode.SrcIn)
                    } else Modifier))
            if (!answering) Text("${seconds}s", style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = secondaryTint)
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = secondaryTint, modifier = Modifier.size(16.dp))
        }
        if (!answering && !expanded) AnimatedContent(targetState = retained.takeLast(400), label = "Latest thought") { text ->
            val previewHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                (MaterialTheme.typography.bodySmall.lineHeight.toPx() * 2).roundToInt()
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = secondaryTint,
                modifier = Modifier.graphicsLayer { clip = true; compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.22f to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
                }.layout { measurable, constraints ->
                    val content = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                    val height = minOf(content.height, previewHeight)
                    layout(content.width, height) { content.placeRelative(0, height - content.height) }
                })
        }
        AnimatedVisibility(expanded) {
            Text(retained, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("thinking-panel").bringIntoViewRequester(bringIntoView).fillMaxWidth().heightIn(max = 280.dp)
                    .background(tint.theirs, RoundedCornerShape(20.dp)).verticalScroll(scroll).padding(14.dp))
        }
    }
}
