package com.openmausbot.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.openmausbot.companion.BuildConfig

/** Installed only by performance fixtures; the constant false release branch has no effects. */
internal val LocalRecompositionProbe = staticCompositionLocalOf<((String) -> Unit)?> { null }

@Composable
internal inline fun RecompositionProbe(key: () -> String) {
    if (BuildConfig.DEBUG) {
        val observer = LocalRecompositionProbe.current
        if (observer != null) {
            val identity = key()
            SideEffect { observer(identity) }
        }
    }
}
