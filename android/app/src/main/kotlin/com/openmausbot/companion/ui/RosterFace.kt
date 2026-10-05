package com.openmausbot.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import kotlinx.coroutines.flow.collectLatest

internal enum class RosterFaceBadge { WAITING, WORKING, QUEUED, UNREAD }

/** Identity leads; runtime belongs to its corner, not a second column of machinery. */
@Composable
internal fun RosterFace(
    size: Dp,
    color: String,
    modifier: Modifier = Modifier,
    working: Boolean = false,
    badge: RosterFaceBadge? = null,
    workingLabel: String = stringResource(R.string.mobile_thread_working),
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = remember(color) { Color(MausPalette.argb(color)) }
    Box(modifier = modifier.size(size)) {
        content()
        if (working) {
            RosterWorkingIndicator(
                size = size + 6.dp,
                color = accent,
                label = workingLabel,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (badge != null) {
            val label = when (badge) {
                RosterFaceBadge.WAITING -> stringResource(R.string.mobile_waiting_on_you_edab5b72)
                RosterFaceBadge.WORKING -> workingLabel
                RosterFaceBadge.QUEUED -> stringResource(R.string.mobile_queued_6a599877)
                RosterFaceBadge.UNREAD -> stringResource(R.string.mobile_unread_07b032b5)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .testTag("roster-face-badge.${badge.name.lowercase()}")
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .size(16.dp)
                    .background(
                        if (badge == RosterFaceBadge.WAITING) rosterNeedsYouContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    )
                    .clearAndSetSemantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                when (badge) {
                    RosterFaceBadge.WAITING -> Icon(
                        painterResource(R.drawable.ic_pan_tool), null,
                        tint = rosterNeedsYouInk, modifier = Modifier.size(11.dp),
                    )
                    RosterFaceBadge.QUEUED -> Icon(
                        painterResource(R.drawable.ic_schedule), null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp),
                    )
                    RosterFaceBadge.UNREAD -> Icon(
                        Icons.Filled.Notifications, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp),
                    )
                    RosterFaceBadge.WORKING -> RosterWorkingIndicator(11.dp, accent, label)
                }
            }
        }
    }
}

/** Only visible work asks for frames; removing animations leaves a complete static ring. */
@Composable
internal fun RosterWorkingIndicator(size: Dp, color: Color, label: String, modifier: Modifier = Modifier) {
    var rotation by remember { mutableFloatStateOf(0f) }
    var moving by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val durationScale = coroutineContext[MotionDurationScale]
        snapshotFlow { shown && (durationScale?.scaleFactor ?: 1f) > 0f }
            .collectLatest { live ->
                moving = live
                if (!live) return@collectLatest
                var start = 0L
                while (true) withFrameNanos { now ->
                    if (start == 0L) start = now
                    rotation = ((now - start) % 2_400_000_000L) / 2_400_000_000f * 360f
                }
            }
    }
    Canvas(
        modifier = modifier.requiredSize(size)
            .testTag("roster-working-indicator")
            .onGloballyPositioned { shown = !it.boundsInWindow().isEmpty }
            .clearAndSetSemantics { contentDescription = label },
    ) {
        val stroke = 1.75.dp.toPx()
        val radius = this.size.minDimension / 2f - stroke / 2f
        if (moving) {
            drawCircle(color.copy(alpha = 0.18f), radius, style = Stroke(stroke))
            drawArc(
                color = color, startAngle = rotation - 90f, sweepAngle = 105f, useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        } else {
            drawCircle(color, radius, style = Stroke(stroke))
        }
    }
}

/** Material has no warning role; this orange tonal pair stays legible on every skin. */
internal val rosterNeedsYouContainer: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF503015) else Color(0xFFFFE0BC)

internal val rosterNeedsYouInk: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFFFDDB5) else Color(0xFF75420A)

internal val rosterUnreadBlue = Color(0xFF2E6FDB)
