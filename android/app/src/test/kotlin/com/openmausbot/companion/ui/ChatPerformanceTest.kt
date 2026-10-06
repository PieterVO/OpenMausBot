package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import com.openmausbot.companion.audio.LiveCallApi
import com.openmausbot.companion.audio.LiveCallLink
import com.openmausbot.companion.audio.LiveCallPhase
import com.openmausbot.companion.audio.LiveCallTransport
import com.openmausbot.companion.audio.MicrophoneAccess
import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Frame
import com.openmausbot.companion.core.LiveCallStart
import com.openmausbot.companion.core.LiveCallState
import com.openmausbot.companion.core.LiveCallStatus
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.RuntimeEvent
import com.openmausbot.companion.core.StreamFrame
import com.openmausbot.companion.core.target
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Measures real session frames with an explicit rendered frame after every input. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class ChatPerformanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    )
    private lateinit var server: MockWebServer
    private lateinit var scene: WiringScene
    private val frames = MutableSharedFlow<StreamFrame>(extraBufferCapacity = 256)
    private val counts = linkedMapOf<String, Int>()
    private var sequence = 1
    private val fixture = bot(busy = true).copy(messages = (0 until 6).map { index ->
        Message("settled-$index", if (index % 2 == 0) Message.Role.USER else Message.Role.BOT,
            Message.Kind.TEXT, 1000.0 + index, text = "Message $index")
    })
    private val transport = CaptionTransport()
    private val call = LiveCallState("perf-call", fixture.id, fixture.threadId, "android", "marin", 1000.0, LiveCallStatus.LIVE)

    @Before
    fun mount() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(if (request.path == "/api/instances") "{\"instances\":[]}" else "{\"messages\":[],\"hasMore\":false}")
        }
        server.start()
        val api = object : LiveCallApi {
            override val serverCall = MutableStateFlow<LiveCallState?>(call)
            override val link = MutableStateFlow(LiveCallLink("perf-fixture", true))
            override suspend fun start(botId: String, threadId: String, sdp: String) = LiveCallStart.Started(call, "answer")
            override suspend fun end(callId: String) { serverCall.value = call.copy(status = LiveCallStatus.ENDED) }
        }
        scene = WiringScene(
            connection = Connection("perf-fixture", "Offline performance fixture", "127.0.0.1", server.port),
            fleet = Fleet(listOf(fixture) + (2..9).map { bot(id = "bot-$it", busy = true) }, emptyList()),
            liveTransports = { transport }, liveApi = api, liveClock = { 1000L },
        ) {
            flow {
                emit(StreamFrame(Frame.Hello(cursor = "perf:1", resumed = false), seq = 1))
                emitAll(frames)
            }
        }
        scene.environment.chatPreferences.setActivityDetail(ActivityDetail.FULL)
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides scene.environment,
                LocalRecompositionProbe provides { key -> counts[key] = (counts[key] ?: 0) + 1 }) {
                CompanionTheme(darkTheme = false) {
                    val state by scene.session.state.collectAsState()
                    if (state.bot(fixture.id) != null) ChatScreen(
                        Destination.Chat(Chat.BotChat(fixture).target),
                        onResolved = {}, onBack = {}, onOpenComputer = {}, onOpenOverview = {},
                    )
                }
            }
        }
        compose.runOnIdle { scene.session.connect() }
        compose.waitUntil(5000) { scene.session.state.value.bot(fixture.id) != null }
        compose.waitForIdle()
        assertTrue("fixture must compose settled rows", counts.keys.any { it.startsWith("message:") })
    }

    @After
    fun stop() {
        if (::scene.isInitialized) compose.runOnIdle {
            scene.environment.liveCalls.hangUp()
            scene.session.disconnect()
        }
        server.shutdown()
    }

    @Test
    fun typingStreamingBusyFleetAndCaptionsStayInTheirOwnRows() {
        measure("typing40") {
            repeat(40) { compose.onNode(hasSetTextAction()).performTextInput("x") }
        }
        // Establish a live reply before counting deltas (not the typing-to-reply transition).
        publish(fixture.threadId, "First ")
        measure("openThread200") { repeat(200) { publish(fixture.threadId, "x") } }
        measure("otherThreads200") { repeat(200) { publish("thread-bot-${2 + it % 8}", "x") } }
        compose.runOnIdle { scene.environment.liveCalls.start(fixture.id, fixture.threadId, fixture.name, MicrophoneAccess { it(true) }) }
        compose.waitUntil(5000) { scene.environment.liveCalls.state.value.phase == LiveCallPhase.LIVE }
        caption("First ")
        measure("captions200") { repeat(200) { caption("x") } }
    }

    private fun publish(threadId: String, delta: String) {
        val expected = scene.session.state.value.streaming[threadId].orEmpty() + delta
        compose.runOnIdle {
            assertTrue(frames.tryEmit(StreamFrame(Frame.Runtime(RuntimeEvent("content.delta", threadId, delta, "assistant_text")), seq = ++sequence)))
        }
        compose.waitUntil(5000) { scene.session.state.value.streaming[threadId] == expected }
        compose.waitForIdle()
    }

    private fun caption(delta: String) {
        val expected = scene.environment.liveCalls.state.value.caption + delta
        compose.runOnIdle { transport.say("{\"type\":\"session.output_transcript.delta\",\"delta\":\"$delta\"}") }
        compose.waitUntil(5000) { scene.environment.liveCalls.state.value.caption == expected }
        compose.waitForIdle()
    }

    private fun measure(scenario: String, action: () -> Unit) {
        compose.runOnIdle { counts.clear() }
        action()
        val snapshot = compose.runOnIdle { counts.toMap() }
        val rows = snapshot.filterKeys { it.startsWith("row:") }.values.sum()
        val messages = snapshot.filterKeys { it.startsWith("message:") }.values.sum()
        val live = snapshot["live"] ?: 0
        println("RECOMPOSITION_BENCH scenario=$scenario rows=$rows messages=$messages live=$live keys=$snapshot")
        if (System.getenv("COMPANION_PERF_BASELINE") != "1") {
            assertEquals("$scenario must not recompose settled row bodies", 0, messages)
            assertEquals("$scenario must not recompose settled lazy items", 0, rows)
            if (scenario == "openThread200") assertTrue("live item must consume streamed text", live > 0)
        }
    }

    private class CaptionTransport : LiveCallTransport {
        private var listener: LiveCallTransport.Listener? = null
        override suspend fun offer(listener: LiveCallTransport.Listener): String { this.listener = listener; return "offer" }
        override suspend fun accept(answerSdp: String) { listener?.onChannelOpen() }
        fun say(json: String) { listener?.onMessage(json) }
        override fun setMuted(muted: Boolean) = Unit
        override fun sendClose() = Unit
        override fun close() = Unit
    }
}
