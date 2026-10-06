package com.openmausbot.companion.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Frame
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.RuntimeEvent
import com.openmausbot.companion.core.StreamFrame
import com.openmausbot.companion.core.target
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real ChatScreen frames, driven by a real Session and isolated loopback responses.
 * Set COMPANION_MOTION_CAPTURE_DIR to android/.impeccable/review/motion (absolute)
 * to write stream/frame-%03d.png and hidden-reveal/frame-%03d.png. Ordinary runs
 * perform the same assertions without allocating or writing screenshot bitmaps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class StreamingMotionScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale { override val scaleFactor = 1f },
    )

    private lateinit var server: MockWebServer
    private lateinit var scene: WiringScene
    private lateinit var fixture: Bot
    private val updates = MutableSharedFlow<StreamFrame>(extraBufferCapacity = 32)
    private var sequence = 1
    private val captureRoot = System.getenv("COMPANION_MOTION_CAPTURE_DIR")
        ?.takeIf { it.isNotBlank() }?.let(::File)
    private var captureDirectory: File? = null
    private var captureIndex = 0

    // The paragraph wraps enough times to exercise frame-by-frame list following.
    private val reply = """
        Here is the **bold** version, saved in `notes.md` for your next quiet afternoon.

        Take the coastal path past the station, pause at the small cafe, and leave enough time to enjoy the view before the afternoon train. The route stays close to the water without turning the day into a checklist. Your notes include the meeting point, the return platform, and a shorter walk if the weather changes. Keep a little breathing room between each stop so there is no need to hurry. Everything is saved together, ready to pick up when you leave home.
    """.trimIndent()
    private val streamedReply = reply.replace("\n\nTake",
        "\n\n| Place | Walk |\n| --- | --- |\n| Linden | 6 min |\n| Ferry | 11 min |\n\nTake")

    @Before
    fun start() {
        // Working dots, caret, avatar, and status spinner never become idle on
        // their own. Only explicit frame advances may move this test's clock.
        compose.mainClock.autoAdvance = false
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(if (request.path == "/api/instances") "{\"instances\":[]}"
                    else "{\"messages\":[],\"hasMore\":false}")
        }
        server.start()
    }

    @After
    fun stop() {
        if (::scene.isInitialized) compose.runOnIdle { scene.session.disconnect() }
        server.shutdown()
    }

    @Test
    fun fullStreamingUnevenDeltasThenSettleWithoutAnArrivalDip() {
        val reply = streamedReply
        mount(ActivityDetail.FULL)
        beginCapture("stream")
        val caretAlphas = mutableListOf<Float>()
        var sawRenderedFade = false
        var received = ""

        fun receive(end: Int) {
            publish(Frame.Runtime(RuntimeEvent("content.delta", fixture.threadId,
                reply.substring(received.length, end), "assistant_text")))
            received = reply.substring(0, end)
            renderFrame()
        }

        fun observe(frames: Int = SAMPLE_FRAMES): Sample {
            val before = sample(STREAM_TAG)
            advanceAndCapture(frames)
            val after = sample(STREAM_TAG)
            assertPaced(before, after, received)
            assertFollowingLatest(STREAM_TAG)
            caretAlphas += after.caretAlpha
            sawRenderedFade = assertRenderedTailIfPresent(STREAM_TAG) || sawRenderedFade
            return after
        }

        receive(80)
        advanceAndCapture(2)
        val first = sample(STREAM_TAG)
        assertTrue("A network batch must initially be a prefix, not appear at once",
            first.visible.isNotEmpty() && first.visible.length < received.length)
        assertTrue(first.revealing)
        val initialHeight = node(STREAM_TAG).size.height
        repeat(4) { observe() }

        // A tiny packet after a larger one must preserve the same cursor.
        val beforeTinyPacket = sample(STREAM_TAG).visible
        receive(87)
        assertTrue(sample(STREAM_TAG).visible.startsWith(beforeTinyPacket))
        repeat(4) { observe() }
        val caughtUpDeadline = compose.mainClock.currentTime + 1_500L
        while (sample(STREAM_TAG).revealing && compose.mainClock.currentTime < caughtUpDeadline) observe()
        assertEquals(received, sample(STREAM_TAG).visible)
        assertFalse(sample(STREAM_TAG).revealing)
        assertTrue("Caught-up ink must lose its temporary tail fade", tailAlphas(STREAM_TAG).isEmpty())

        // A caught-up incomplete header/delimiter still stays out of the
        // rendered text. The newline admits header and delimiter together.
        val delimiterEnd = reply.indexOf('\n', reply.indexOf("| ---"))
        receive(delimiterEnd)
        while (sample(STREAM_TAG).revealing) observe()
        compose.onNodeWithTag("data-table").assertDoesNotExist()
        assertTrue(layouts(STREAM_TAG).none { it.layoutInput.text.text.contains('|') })
        receive(delimiterEnd + 1)
        repeat(2) { observe() }
        compose.onNodeWithTag("data-table").assertIsDisplayed()

        val lindenEnd = reply.indexOf('\n', reply.indexOf("| Linden"))
        receive(lindenEnd)
        while (sample(STREAM_TAG).revealing) observe()
        compose.onNodeWithText("Linden", useUnmergedTree = true).assertDoesNotExist()
        receive(lindenEnd + 1)
        repeat(2) { observe() }
        compose.onNodeWithText("Linden", useUnmergedTree = true).assertIsDisplayed()

        val ferryEnd = reply.indexOf('\n', reply.indexOf("| Ferry"))
        receive(ferryEnd)
        while (sample(STREAM_TAG).revealing) observe()
        compose.onNodeWithText("Ferry", useUnmergedTree = true).assertDoesNotExist()
        receive(ferryEnd + 1)
        repeat(2) { observe() }
        compose.onNodeWithText("Ferry", useUnmergedTree = true).assertIsDisplayed()

        // Separate uneven bursts introduce the long paragraph, then grow it
        // while following without any further server update between frames.
        receive(330)
        repeat(5) { observe() }
        receive(reply.length - 55)
        repeat(5) { observe() }
        assertTrue("Paced wrapping must grow the actual bubble height",
            node(STREAM_TAG).size.height > initialHeight + with(compose.density) { 32.dp.roundToPx() })
        assertTrue("The caret must breathe while the real stream is open",
            caretAlphas.maxOrNull()!! - caretAlphas.minOrNull()!! > 0.2f)
        assertTrue("At least one rendered Markdown tail must fade while catching up", sawRenderedFade)

        // A genuine gesture releases following. New runtime frames and paced
        // layout growth must not move the reader's existing history anchor.
        compose.onNodeWithTag("chat-transcript").performTouchInput {
            down(Offset(center.x, height * 0.25f))
            moveTo(Offset(center.x, height * 0.80f), delayMillis = 400L)
            advanceEventTime(200L)
            up()
        }
        repeat(8) { advanceAndCapture() }
        compose.onNodeWithTag("jump-to-latest").assertIsDisplayed()
        val anchor = historyAnchor()
        val frozenOffset = scrollOffset()
        receive(reply.length)
        repeat(5) {
            advanceAndCapture()
            assertEquals("A new delta must not yank the transcript", frozenOffset, scrollOffset(), 0.001f)
            assertEquals("The history row must stay at the reader's position",
                anchor.top, node(anchor.tag).positionInRoot.y, 1f)
        }
        compose.onNodeWithTag("jump-to-latest").assertIsDisplayed()
        compose.onNodeWithTag("jump-to-latest").performClick()
        repeat(16) { advanceAndCapture() }
        assertFollowingLatest(STREAM_TAG)

        // Add one last unfinished batch so settlement is also tested with a
        // live backlog, not only after the pacer has already caught up.
        val finalText = "$reply\n\nYour afternoon is ready."
        publish(Frame.Runtime(RuntimeEvent("content.delta", fixture.threadId,
            finalText.substring(reply.length), "assistant_text")))
        renderFrame()
        assertTrue(sample(STREAM_TAG).visible.length < finalText.length)
        settle(finalText)
        renderFrame()
        assertSettledImmediately(finalText)
        capture()
        repeat(8) {
            advanceAndCapture()
            assertSettledImmediately(finalText)
        }
        assertMarkdownIsRendered(REPLY_TAG)
    }

    @Test
    fun hiddenWorkingStatusThenFinalReplyRevealsWithinAFrameOfNineTenthsOfASecond() {
        mount(ActivityDetail.HIDDEN)
        beginCapture("hidden-reveal")
        compose.onNodeWithContentDescription("Scout is typing").assertIsDisplayed()
        repeat(4) { advanceAndCapture() }

        val statusSource = "Checking the **bold** route in `notes.md`.\n\nLeaving time for the coast."
        publish(Frame.Runtime(RuntimeEvent("content.delta", fixture.threadId, statusSource, "assistant_text")))
        renderFrame()
        repeat(5) { advanceAndCapture() }
        compose.onNodeWithContentDescription("Scout is typing").assertIsDisplayed()
        compose.onNodeWithTag(STREAM_TAG).assertDoesNotExist()
        val status = compose.onNodeWithTag("live-status-line").fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString(" ") { it.text }
        assertEquals("Checking the bold route in notes.md. Leaving time for the coast.", status)
        assertFalse(status.contains('*') || status.contains('`') || status.contains('\n'))

        // Both frames travel through the Session before the next composition:
        // there was no transcript bubble, so this is an arrival reveal, not
        // the full-detail streaming handover exercised by the other test.
        settle(reply)
        renderFrame()
        val revealStart = compose.mainClock.currentTime
        val initial = sample(REPLY_TAG)
        assertTrue("Hidden final text must start as an intermediate prefix", initial.visible.length < reply.length)
        advanceAndCapture(2)
        val first = sample(REPLY_TAG)
        assertTrue(first.visible.isNotEmpty() && first.visible.length < reply.length)
        assertTrue(first.revealing)
        assertTrue("Hidden arrival must use the real bubble's short fade", bubbleProperty(REPLY_TAG, BubbleMotionAlpha) < 1f)
        var sawRenderedFade = assertRenderedTailIfPresent(REPLY_TAG)
        var previous = first
        // The specified length/.9 rate reaches its endpoint on the next display
        // frame when 900 ms falls between ticks; never allow a second extra frame.
        val deadline = 900L + FRAME_MILLIS
        while (previous.revealing && compose.mainClock.currentTime + FRAME_MILLIS - revealStart <= deadline) {
            val remaining = deadline - (compose.mainClock.currentTime - revealStart)
            val frames = minOf(SAMPLE_FRAMES, (remaining / FRAME_MILLIS).toInt())
            advanceAndCapture(frames)
            val next = sample(REPLY_TAG)
            assertPaced(previous, next, reply, arrival = true)
            sawRenderedFade = assertRenderedTailIfPresent(REPLY_TAG) || sawRenderedFade
            previous = next
        }
        assertTrue("The complete hidden arrival must finish within one frame of .9s",
            compose.mainClock.currentTime - revealStart <= deadline)
        assertEquals(reply, previous.visible)
        assertFalse(previous.revealing)
        assertTrue("Hidden arrival also renders the fading ink tail", sawRenderedFade)
        assertTrue(tailAlphas(REPLY_TAG).isEmpty())
        assertEquals(1f, motionAlpha(REPLY_TAG), 0.001f)
        compose.onNodeWithTag("live-status-line").assertDoesNotExist()
        compose.onNodeWithContentDescription("Scout is typing").assertDoesNotExist()
        repeat(4) {
            advanceAndCapture()
            assertEquals(reply, sample(REPLY_TAG).visible)
            assertFalse(sample(REPLY_TAG).revealing)
        }
        assertMarkdownIsRendered(REPLY_TAG)
    }

    private fun mount(detail: ActivityDetail) {
        val history = (0 until 14).map { index ->
            Message("history-$index", if (index % 2 == 0) Message.Role.BOT else Message.Role.USER,
                Message.Kind.TEXT, 1_791_193_200_000.0 + index * 1_000,
                text = if (index == 13) "Could you save a quiet afternoon by the coast?"
                    else "Earlier note $index: keep the meeting point, return platform, and walking route together so we can pick up this conversation later.",
                parentId = if (index == 0) null else "history-${index - 1}")
        }
        fixture = bot(name = "Scout", busy = true).copy(messages = history, activeLeafId = history.last().id)
        scene = WiringScene(Connection(id = "streaming-motion", name = "Isolated motion fixture",
            host = "127.0.0.1", port = server.port), fleet = Fleet(listOf(fixture), emptyList())) {
            flow {
                emit(StreamFrame(Frame.Hello(cursor = "motion:1", resumed = false), seq = 1))
                emitAll(updates)
            }
        }
        scene.environment.chatPreferences.setAppearanceSkin(AppearanceSkin.LINEN)
        scene.environment.chatPreferences.setActivityDetail(detail)
        scene.environment.chatPreferences.setShowWorkSummaries(false)
        scene.environment.chatPreferences.setQuickReplies(emptyList())
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides scene.environment) {
                CompanionTheme(skin = AppearanceSkin.LINEN) {
                    val state by scene.session.state.collectAsState()
                    Surface(Modifier.fillMaxSize()) {
                        if (state.bot(fixture.id) != null) ChatScreen(
                            Destination.Chat(Chat.BotChat(fixture).target), {}, {}, {}, {},
                        )
                    }
                }
            }
        }
        compose.runOnIdle { scene.session.connect() }
        compose.waitUntil(5_000) { scene.session.state.value.bot(fixture.id) != null && updates.subscriptionCount.value == 1 }
        repeat(4) { renderFrame() }
        compose.onNodeWithTag("chat-transcript").assertIsDisplayed()
    }

    private fun publish(vararg frames: Frame) {
        compose.runOnIdle {
            frames.forEach { frame ->
                assertTrue("The isolated runtime stream must accept every frame",
                    updates.tryEmit(StreamFrame(frame, seq = ++sequence)))
            }
        }
        // Waiting for network/state work must never advance animation time.
        compose.waitForStream { scene.session.state.value.cursor == "motion:$sequence" }
    }

    private fun settle(text: String) {
        publish(
            Frame.Message(fixture.threadId, Message("reply", Message.Role.BOT, Message.Kind.TEXT,
                1_791_193_230_000.0, text = text, parentId = "history-13", turnId = "coast", turnTerminal = true)),
            Frame.Runtime(RuntimeEvent("turn.completed", fixture.threadId)),
            Frame.Bot(fixture.copy(busy = false, messages = null, activeLeafId = "reply")),
        )
    }

    private fun renderFrame() {
        compose.mainClock.advanceTimeByFrame()
        // With autoAdvance=false this flushes Android measure/layout/draw, not
        // the perpetual animation loops or the reveal's remaining duration.
        compose.waitForIdle()
    }

    private fun advanceAndCapture(frames: Int = SAMPLE_FRAMES) {
        repeat(frames) { renderFrame() }
        capture()
    }

    private fun beginCapture(name: String) {
        captureDirectory = captureRoot?.let { File(it, name).also { directory -> directory.mkdirs() } }
        captureIndex = 0
        capture()
    }

    private fun capture() {
        val directory = captureDirectory ?: return
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            assertTrue("Native evidence needs a laid-out activity", view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val file = File(directory, String.format(Locale.ROOT, "frame-%03d.png", captureIndex++))
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    private fun descendants(node: SemanticsNode): Sequence<SemanticsNode> = sequence {
        yield(node)
        node.children.forEach { yieldAll(descendants(it)) }
    }

    private fun <T> bubbleProperty(tag: String, key: SemanticsPropertyKey<T>): T =
        descendants(node(tag)).mapNotNull { it.config.getOrNull(key) }.first()

    private data class Sample(val at: Long, val visible: String, val revealing: Boolean, val caretAlpha: Float)
    private fun sample(tag: String) = Sample(compose.mainClock.currentTime,
        bubbleProperty(tag, StreamingVisibleText), bubbleProperty(tag, StreamingRevealing),
        bubbleProperty(tag, StreamingCaretAlpha))

    private fun assertPaced(before: Sample, after: Sample, source: String, arrival: Boolean = false) {
        assertTrue("The visible text must remain a source prefix", source.startsWith(after.visible))
        assertTrue("A reveal must never move its cursor backwards", after.visible.startsWith(before.visible))
        assertEquals(after.visible.length < source.length, after.revealing)
        val seconds = (after.at - before.at) / 1_000.0
        val backlog = source.length - before.visible.length
        val rate = if (arrival) max(90.0, source.length / 0.9) else max(60.0, backlog / 0.35)
        val advanced = after.visible.length - before.visible.length
        assertTrue("A batch must be paced rather than dumped into one frame",
            advanced <= ceil(rate * seconds).toInt() + 2)
        if (backlog > 0) assertTrue("The fractional cursor must keep progressing",
            advanced >= minOf(backlog, floor((if (arrival) 90.0 else 60.0) * seconds).toInt()) - 1)
        assertTrue(after.caretAlpha in 0f..1f)
    }

    private fun motionAlpha(tag: String): Float {
        var current: SemanticsNode? = node(tag)
        while (current != null) {
            current.config.getOrNull(MessageMotionAlpha)?.let { return it }
            current = current.parent
        }
        error("The real message must expose its ArrivalRow's MessageMotionAlpha")
    }

    private fun assertSettledImmediately(text: String) {
        compose.onNodeWithTag(STREAM_TAG).assertDoesNotExist()
        compose.onNodeWithTag(REPLY_TAG).assertIsDisplayed()
        assertEquals("Full-detail settlement must reveal all text on its first rendered frame", text, sample(REPLY_TAG).visible)
        assertFalse("Full-detail settlement must not restart the arrival pacer", sample(REPLY_TAG).revealing)
        assertEquals("A streaming handover must never dip the ArrivalRow alpha", 1f, motionAlpha(REPLY_TAG), 0.001f)
        assertEquals("A streaming handover must never dip the bubble alpha", 1f, bubbleProperty(REPLY_TAG, BubbleMotionAlpha), 0.001f)
    }

    private fun scrollOffset(): Float = node("chat-transcript").config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun assertFollowingLatest(tag: String) {
        val transcript = node("chat-transcript")
        val range = transcript.config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("Following must stay at the end as the paced prefix wraps", range.maxValue(), range.value(), 0.01f)
        val bubble = node(tag)
        val bottom = bubble.positionInRoot.y + bubble.size.height
        val viewportBottom = transcript.positionInRoot.y + transcript.size.height
        assertTrue("The live bubble's actual bottom must remain inside the viewport", bottom <= viewportBottom + 1f)
        assertTrue("The live bubble must stay pinned above the transcript's bottom padding",
            bottom >= viewportBottom - with(compose.density) { 28.dp.toPx() })
        compose.onNodeWithTag("jump-to-latest").assertDoesNotExist()
    }

    private data class HistoryAnchor(val tag: String, val top: Float)
    private fun historyAnchor(): HistoryAnchor {
        val viewport = node("chat-transcript").boundsInRoot
        val matcher = SemanticsMatcher("A visible history message") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("message-history-") == true
        }
        val anchor = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
            .first { it.positionInRoot.y > viewport.top + with(compose.density) { 148.dp.toPx() } && it.boundsInRoot.bottom < viewport.bottom }
        return HistoryAnchor(anchor.config[SemanticsProperties.TestTag], anchor.positionInRoot.y)
    }

    private fun tailAlphas(tag: String): List<Float> = descendants(node(tag))
        .flatMap { it.config.getOrNull(InkTailAlphas).orEmpty().asSequence() }.toList()

    private fun layouts(tag: String): List<TextLayoutResult> = descendants(node(tag)).flatMap { text ->
        val results = mutableListOf<TextLayoutResult>()
        text.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        results.asSequence()
    }.toList()

    private fun assertRenderedTailIfPresent(tag: String): Boolean {
        val alphas = tailAlphas(tag)
        if (alphas.isEmpty()) return false // A blank prefix/paragraph break has no rendered tail yet.
        assertTrue(alphas.all { it in 0.3f..1f })
        assertTrue("Ink fades towards the latest grapheme", alphas.zipWithNext().all { (a, b) -> a >= b })
        assertEquals(0.3f, alphas.last(), 0.01f)
        assertTrue("The fade must also reach the actual annotated Text layout, not only a semantics hook",
            layouts(tag).any { layout ->
                val bodyLength = layout.layoutInput.text.text.removeSuffix("\u2007\u258d").length
                layout.layoutInput.text.spanStyles.any {
                    it.start < bodyLength && it.end <= bodyLength &&
                        it.item.color != Color.Unspecified && it.item.color.alpha < 0.99f
                }
            })
        return true
    }

    private fun assertMarkdownIsRendered(tag: String) {
        val layouts = layouts(tag)
        assertTrue("Bold must be rendered, not leak its Markdown delimiters", layouts.any { layout ->
            layout.layoutInput.text.text.contains("bold") &&
                (layout.layoutInput.style.fontWeight == FontWeight.Bold || layout.layoutInput.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        })
        assertTrue("Inline code must keep its monospace styling", layouts.any { layout ->
            layout.layoutInput.text.text.contains("notes.md") &&
                (layout.layoutInput.style.fontFamily == FontFamily.Monospace || layout.layoutInput.text.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        })
    }

    private companion object {
        const val STREAM_TAG = "streaming-bubble"
        const val REPLY_TAG = "message-reply"
        const val FRAME_MILLIS = 16L
        const val SAMPLE_FRAMES = 3 // 48 ms: one reproducible capture about every 50 ms.
    }
}
