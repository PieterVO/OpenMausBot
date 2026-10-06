package com.openmausbot.companion.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import com.openmausbot.companion.core.StreamingText
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.cos

internal val StreamingVisibleText = SemanticsPropertyKey<String>("StreamingVisibleText")
internal val StreamingRevealing = SemanticsPropertyKey<Boolean>("StreamingRevealing")
internal val StreamingCaretAlpha = SemanticsPropertyKey<Float>("StreamingCaretAlpha")
internal val MessageMotionAlpha = SemanticsPropertyKey<Float>("MessageMotionAlpha")
internal val BubbleMotionAlpha = SemanticsPropertyKey<Float>("BubbleMotionAlpha")
internal val InkTailAlphas = SemanticsPropertyKey<List<Float>>("InkTailAlphas")
internal val LocalTextArrival = compositionLocalOf { false }
internal val LocalPacedMessage = compositionLocalOf<PacedText?> { null }
internal val LocalLiveTextVisibility = compositionLocalOf<(Boolean) -> Unit> { {} }

/** A single cursor survives network batches; only grapheme changes rebuild Markdown. */
@Stable
internal class PacedText(initial: String) {
    var visible by mutableStateOf(initial)
    var revealing by mutableStateOf(false)
    var position = initial.length.toDouble()
    var previousSource = initial
    var previousFrame = 0L
    var startFrame = 0L
    var caretAlpha by mutableFloatStateOf(1f)
    var motion by mutableStateOf(true)
}

@Composable
internal fun rememberPacedText(source: String, streamOpen: Boolean = false, reveal: Boolean = false): PacedText {
    val completeSource = remember(source) {
        if (source.lastOrNull()?.let { Character.isHighSurrogate(it) } == true) source.dropLast(1) else source
    }
    if (!streamOpen && !reveal) return remember(completeSource) { PacedText(completeSource) }
    val paced = remember { PacedText("") }
    val boundaries = remember(source) { StreamingText.graphemeEnds(source) }
    // A duration-scale update cancels the frame loop even while a stream is open.
    LaunchedEffect(source, streamOpen, reveal) {
        val durationScale = coroutineContext[MotionDurationScale]
        snapshotFlow { (durationScale?.scaleFactor ?: 1f) > 0f }.collectLatest { moving ->
            paced.motion = moving
            if (!source.startsWith(paced.previousSource)) {
                paced.position = 0.0
                paced.visible = ""
            }
            paced.previousSource = source
            if (!moving || (!streamOpen && !reveal)) {
                paced.visible = completeSource
                paced.revealing = false
                paced.caretAlpha = 1f
                paced.position = source.length.toDouble()
                return@collectLatest
            }
            while (paced.position < source.length || streamOpen) {
                withFrameNanos { frame ->
                    if (paced.startFrame == 0L) paced.startFrame = frame
                    val seconds = if (paced.previousFrame == 0L) 1.0 / 60.0 else (frame - paced.previousFrame) / 1_000_000_000.0
                    paced.previousFrame = frame
                    paced.position = StreamingText.step(paced.position, source.length, seconds, arrival = reveal && !streamOpen)
                    val danglingSurrogate = source.lastOrNull()?.let { Character.isHighSurrogate(it) } == true
                    val end = if (!streamOpen && paced.position >= source.length && !danglingSurrogate)
                        source.length else StreamingText.prefixEnd(boundaries, paced.position)
                    if (end != paced.visible.length) paced.visible = source.substring(0, end)
                    paced.revealing = end < source.length
                    if (streamOpen) {
                        val cycle = ((frame - paced.startFrame) % 1_100_000_000L) / 1_100_000_000.0
                        paced.caretAlpha = (0.35 + 0.65 * (1.0 + cos(cycle * 2.0 * PI)) * 0.5).toFloat()
                    }
                }
            }
            paced.revealing = false
        }
    }
    // Once Remove animations is known, a new batch is visible in this very
    // composition, not one frame after its effect has copied the source.
    return if (paced.motion) paced else remember(completeSource) {
        PacedText(completeSource).also { it.motion = false }
    }
}

@Composable
internal fun touchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

internal fun SemanticsPropertyReceiver.pacedText(paced: PacedText) {
    this[StreamingVisibleText] = paced.visible
    this[StreamingRevealing] = paced.revealing
    this[StreamingCaretAlpha] = paced.caretAlpha
}
