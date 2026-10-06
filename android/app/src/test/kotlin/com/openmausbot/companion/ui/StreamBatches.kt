package com.openmausbot.companion.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.openmausbot.companion.core.FRAME_BATCH_WINDOW_MILLIS
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * Waits for state the stream delivers. Frames reach the session one batch
 * window apart (`inBatches`), timed on the main looper's clock, which in a
 * Robolectric test only moves when told to: each poll moves it one window.
 */
internal fun ComposeTestRule.waitForStream(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
    waitUntil(timeoutMillis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_BATCH_WINDOW_MILLIS + 1))
        condition()
    }
}
