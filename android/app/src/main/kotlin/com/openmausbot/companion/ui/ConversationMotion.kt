package com.openmausbot.companion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.currentCoroutineContext

/** Track the old tail, not the list size: paging at the head never animates arrivals. */
internal class TranscriptArrivals {
    private var initialized = false
    private var previous = emptySet<String>()
    private val appended = mutableSetOf<String>()
    private var tail: String? = null
    private var tailChanged = false
    private var liveTextWasVisible = false
    /** Called after composition: the next transcript update replaces this frame. */
    fun recordLiveTextVisible(visible: Boolean) { liveTextWasVisible = visible }
    fun update(ids: List<String>, botTextIds: Set<String> = emptySet()): Set<String> {
        val newTail = ids.lastOrNull()
        tailChanged = initialized && newTail != tail
        if (initialized) {
            if (previous.isEmpty()) appended.addAll(ids.filterNot { liveTextWasVisible && it in botTextIds })
            else {
                val oldTail = tail?.let { ids.indexOf(it) } ?: -1
                if (oldTail >= 0) for (index in oldTail + 1 until ids.size) {
                    val id = ids[index]
                    if (id !in previous && !(liveTextWasVisible && id in botTextIds)) appended.add(id)
                }
            }
        }
        initialized = true
        tail = newTail
        val current = ids.toHashSet()
        previous = current
        appended.retainAll(current)
        return appended.toSet()
    }
    fun consume(id: String): Boolean = appended.remove(id)
    fun consumeTailChange(): Boolean = tailChanged.also { tailChanged = false }
}

@Composable
internal fun motionEnabled(): Boolean {
    var enabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val scale = currentCoroutineContext()[MotionDurationScale]
        snapshotFlow { (scale?.scaleFactor ?: 1f) > 0f }.collect { enabled = it }
    }
    return enabled
}

@Composable
internal fun ArrivalRow(id: String, appended: Boolean, mine: Boolean, revealText: Boolean = false, content: @Composable () -> Unit) {
    val progress = remember(id) { Animatable(if (appended && !revealText) 0f else 1f) }
    val offset = with(LocalDensity.current) { (if (mine) 18.dp else 10.dp).toPx() }
    val anchor = if (physicalBubbleTail(if (mine) BubbleTail.TRAILING else BubbleTail.LEADING, LocalLayoutDirection.current) == BubbleTail.TRAILING) 1f else 0f
    LaunchedEffect(id) {
        if (appended && !revealText && (currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) > 0f)
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 380f))
        else progress.snapTo(1f)
    }
    Box(Modifier.semantics { this[MessageMotionAlpha] = progress.value }.graphicsLayer {
        val value = progress.value
        alpha = value
        translationY = offset * (1f - value)
        scaleX = 0.97f + 0.03f * value
        scaleY = scaleX
        transformOrigin = TransformOrigin(anchor, 1f)
    }) { content() }
}

@Composable
internal fun StepReveal(index: Int, content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if ((currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) > 0f) {
            delay(minOf(index, 7) * 30L)
            alpha.animateTo(1f, tween(200))
        } else alpha.snapTo(1f)
    }
    Box(Modifier.graphicsLayer { this.alpha = alpha.value }) { content() }
}
