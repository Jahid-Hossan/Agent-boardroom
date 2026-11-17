package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.ui.theme.AccentAmberBright
import com.example.ui.theme.BgElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextPrimary

@Composable
fun FormattedMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    val annotated = remember(text, color) { parseSimpleMarkdown(text, color) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = color),
        modifier = modifier
    )
}

private fun parseSimpleMarkdown(input: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = input.split("\n")
        lines.forEachIndexed { lineIndex, rawLine ->
            val line = when {
                rawLine.startsWith("### ") -> rawLine.removePrefix("### ")
                rawLine.startsWith("## ") -> rawLine.removePrefix("## ")
                rawLine.startsWith("# ") -> rawLine.removePrefix("# ")
                else -> rawLine
            }
            val isHeading = rawLine.startsWith("#")
            if (isHeading) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AccentAmberBright)) {
                    appendInlineFormatting(line, baseColor)
                }
            } else {
                appendInlineFormatting(line, baseColor)
            }
            if (lineIndex < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineFormatting(line: String, baseColor: Color) {
    var i = 0
    while (i < line.length) {
        if (line.startsWith("**", i)) {
            val end = line.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                    append(line.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (line[i] == '`') {
            val end = line.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(
                    SpanStyle(
                        fontFamily = JetBrainsMonoFamily,
                        background = BgElevated,
                        color = AccentAmberBright
                    )
                ) {
                    append(" ${line.substring(i + 1, end)} ")
                }
                i = end + 1
                continue
            }
        }
        append(line[i])
        i++
    }
}
