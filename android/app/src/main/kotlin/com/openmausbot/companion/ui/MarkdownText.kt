package com.openmausbot.companion.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One block parser for settled replies, live replies and embedded tables. */
@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier, caret: Boolean = false, openLink: ((String) -> Unit)? = null) {
    MarkdownBlocks(remember(source) { Markdown.blocks(source) }, modifier, caret, openLink)
}

@Composable
internal fun MarkdownBlocks(blocks: List<MarkdownBlock>, modifier: Modifier = Modifier, caret: Boolean = false, openLink: ((String) -> Unit)? = null) {
    CompositionLocalProvider(LocalLinkOpener provides openLink) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEachIndexed { index, block -> MarkdownBlockView(block, caret && index == blocks.lastIndex) }
        }
    }
}

private val LocalLinkOpener = compositionLocalOf<((String) -> Unit)?> { null }

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, tail: Boolean) {
    val tint = chatTint
    val muted = secondaryTint
    when (block) {
        is MarkdownBlock.Paragraph -> MarkdownInlineText(block.text, tail, style = MaterialTheme.typography.bodyLarge)
        is MarkdownBlock.Heading -> MarkdownInlineText(block.text, tail,
            style = when (block.level) { 1 -> MaterialTheme.typography.titleMedium; 2 -> MaterialTheme.typography.titleSmall; else -> MaterialTheme.typography.labelLarge },
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        is MarkdownBlock.Bullet -> MarkerRow(block.indent, block.text, tail) {
            Box(Modifier.padding(top = 8.dp).size(5.dp).background(tint.ink, CircleShape))
        }
        is MarkdownBlock.Ordered -> MarkerRow(block.indent, block.text, tail) {
            Text("${block.number}.", style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), color = tint.ink)
        }
        is MarkdownBlock.Task -> Row(Modifier.padding(start = (block.indent * 14).dp).semantics(mergeDescendants = true) {
            contentDescription = "${if (block.checked) "completed" else "not completed"}, ${block.text}"
        }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            block.number?.let { Text("$it.", style = MaterialTheme.typography.bodyLarge, color = tint.ink) }
            PlanCircle(if (block.checked) TodoStatus.DONE else TodoStatus.PENDING, tint.ink, Modifier.padding(top = 2.dp).size(18.dp))
            MarkdownInlineText(block.text, tail, style = MaterialTheme.typography.bodyLarge,
                color = if (block.checked) muted else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (block.checked) TextDecoration.LineThrough else null)
        }
        is MarkdownBlock.Table -> DataTableCard(TranscriptCard.Table(block.headers, block.rows, block.alignments))
        is MarkdownBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
            MarkdownInlineText(block.text, tail, style = MaterialTheme.typography.bodyLarge, color = muted)
        }
        is MarkdownBlock.Code -> CodeBlock(block, tail)
        MarkdownBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 2.dp))
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code, tail: Boolean) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var copied by remember(block.text) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
    Column(Modifier.fillMaxWidth().background(chatTint.inset, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(block.language.orEmpty(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                color = secondaryTint, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("OpenMausBot code", block.text))) }
                haptics.play(HapticCue.SELECT); copied = true
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_tool_copy), null, modifier = Modifier.size(16.dp), tint = chatTint.ink)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (copied) R.string.mobile_copied_8e3df45a else R.string.mobile_copy_af74f7c5), color = chatTint.ink)
            }
        }
        Text(buildAnnotatedString { append(block.text); appendCaret(tail, secondaryTint) },
            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            softWrap = false, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp))
    }
}

