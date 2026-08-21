package com.workavera.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownDimens
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

/** Which type scale a markdown body is rendered at. */
enum class MarkdownVariant {
    /**
     * Chat messages: body-sized text with headings kept near it, so a reply
     * never dwarfs the app chrome around it.
     */
    Chat,

    /**
     * Document bodies: the web reader's scale (`.workavera-doc-reader`), so a
     * document reads the same on a phone as on the desktop.
     */
    Document,
}

/**
 * Shared Compose-native markdown renderer (multiplatform-markdown-renderer
 * with the Material 3 theme + Coil 3 images). Used by the Docs reader and by
 * Chat, at the two scales [MarkdownVariant] names. Tables and other GFM
 * elements are handled by the library.
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    variant: MarkdownVariant = MarkdownVariant.Chat,
) {
    Markdown(
        content = content,
        imageTransformer = Coil3ImageTransformerImpl,
        // Fenced/indented code blocks get the shared Workavera presentation:
        // language tag + copy button + syntax highlighting (iOS
        // workspaceMarkdownCodeStyle).
        components = markdownComponents(
            codeFence = { model ->
                // A multi-line fence has one CODE_FENCE_CONTENT child per
                // line, so the code is the span from the first content child
                // to the last (mirrors the library's own MarkdownCodeFence).
                val node = model.node
                val content = model.content
                val language = node
                    .findChildOfType(MarkdownTokenTypes.FENCE_LANG)
                    ?.getTextInNode(content)?.toString()
                if (node.children.size >= 3) {
                    val start = node.children[2].startOffset
                    val minCodeFenceCount =
                        if (language != null && node.children.size > 3) 3 else 2
                    val end = node.children[
                        (node.children.size - 2).coerceAtLeast(minCodeFenceCount),
                    ].endOffset
                    MarkdownCodeBlock(
                        code = content.subSequence(start, end).toString()
                            .replaceIndent()
                            .trimEnd(),
                        language = language,
                    )
                }
            },
            codeBlock = { model ->
                val node = model.node
                val start = node.children[0].startOffset
                val end = node.children[node.children.size - 1].endOffset
                MarkdownCodeBlock(
                    code = model.content.subSequence(start, end).toString()
                        .replaceIndent()
                        .trimEnd(),
                    language = null,
                )
            },
            table = { model ->
                when (variant) {
                    MarkdownVariant.Chat -> MarkdownTable(
                        model.content,
                        model.node,
                        style = model.typography.table,
                    )
                    MarkdownVariant.Document -> WrappingMarkdownTable(model)
                }
            },
        ),
        typography = when (variant) {
            MarkdownVariant.Chat -> chatTypography()
            MarkdownVariant.Document -> documentTypography()
        },
        padding = when (variant) {
            MarkdownVariant.Chat -> markdownPadding()
            MarkdownVariant.Document -> documentPadding()
        },
        dimens = when (variant) {
            MarkdownVariant.Chat -> markdownDimens()
            MarkdownVariant.Document -> documentDimens()
        },
        modifier = modifier,
    )
}

/**
 * A table whose cells wrap instead of ending in an ellipsis.
 *
 * The library caps every cell at one line, as the web reader does. On a phone
 * that hides the part of a cell that did not fit, with no way to reach it —
 * a cell reading "破坏交叉编译和 C…" never tells you it meant `CGO_ENABLED=0`.
 * A document is read to be read, so height is the cheaper thing to spend.
 */
@Composable
private fun WrappingMarkdownTable(model: MarkdownComponentModel) {
    MarkdownTable(
        content = model.content,
        node = model.node,
        style = model.typography.table,
        headerBlock = { content, header, tableWidth, style ->
            MarkdownTableHeader(
                content = content,
                header = header,
                tableWidth = tableWidth,
                style = style,
                verticalAlignment = Alignment.Top,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
        },
        rowBlock = { content, row, tableWidth, style ->
            MarkdownTableRow(
                content = content,
                header = row,
                tableWidth = tableWidth,
                style = style,
                verticalAlignment = Alignment.Top,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
        },
    )
}

/**
 * Headings mapped down to near-body sizes; the library defaults render them at
 * display size, which dwarfs the surrounding app chrome.
 */
@Composable
private fun chatTypography(): MarkdownTypography {
    val typography = MaterialTheme.typography
    return markdownTypography(
        h1 = typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        h2 = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        h3 = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        h4 = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        h5 = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        h6 = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        text = typography.bodyMedium.copy(lineHeight = 1.65.em),
        paragraph = typography.bodyMedium.copy(lineHeight = 1.65.em),
    )
}

/**
 * The web reader's type scale, restated here: 16sp at a 1.6 line height, bold
 * headings on tight leading, and every block sharing one body style so a
 * document keeps the rhythm it was written in.
 *
 * Headings 1 and 2 are the one deliberate departure. The web reader sets them
 * at 3em and 2em for a wide column; on a phone that leaves a title a few
 * characters to a line.
 */
@Composable
private fun documentTypography(): MarkdownTypography {
    val body = MaterialTheme.typography.bodyLarge.copy(
        fontSize = DOCUMENT_BASE_SIZE.sp,
        lineHeight = 1.6.em,
    )
    return markdownTypography(
        h1 = documentHeading(body, scale = 2f),
        h2 = documentHeading(body, scale = 1.5f),
        h3 = documentHeading(body, scale = 1.3f),
        h4 = documentHeading(body, scale = 1f),
        h5 = documentHeading(body, scale = 0.9f),
        h6 = documentHeading(body, scale = 0.8f),
        text = body,
        paragraph = body,
        // The library italicises quotes by default; the web reader marks them
        // with the bar alone and leaves the text upright.
        quote = body,
        ordered = body,
        bullet = body,
        list = body,
    )
}

private fun documentHeading(body: TextStyle, scale: Float): TextStyle = body.copy(
    fontSize = (DOCUMENT_BASE_SIZE * scale).sp,
    lineHeight = 1.25.em,
    fontWeight = FontWeight.Bold,
)

/**
 * The web reader packs blocks 3px apart and lets the line height carry the
 * rhythm, rather than spacing them a full line apart.
 */
@Composable
private fun documentPadding(): MarkdownPadding = markdownPadding(
    block = 3.dp,
    list = 3.dp,
    listItemTop = 3.dp,
    listItemBottom = 3.dp,
    listIndent = 24.dp,
)

/**
 * [tableCellWidth] is the width below which the table scrolls sideways rather
 * than sharing the screen between its columns. The library's 160dp puts a
 * two-column table right at the edge of a phone's content width, which would
 * hand a wrapping table a horizontal scroll it does not need; 110dp lets two
 * and three columns fit and wrap, and leaves wider tables scrolling.
 */
@Composable
private fun documentDimens(): MarkdownDimens = markdownDimens(
    tableCellWidth = 110.dp,
)

private const val DOCUMENT_BASE_SIZE = 16f
