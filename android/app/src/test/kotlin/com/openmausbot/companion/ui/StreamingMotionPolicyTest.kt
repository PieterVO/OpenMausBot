package com.openmausbot.companion.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Message
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class StreamingMotionPolicyTest {
    private val scale = mutableFloatStateOf(1f)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = object : MotionDurationScale {
        override val scaleFactor get() = scale.floatValue
    })
    private val reply = Message("reply", Message.Role.BOT, Message.Kind.TEXT, 1000.0,
        text = "A calm reply with **bold words** and a little breathing room.")

    @Test fun zeroAnimatorScaleShowsTheEntireStreamAndStaticCaret() {
        scale.floatValue = 0f
        compose.mainClock.autoAdvance = false
        val source = mutableStateOf(reply.text!! + "\uD83D")
        compose.setContent { MaterialTheme { StreamingBubble(source.value, null) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("streaming-bubble").assert(SemanticsMatcher.expectValue(StreamingVisibleText, reply.text!!))
            .assert(SemanticsMatcher.expectValue(StreamingRevealing, false))
            .assert(SemanticsMatcher.expectValue(StreamingCaretAlpha, 1f))
        compose.mainClock.advanceTimeBy(560)
        compose.onNodeWithTag("streaming-bubble").assert(SemanticsMatcher.expectValue(StreamingCaretAlpha, 1f))
        val complete = reply.text!! + "👨‍👩‍👧‍👦"
        compose.runOnIdle {
            source.value = complete
            // Direct snapshot writes need publication before the frozen next frame.
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag("streaming-bubble").assert(SemanticsMatcher.expectValue(StreamingVisibleText, complete))
    }

    @Test fun disablingAnimationsFinishesAnInFlightRevealImmediately() {
        val scene = WiringScene()
        compose.mainClock.autoAdvance = false
        compose.setContent { CompositionLocalProvider(LocalCompanion provides scene.environment, LocalTextArrival provides true) {
            CompanionTheme { MessageRow(Chat.BotChat(bot()), reply) }
        } }
        compose.mainClock.advanceTimeBy(96)
        val prefix = compose.onNodeWithTag("message-reply").fetchSemanticsNode().config[StreamingVisibleText]
        org.junit.Assert.assertTrue(prefix.isNotEmpty() && prefix.length < reply.text!!.length)
        compose.runOnIdle { scale.floatValue = 0f }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("message-reply").assert(SemanticsMatcher.expectValue(StreamingVisibleText, reply.text!!))
            .assert(SemanticsMatcher.expectValue(StreamingRevealing, false))
    }

    @Test fun historyDoesNotRevealEvenWithAnimationsEnabled() {
        val scene = WiringScene()
        compose.mainClock.autoAdvance = false
        compose.setContent { CompositionLocalProvider(LocalCompanion provides scene.environment) {
            CompanionTheme { MessageRow(Chat.BotChat(bot()), reply) }
        } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("message-reply").assert(SemanticsMatcher.expectValue(StreamingVisibleText, reply.text!!))
            .assert(SemanticsMatcher.expectValue(StreamingRevealing, false))
    }

    @Test fun touchExplorationSkipsTheLiveArrivalRevealAndBubbleFade() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setTouchExplorationEnabled(true)
        try {
            val scene = WiringScene()
            compose.mainClock.autoAdvance = false
            compose.setContent { CompositionLocalProvider(LocalCompanion provides scene.environment, LocalTextArrival provides true) {
                CompanionTheme { MessageRow(Chat.BotChat(bot()), reply) }
            } }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("message-reply").assert(SemanticsMatcher.expectValue(StreamingVisibleText, reply.text!!))
                .assert(SemanticsMatcher.expectValue(StreamingRevealing, false))
            val bubble = compose.onAllNodes(SemanticsMatcher.keyIsDefined(BubbleMotionAlpha), useUnmergedTree = true).fetchSemanticsNodes().single()
            assertEquals(1f, bubble.config[BubbleMotionAlpha], 0f)
        } finally {
            shadowOf(manager).setTouchExplorationEnabled(false)
        }
    }
}
