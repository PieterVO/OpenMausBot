package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ToolCategory

@Composable
internal fun ToolGlyph(category: ToolCategory, tint: Color, modifier: Modifier = Modifier) {
    val drawable = when (category) {
        ToolCategory.SHELL -> R.drawable.ic_tool_terminal
        ToolCategory.READ -> R.drawable.ic_tool_document
        ToolCategory.WEB -> R.drawable.ic_tool_globe
        ToolCategory.BROWSER -> R.drawable.ic_tool_browser
        ToolCategory.COMPUTER -> R.drawable.ic_tool_computer
        ToolCategory.MEMORY -> R.drawable.ic_tool_bookmark
        ToolCategory.MESSAGE -> R.drawable.ic_tool_chat
        ToolCategory.IMAGE -> R.drawable.ic_tool_image
        else -> null
    }
    if (drawable != null) Icon(painterResource(drawable), null, tint = tint, modifier = modifier)
    else Icon(when (category) {
        ToolCategory.PLAN -> Icons.AutoMirrored.Filled.List
        ToolCategory.EDIT -> Icons.Filled.Edit
        ToolCategory.SEARCH -> Icons.Filled.Search
        ToolCategory.MAIL -> Icons.Filled.Email
        ToolCategory.CALENDAR -> Icons.Filled.DateRange
        else -> Icons.Filled.Build
    }, null, tint = tint, modifier = modifier)
}

@Composable
internal fun StepBadge(category: ToolCategory, failed: Boolean = false, modifier: Modifier = Modifier) {
    val ink = if (failed) MaterialTheme.colorScheme.error else chatTint.ink
    Box(modifier.size(22.dp).background(if (failed) ink.copy(alpha = 0.14f) else chatTint.glyphFill, CircleShape), contentAlignment = Alignment.Center) {
        ToolGlyph(category, ink, Modifier.size(13.dp))
    }
}

@Composable
internal fun StepFailure(modifier: Modifier = Modifier) {
    val danger = MaterialTheme.colorScheme.error
    val foreground = if (1.05f / (danger.luminance() + 0.05f) >= 3f) Color.White else Color.Black
    Canvas(modifier) {
        drawCircle(danger)
        val stroke = 1.5.dp.toPx()
        drawLine(foreground, Offset(size.width * 0.33f, size.height * 0.33f), Offset(size.width * 0.67f, size.height * 0.67f), stroke)
        drawLine(foreground, Offset(size.width * 0.67f, size.height * 0.33f), Offset(size.width * 0.33f, size.height * 0.67f), stroke)
    }
}
