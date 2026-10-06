package com.openmausbot.companion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** The skin owns the neutral; the contact owns the quiet tint laid over it. */
@Immutable
data class ConversationTint(val theirs: Color, val ink: Color, val glyphFill: Color, val wash: Color, val inset: Color) {
    val actionText: Color get() = if (contrast(Color.White, ink) >= 4.5f) Color.White else Color.Black
    companion object {
        fun create(color: Color, neutral: Color, dark: Boolean): ConversationTint {
            val minimumSeparation = if (dark) 1.25f else 1.20f
            var fill = if (dark) 0.17f else 0.11f
            var theirs = lerp(neutral, color, fill)
            val fillTarget = if (dark) color else lerp(color, Color.Black, 0.05f)
            while (contrast(theirs, neutral) < minimumSeparation && fill < 1f) {
                fill = (fill + 0.025f).coerceAtMost(1f)
                theirs = lerp(neutral, fillTarget, fill)
            }
            val target = if (contrast(Color.White, theirs) > contrast(Color.Black, theirs)) Color.White else Color.Black
            var amount = if (dark) 0.25f else 0.20f
            var ink = lerp(color, target, amount)
            while (contrast(ink, theirs) < 4.5f && amount < 1f) {
                amount = (amount + 0.05f).coerceAtMost(1f)
                ink = lerp(color, target, amount)
            }
            return ConversationTint(theirs, ink, ink.copy(alpha = if (dark) 0.20f else 0.14f),
                color.copy(alpha = if (dark) 0.22f else 0.14f), target.copy(alpha = if (dark) 0.07f else 0.06f))
        }
        private fun contrast(a: Color, b: Color): Float {
            val x = a.luminance(); val y = b.luminance()
            return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
        }
    }
}

internal val LocalConversationTint = compositionLocalOf<ConversationTint?> { null }

@Composable
internal fun conversationTint(color: String = "blue"): ConversationTint {
    val scheme = MaterialTheme.colorScheme
    return remember(color, scheme) {
        ConversationTint.create(Color(MausPalette.argb(color)), scheme.background, scheme.background.luminance() < 0.5f)
    }
}

internal val chatTint: ConversationTint
    @Composable get() = LocalConversationTint.current ?: conversationTint()
