package com.workavera.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workavera.android.ui.theme.StatusOnline
import kotlinx.coroutines.delay

/**
 * Fenced code block with a language tag, copy button, monospaced syntax-
 * highlighted content, and a theme-aware gray surface; horizontally
 * scrollable for long lines (iOS MarkdownCodeBlockView).
 */
@Composable
fun MarkdownCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val colors = syntaxColors()
    val highlighted = remember(code, colors) { highlightedCode(code, colors) }
    val baseStyle = MaterialTheme.typography.bodyMedium
    val codeStyle = baseStyle.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = (baseStyle.fontSize.value * 0.88f).sp,
        lineHeight = (baseStyle.fontSize.value * 0.88f * 1.4f).sp,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.trim()?.takeIf { it.isNotEmpty() }?.lowercase() ?: "code",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (copied) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (copied) "Copied" else "Copy",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (copied) {
                        StatusOnline
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = highlighted,
            style = codeStyle,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}

// ---- Lightweight regex-based syntax colorizer (iOS WorkspaceCodeHighlighter) ----

private data class SyntaxColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
)

/** Xcode-like token colors that adapt to light and dark appearances. */
@Composable
private fun syntaxColors(): SyntaxColors =
    if (androidx.compose.foundation.isSystemInDarkTheme()) {
        SyntaxColors(
            keyword = Color(0.94f, 0.47f, 0.71f),
            string = Color(0.98f, 0.60f, 0.54f),
            number = Color(0.60f, 0.80f, 1.00f),
            comment = Color(0.50f, 0.58f, 0.55f),
        )
    } else {
        SyntaxColors(
            keyword = Color(0.61f, 0.12f, 0.55f),
            string = Color(0.77f, 0.20f, 0.18f),
            number = Color(0.11f, 0.35f, 0.80f),
            comment = Color(0.42f, 0.48f, 0.44f),
        )
    }

private val KEYWORDS = listOf(
    "func", "function", "def", "fn", "return", "let", "var", "const", "val",
    "class", "struct", "enum", "interface", "protocol", "extension", "import",
    "export", "from", "package", "namespace", "if", "else", "elif", "for",
    "while", "do", "switch", "case", "break", "continue", "default", "guard",
    "defer", "try", "catch", "throw", "throws", "finally", "async", "await",
    "yield", "public", "private", "protected", "internal", "static", "final",
    "abstract", "override", "new", "self", "this", "super", "nil", "null",
    "true", "false", "void", "in", "is", "as", "typeof", "instanceof",
    "lambda", "where", "pub", "impl", "trait", "match", "mut", "use",
).joinToString("|")

private val SYNTAX_REGEX = Regex(
    "(?<comment>//[^\\n]*|#[^\\n]*|/\\*[\\s\\S]*?\\*/)" +
        "|(?<string>\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`)" +
        "|(?<number>\\b0x[0-9a-fA-F]+\\b|\\b\\d[\\d_]*\\.?\\d*\\b)" +
        "|(?<keyword>\\b(?:$KEYWORDS)\\b)",
)

/** Splits code into comment/string/number/keyword segments and colors them. */
private fun highlightedCode(code: String, colors: SyntaxColors): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        for (match in SYNTAX_REGEX.findAll(code)) {
            if (match.range.first > cursor) {
                append(code.substring(cursor, match.range.first))
            }
            val color = when {
                match.groups["comment"] != null -> colors.comment
                match.groups["string"] != null -> colors.string
                match.groups["number"] != null -> colors.number
                match.groups["keyword"] != null -> colors.keyword
                else -> null
            }
            if (color != null) {
                withStyle(SpanStyle(color = color)) { append(match.value) }
            } else {
                append(match.value)
            }
            cursor = match.range.last + 1
        }
        if (cursor < code.length) {
            append(code.substring(cursor))
        }
    }
