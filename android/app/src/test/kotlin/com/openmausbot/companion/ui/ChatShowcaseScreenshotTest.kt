package com.openmausbot.companion.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.openmausbot.companion.core.*
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every pixel is the production ChatScreen; every computer response is synthetic and loopback-only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class ChatShowcaseScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = object : MotionDurationScale { override val scaleFactor = 0f })
    private lateinit var server: MockWebServer
    private lateinit var scene: WiringScene
    private val updates = MutableSharedFlow<StreamFrame>(extraBufferCapacity = 8)
    private val previewPng by lazy {
        val bitmap = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(231, 245, 238))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(24f, 24f, 616f, 376f, 20f, 20f, paint)
        paint.color = android.graphics.Color.rgb(0, 110, 69); paint.textSize = 30f
        canvas.drawText("Your weekend by the coast", 48f, 84f, paint)
        paint.color = android.graphics.Color.rgb(41, 55, 48); paint.textSize = 23f
        listOf("10:30   A quiet coastal walk", "12:15   Café near the station", "16:00   Afternoon train home").forEachIndexed { index, text -> canvas.drawText(text, 48f, 146f + index * 56f, paint) }
        paint.textSize = 20f; paint.color = android.graphics.Color.rgb(91, 108, 100)
        canvas.drawText("Saved to your weekend notes.", 48f, 336f, paint)
        java.io.ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); output.toByteArray() }
    }
    private val reply = """
        ## Ready for your weekend
        The route is saved, and the packing list is a little lighter.

        - A quiet coastal walk
        - A café near the station
        - [x] Save the route
        - [ ] Check the weather before leaving

        Your notes live in `weekend.md`.

        ```sh
        git diff --stat
        ./gradlew :core:test
        ```

        | Stop | Time | Budget |
        | :--- | :---: | ---: |
        | Coast path | 10:30 | €0 |
        | Café | 12:15 | €14 |
        | Train home | 16:00 | €9 |

        Everything is ready. The weather lookup failed once; I used the saved forecast instead.
    """.trimIndent()
    private fun snapshot(id: String, at: Double, done: Int, active: Int?, turn: String = "weekend") = Message(id, Message.Role.BOT, Message.Kind.ACTIVITY, at,
        tool = ToolActivity("TodoWrite", ok = true, input = "{\"todos\":[" + listOf("Check the route", "Save the packing list", "Share the weekend plan").mapIndexed { index, text ->
            "{\"content\":\"$text\",\"activeForm\":\"${if (index == 1) "Saving the packing list" else "Checking the route"}\",\"status\":\"${if (index < done) "completed" else if (index == active) "in_progress" else "pending"}\"}"
        }.joinToString(",") + "]}"), turnId = turn)
    private fun fixture(): List<Message> {
        val first = listOf(Message("ask", Message.Role.USER, Message.Kind.TEXT, 1000.0, text = "Could you plan a cozy weekend by the coast?"),
            Message("narration", Message.Role.BOT, Message.Kind.TEXT, 2000.0, text = "I'll find a quiet route and get your notes ready.", turnId = "weekend"),
            snapshot("plan1", 3000.0, 0, null), snapshot("plan2", 4000.0, 1, 1),
            Message("shell", Message.Role.BOT, Message.Kind.ACTIVITY, 5000.0, tool = ToolActivity("shell", true, summary = "git diff --stat"), turnId = "weekend"),
            Message("read", Message.Role.BOT, Message.Kind.ACTIVITY, 6000.0, tool = ToolActivity("read", true, summary = "notes/weekend.md"), turnId = "weekend"),
            Message("edit", Message.Role.BOT, Message.Kind.ACTIVITY, 7000.0, tool = ToolActivity("edit", true, summary = "Saved the route and packing list"), turnId = "weekend"),
            Message("web", Message.Role.BOT, Message.Kind.ACTIVITY, 8000.0, tool = ToolActivity("web_search", false, summary = "Coastal weather · connection timed out"), turnId = "weekend"),
            snapshot("plan3", 9000.0, 3, null),
            Message("reply", Message.Role.BOT, Message.Kind.TEXT, 10000.0, text = reply, turnId = "weekend", turnTerminal = true),
            Message("digest", Message.Role.BOT, Message.Kind.DIGEST, 11000.0, turnId = "weekend", turnSucceeded = true,
                digest = StructuredTurnDigest("weekend", "bot-1", "thread-bot-1", 11000.0, 72000.0,
                    listOf(DigestTool("shell", 2, 0, "git diff --stat"), DigestTool("read", 1, 0, "notes/weekend.md"), DigestTool("edit", 1, 0, "Saved the route"), DigestTool("web_search", 1, 1, "Coastal weather")),
                    listOf(DigestMemory("preferences/weekends.md", "updated")), "Ready for your weekend", "preview",
                    files = DigestFiles(changed = listOf("notes/weekend.md"), added = listOf("routes/coast.gpx")), usage = DigestUsage(10300, 2000, 4200, 0.04))),
            Message("quiet-ask", Message.Role.USER, Message.Kind.TEXT, 12000.0, text = "Thanks. Could you keep that café in my notes?"),
            Message("quiet-reply", Message.Role.BOT, Message.Kind.TEXT, 13000.0, text = "Of course. I've saved it for next time.", turnId = "save", turnTerminal = true),
            Message("quiet-digest", Message.Role.BOT, Message.Kind.DIGEST, 14000.0, turnId = "save", turnSucceeded = true,
                digest = StructuredTurnDigest("save", "bot-1", "thread-bot-1", 14000.0, 40000.0,
                    listOf(DigestTool("memory_update", 1, 0, "Saved your café preference")), listOf(DigestMemory("preferences/cafes.md", "updated")), "Saved", "full")),
            Message("question", Message.Role.BOT, Message.Kind.OPTIONS, 14100.0, card = OptionCard("A little more personal", "One question before I finish your plan", emptyList(),
                requestId = "showcase-question", questionRequest = QuestionRequestCardData(questions = listOf(AskQuestion("What kind of afternoon sounds good?", header = "Pace",
                    options = listOf(AskQuestionOption("Take it slow", "More time by the sea"), AskQuestionOption("See a little more", "A café and a short coastal walk"))))))),
            Message("routine", Message.Role.BOT, Message.Kind.ROUTINE_RUN, 14200.0,
                routineRun = RoutineRunCard("showcase-run", "morning-check", "Morning check-in", status = "completed", summary = "Your route is saved, and the afternoon train is running on time.")),
            Message("compaction", Message.Role.BOT, Message.Kind.COMPACTION, 14300.0,
                compaction = Compaction("Scout kept the route, café preference, and packing list so your next message can pick up naturally.", 24000)),
            Message("update", Message.Role.BOT, Message.Kind.ACTIVITY, 14400.0,
                tool = ToolActivity("error: Claude Code is too old", false, setup = true, claudeUpdate = true)),
            Message("screen", Message.Role.BOT, Message.Kind.SCREEN, 14410.0, png = android.util.Base64.encodeToString(previewPng, android.util.Base64.NO_WRAP), mime = "image/png"),
            Message("generated-image", Message.Role.BOT, Message.Kind.TEXT, 14420.0, text = "A little preview for you.",
                attachments = listOf(MessageImageAttachment("image", "/fixture/weekend.png", "image/png"))),
            Message("voice-note", Message.Role.BOT, Message.Kind.TEXT, 14430.0, text = "A quick note for the walk.",
                attachments = listOf(MessageImageAttachment("audio", "/fixture/weekend-note.mp3", "audio/mpeg", 4200.0))),
            Message("diff", Message.Role.BOT, Message.Kind.TEXT, 14435.0, text = "```diff\n--- a/notes/weekend.md\n+++ b/notes/weekend.md\n@@ -1 +1 @@\n-A hurried afternoon\n+A little more time by the sea\n```"),
            Message("document", Message.Role.USER, Message.Kind.TEXT, 14440.0, text = "Your notes\n<attached-file path=\"/fixture/weekend.pdf\" name=\"Weekend notes.pdf\" />"),
            Message("webhook", Message.Role.USER, Message.Kind.TEXT, 15000.0, text = "[AUTHENTICATED WEBHOOK TASK]\nCheck the train times for Saturday.\n[/AUTHENTICATED WEBHOOK TASK]\n\n[UNTRUSTED WEBHOOK EVENT DATA]\nEvent: calendar.updated\n{\"trip\":\"coast\"}\n[/UNTRUSTED WEBHOOK EVENT DATA]"),
            Message("busy-ask", Message.Role.USER, Message.Kind.TEXT, 16000.0, text = "Let's book the afternoon train."),
            Message("busy-narration", Message.Role.BOT, Message.Kind.TEXT, 16500.0, text = "Checking the fare before I book it.", turnId = "booking"),
            snapshot("live-plan", 17000.0, 1, 1, "booking"))
        return (first + CompanionJson.decodeFromString<List<Message>>("""[{"id":"approval","role":"bot","kind":"options","at":18000,"card":{"title":"Book the afternoon train?","subtitle":"Saturday · 16:00 · €9","options":["Allow once","Deny"],"requestId":"showcase-approval","tool":"Bash"}}]"""))
            .map { it.copy(at = 1791193200000.0 + it.at) }
    }
    @Before fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = if (request.path?.endsWith("/file") == true)
                MockResponse().setHeader("Content-Type", "image/png").setHeader("Content-Disposition", "attachment; filename=weekend.png").setBody(okio.Buffer().write(previewPng))
            else MockResponse().setHeader("Content-Type", "application/json")
                .setBody(if (request.path == "/api/instances") "{\"instances\":[]}" else "{\"messages\":[],\"hasMore\":false}")
        }
        server.start()
    }
    @After fun stop() { if (::scene.isInitialized) scene.session.disconnect(); server.shutdown() }
    @Test fun lightShowcase() = showcase(false)
    @Test fun darkShowcase() = showcase(true)
    @Test fun largeTypeRtlShowcase() = showcase(true, rtl = true, fontScale = 1.3f)

    private fun showcase(dark: Boolean, rtl: Boolean = false, fontScale: Float = 1f) {
        val fixture = bot(name = "Scout", busy = true).copy(title = "Your thoughtful travel companion", modelSelection = ModelSelection("instance-1", "Claude Sonnet 4.5"), messages = fixture())
        scene = WiringScene(Connection(id = "chat-showcase", name = "Synthetic showcase", host = "127.0.0.1", port = server.port), fleet = Fleet(listOf(fixture, bot("bot-2", "Pip")), emptyList())) {
            flow {
                emit(StreamFrame(Frame.Hello(cursor = "showcase:1", resumed = false), seq = 1))
                emit(StreamFrame(Frame.Runtime(RuntimeEvent("content.delta", fixture.threadId,
                    "I'm comparing the afternoon train with the coastal route. The station is close to the café, so you won't have to rush.\n\nBefore booking, I'll confirm the fare and ask for your approval. Your saved preferences say you like a little breathing room between plans.", "reasoning_text")), seq = 2))
                emitAll(updates)
            }
        }
        scene.environment.chatPreferences.setActivityDetail(ActivityDetail.FULL)
        scene.environment.chatPreferences.setShowWorkSummaries(true)
        scene.environment.chatPreferences.setQuickReplies(emptyList())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalCompanion provides scene.environment,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, fontScale)) {
            CompanionTheme(darkTheme = dark) {
                val state by scene.session.state.collectAsState()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (state.bot(fixture.id) != null) ChatScreen(Destination.Chat(Chat.BotChat(fixture).target), {}, {}, {}, {})
                }
            }
        } }
        compose.runOnIdle { scene.session.connect() }
        compose.waitUntil(5000) { scene.session.state.value.bot(fixture.id) != null && scene.session.state.value.reasoning[fixture.threadId] != null }
        compose.waitForIdle()
        val suffix = if (rtl) "large-type-rtl" else if (dark) "dark" else "light"
        compose.onNodeWithTag("live-plan-strip").assertIsDisplayed()
        capture("showcase-busy-approval-$suffix")
        compose.onNodeWithTag("chat-transcript").performTouchInput { swipeDown() }
        compose.onNodeWithTag("jump-to-latest").assertIsDisplayed()
        val frozenOffset = compose.onNodeWithTag("chat-transcript").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        compose.runOnIdle {
            updates.tryEmit(StreamFrame(Frame.Runtime(RuntimeEvent("content.delta", fixture.threadId,
                "\n\nThe afternoon train is on time, and the station has step-free access.", "reasoning_text")), seq = 3))
        }
        compose.waitUntil(5000) { scene.session.state.value.reasoning[fixture.threadId]?.contains("step-free access") == true }
        compose.onNodeWithTag("jump-to-latest-face", useUnmergedTree = true).assertIsDisplayed()
        Assert.assertEquals(frozenOffset, compose.onNodeWithTag("chat-transcript").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value())
        capture("showcase-jump-$suffix")
        compose.onNodeWithTag("jump-to-latest").performClick()
        compose.onNodeWithTag("jump-to-latest").assertDoesNotExist()
        compose.onNodeWithTag("live-plan-strip").performClick()
        compose.onNodeWithTag("plan-plan.live-plan").assertIsDisplayed()
        compose.onNodeWithTag("thinking-row").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("@")
        compose.onNodeWithText("@Pip").assertIsDisplayed()
        capture("showcase-mention-$suffix")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithTag("thinking-row").performClick()
        compose.onNodeWithTag("thinking-panel").assertIsDisplayed()
        compose.onNodeWithTag("thinking-panel").performScrollTo()
        capture("showcase-thinking-$suffix")
        compose.onNodeWithTag("thinking-row").performClick()
        scroll(hasTestTag("plan-plan.plan1"))
        capture("showcase-plan-$suffix")
        scroll(hasTestTag("step-shell"))
        capture("showcase-steps-$suffix")
        scroll(hasText("Ready for your weekend"))
        capture("showcase-markdown-$suffix")
        scroll(hasText("DATA TABLE"))
        capture("showcase-table-$suffix")
        scroll(hasTestTag("message-question"))
        capture("showcase-question-$suffix")
        scroll(hasTestTag("message-routine"))
        capture("showcase-routine-$suffix")
        scroll(hasTestTag("message-compaction"))
        capture("showcase-compaction-$suffix")
        scroll(hasTestTag("step-update"))
        capture("showcase-claude-update-$suffix")
        scroll(hasTestTag("message-screen"))
        compose.waitUntil(5000) { compose.onAllNodes(hasContentDescription("frame", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        capture("showcase-screenshot-$suffix")
        scroll(hasTestTag("message-generated-image"))
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Image attachment: weekend.png. Tap to preview.").fetchSemanticsNodes()
            .any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        capture("showcase-image-$suffix")
        scroll(hasTestTag("message-voice-note"))
        compose.onNodeWithContentDescription("Play voice note").assertIsDisplayed()
        capture("showcase-voice-$suffix")
        scroll(hasTestTag("message-document"))
        capture("showcase-document-$suffix")
        scroll(hasTestTag("message-diff"))
        capture("showcase-diff-$suffix")
        scroll(hasTestTag("message-webhook"))
        capture("showcase-webhook-$suffix")
        scroll(hasTestTag("digest-line-digest"))
        compose.onNodeWithTag("digest-line-digest").performClick()
        compose.onNodeWithTag("digest-sheet").assertIsDisplayed()
        compose.onNodeWithText("$0.04").assertIsDisplayed()
        capture("showcase-digest-$suffix")
        compose.onNode(hasText("Copy") and hasAnyAncestor(hasTestTag("digest-sheet"))).performClick()
        compose.onNode(hasText("Copied") and hasAnyAncestor(hasTestTag("digest-sheet"))).assertIsDisplayed()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            Assert.assertTrue(clipboard.primaryClip!!.getItemAt(0).text.toString().contains("routes/coast.gpx"))
        }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { scene.environment.chatPreferences.setShowWorkSummaries(false) }
        scroll(hasText("Of course. I've saved it for next time."))
        compose.onNodeWithText("Of course. I've saved it for next time.").performClick()
        compose.onNodeWithTag("message-meta-quiet-reply", useUnmergedTree = true).assertExists()
        capture("showcase-message-details-$suffix")
        compose.onNodeWithText("· What I did").performClick()
        compose.onNodeWithTag("digest-sheet").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { scene.environment.chatPreferences.setActivityDetail(ActivityDetail.REDUCED) }
        scroll(hasTestTag("step-run-run.shell"))
        capture("showcase-step-run-$suffix")
        compose.onNodeWithTag("step-run-run.shell").performClick()
        compose.onNodeWithTag("step-shell").assertExists()
        capture("showcase-step-timeline-$suffix")
        compose.runOnIdle { scene.environment.chatPreferences.setActivityDetail(ActivityDetail.HIDDEN) }
        compose.onNodeWithTag("thinking-row").assertDoesNotExist()
        compose.onNodeWithTag("live-status-line").assertIsDisplayed()
        capture("showcase-hidden-live-$suffix")
        scroll(hasTestTag("chat-poster"))
        capture("showcase-poster-$suffix")
    }
    private fun scroll(matcher: SemanticsMatcher) {
        compose.onNodeWithTag("chat-transcript").performScrollToNode(matcher)
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            view.draw(canvas)
            // ModalBottomSheet owns a dialog window, not the activity's decor tree.
            org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.draw(canvas)
            val file = File("build/outputs/transcript-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
