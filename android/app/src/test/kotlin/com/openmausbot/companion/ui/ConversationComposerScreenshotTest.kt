package com.openmausbot.companion.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.MotionDurationScale
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.PendingMessageAttachment
import com.openmausbot.companion.core.QueuedSend
import java.io.File
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native production composer components; no transport or user data is involved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class ConversationComposerScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = object : MotionDurationScale { override val scaleFactor = 0f })
    @Test fun lightComposerCards() = capture(false)
    @Test fun darkComposerCards() = capture(true)
    @Test fun largeTypeRtlComposerCards() = capture(true, rtl = true, fontScale = 1.3f)

    private fun capture(dark: Boolean, rtl: Boolean = false, fontScale: Float = 1f) {
        val scene = WiringScene(Connection(id = "composer-showcase", name = "Offline composer", host = "127.0.0.1", port = 1))
        val attachment = PendingMessageAttachment(id = "offline-document", data = "Your weekend route".toByteArray(), name = "Weekend notes.pdf", mime = "application/pdf", kind = PendingMessageAttachment.Kind.FILE)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalCompanion provides scene.environment,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, fontScale)) {
                CompanionTheme(darkTheme = dark) {
                    CompositionLocalProvider(LocalConversationTint provides conversationTint("green")) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                MausAvatar("green", size = 64.dp)
                                Text("Ready when you are", style = MaterialTheme.typography.titleLarge)
                                PendingAttachmentChip(attachment, enabled = true, onRemove = {})
                                QueuedSendRow(QueuedSend("offline-send", "A little more time by the sea, please."), onSteer = {}, steering = false, onEdit = {}, onCancel = {}, modifier = Modifier.fillMaxWidth())
                                PredictiveChipsRow(listOf(PredictiveChip("What's next?", "What's next?", "next"), PredictiveChip("Run tests", "Run tests", "tests")), onSelect = {})
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Weekend notes.pdf").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit this queued message").assertIsDisplayed()
        val output = File("build/outputs/transcript-screenshots/showcase-composer-${if (rtl) "large-type-rtl" else if (dark) "dark" else "light"}.png")
        output.parentFile.mkdirs()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