@Composable
private fun MarkerRow(indent: Int, text: String, tail: Boolean, marker: @Composable () -> Unit) {
    Row(Modifier.padding(start = (indent * 14).dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.widthIn(min = 16.dp), contentAlignment = Alignment.TopCenter) { marker() }
        MarkdownInlineText(text, tail, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun MarkdownInlineText(text: String, tail: Boolean, style: TextStyle, modifier: Modifier = Modifier,
    color: Color = Color.Unspecified, fontWeight: FontWeight? = null, textDecoration: TextDecoration? = null) {
    val tint = chatTint
    val muted = secondaryTint
    val opener = LocalLinkOpener.current
    val spans = remember(text) { InlineMarkdown.parse(text) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val codeStyle = style.copy(fontFamily = FontFamily.Monospace, fontSize = style.fontSize * 0.94f,
        fontWeight = fontWeight ?: style.fontWeight, textDecoration = textDecoration ?: style.textDecoration)
    val content = remember(spans, codeStyle, density, measurer, color, tint.inset, tint.ink) { spans.mapIndexedNotNull { index, span ->
        if (InlineStyle.CODE !in span.styles) return@mapIndexedNotNull null
        var decoration = codeStyle.textDecoration
        if (InlineStyle.STRIKE in span.styles) decoration = (decoration ?: TextDecoration.None) + TextDecoration.LineThrough
        if (span.link != null) decoration = (decoration ?: TextDecoration.None) + TextDecoration.Underline
        val code = codeStyle.copy(fontWeight = if (InlineStyle.BOLD in span.styles) FontWeight.Bold else codeStyle.fontWeight,
            fontStyle = if (InlineStyle.ITALIC in span.styles) FontStyle.Italic else codeStyle.fontStyle, textDecoration = decoration)
        val accent = span.link != null || (color == Color.Unspecified && (InlineStyle.BOLD in span.styles || InlineStyle.ITALIC in span.styles))
        val measured = measurer.measure(AnnotatedString(span.text), code, softWrap = false)
        val width = with(density) { (measured.size.width + 6.dp.toPx()).toSp() }
        val height = with(density) { measured.size.height.toSp() }
        "code-$index" to InlineTextContent(Placeholder(width, height, PlaceholderVerticalAlign.TextCenter)) {
            Text(span.text, style = code, color = if (accent) tint.ink else color, softWrap = false,
                modifier = Modifier.background(tint.inset, RoundedCornerShape(5.dp)).padding(horizontal = 3.dp))
        }
    }.toMap() }
    val annotated = remember(spans, content, tail, muted, tint.ink, opener, color) { buildAnnotatedString {
        for ((index, span) in spans.withIndex()) {
            val spanStyle = SpanStyle(fontWeight = if (InlineStyle.BOLD in span.styles) FontWeight.Bold else null,
                fontStyle = if (InlineStyle.ITALIC in span.styles) FontStyle.Italic else null,
                fontFamily = if (InlineStyle.CODE in span.styles) FontFamily.Monospace else null,
                color = if (color == Color.Unspecified && (InlineStyle.BOLD in span.styles || InlineStyle.ITALIC in span.styles)) tint.ink else Color.Unspecified,
                textDecoration = if (InlineStyle.STRIKE in span.styles) TextDecoration.LineThrough else null)
            val url = span.link?.takeIf { it.isNotBlank() }
            if (url != null) withLink(LinkAnnotation.Url(url, TextLinkStyles(spanStyle.copy(color = tint.ink, textDecoration = TextDecoration.Underline)),
                linkInteractionListener = opener?.let { open -> LinkInteractionListener { open(url) } })) {
                    if ("code-$index" in content) appendInlineContent("code-$index", span.text) else append(span.text)
                }
            else if ("code-$index" in content) appendInlineContent("code-$index", span.text)
            else withStyle(spanStyle) { append(span.text) }
        }
        appendCaret(tail, muted)
    } }
    Text(annotated, modifier, color = color, style = style, fontWeight = fontWeight, textDecoration = textDecoration, inlineContent = content)
}

private fun AnnotatedString.Builder.appendCaret(tail: Boolean, muted: Color) {
    if (tail) withStyle(SpanStyle(color = muted)) { append(" ▍") }
}
