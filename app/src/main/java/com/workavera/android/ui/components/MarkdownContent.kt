package com.workavera.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Shared Compose-native markdown renderer (multiplatform-markdown-renderer
 * with the Material 3 theme + Coil 3 images). Used by the Docs reader and,
 * later, by Chat. Tables and other GFM elements are handled by the library.
 *
 * Headings are mapped down to near-body sizes (the library defaults render
 * them at display size, which dwarfs the surrounding app chrome).
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
) {
    val typography = MaterialTheme.typography
    Markdown(
        content = content,
        imageTransformer = Coil3ImageTransformerImpl,
        typography = markdownTypography(
            h1 = typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            h2 = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            h3 = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            h4 = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            h5 = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            h6 = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            text = typography.bodyMedium.copy(lineHeight = 1.65.em),
            paragraph = typography.bodyMedium.copy(lineHeight = 1.65.em),
        ),
        modifier = modifier,
    )
}
