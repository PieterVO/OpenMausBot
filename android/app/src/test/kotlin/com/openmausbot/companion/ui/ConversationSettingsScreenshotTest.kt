package com.openmausbot.companion.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.openmausbot.companion.core.Connection
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real settings and isolated preferences; the synthetic computer is never contacted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConversationSettingsScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightWorkSummaries() = capture(false)
    @Test fun darkWorkSummaries() = capture(true)

    @Test fun largeTypeRtlWorkSummaries() = capture(true, rtl = true, fontScale = 1.3f)
    private fun capture(dark: Boolean, rtl: Boolean = false, fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        val scene = WiringScene(Connection(id = "settings-showcase", name = "Offline settings", host = "127.0.0.1", port = 1))
        val skin = AppearanceSkin.entries.first { it.isDark == dark }
        scene.environment.chatPreferences.setAppearanceSkin(skin)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalCompanion provides scene.environment,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, fontScale)) {
                CompanionTheme(skin = skin) {
                    Surface(Modifier.fillMaxSize()) { SettingsScreen(onBack = {}) }
                }
            }
        }
        val toggle = compose.onNodeWithContentDescription("Work summaries")
        toggle.performScrollTo().assertIsOff()
        compose.onNodeWithText("Show what each reply did under it. Always shown when a step failed.").performScrollTo()
        val suffix = if (rtl) "large-type-rtl" else if (dark) "dark" else "light"
        val output = File("build/outputs/transcript-screenshots/showcase-settings-$suffix.png")
        output.parentFile.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        toggle.performClick().assertIsOn()
    }
}
