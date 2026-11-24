package com.chartdesk.app.ui.scripts

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.chartdesk.app.ui.theme.ChartDeskPalette

/** Lightweight Pine highlighter: comments, strings, keywords, builtins, numbers and calls. */
class PineHighlighter(private val palette: ChartDeskPalette) : VisualTransformation {

    private val keywords = setOf("var", "if", "else", "for", "to", "by", "and", "or", "not", "true", "false", "na")
    private val builtins = setOf("open", "high", "low", "close", "volume", "time", "bar_index")
    private val pattern = Regex(
        "(//[^\\n]*)|(\"(?:\\\\.|[^\"\\\\])*\")|('(?:\\\\.|[^'\\\\])*')|\\b(ta|math|array|input|request|strategy|color)\\.[A-Za-z_][A-Za-z0-9_]*|\\b([A-Za-z_][A-Za-z0-9_]*)\\b|\\b(\\d+(?:\\.\\d+)?)\\b",
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder(raw)
        pattern.findAll(raw).forEach { match ->
            val range = match.range
            val token = match.value
            val style = when {
                match.groups[1] != null -> SpanStyle(color = palette.textSecondary.copy(alpha = 0.8f))
                match.groups[2] != null || match.groups[3] != null -> SpanStyle(color = palette.warning)
                match.groups[4] != null -> SpanStyle(color = palette.accent)
                match.groups[5] != null -> when {
                    token in keywords -> SpanStyle(color = palette.accent, fontWeight = FontWeight.SemiBold)
                    token in builtins -> SpanStyle(color = palette.textPrimary)
                    else -> SpanStyle(color = palette.textPrimary)
                }
                match.groups[6] != null -> SpanStyle(color = palette.warning)
                else -> SpanStyle(color = palette.textPrimary)
            }
            builder.addStyle(style, range.first, range.last + 1)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
