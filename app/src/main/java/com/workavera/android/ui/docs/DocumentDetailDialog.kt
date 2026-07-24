package com.workavera.android.ui.docs

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.workavera.android.data.model.DocumentKind
import com.workavera.android.data.model.DocumentStatus
import com.workavera.android.data.model.DocumentVersion
import com.workavera.android.data.model.WorkspaceDocument
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.components.MarkdownContent
import com.workavera.android.ui.notifications.RelativeTime

/** One-time warning color, matching the iOS .orange accents. */
private val WarningOrange = androidx.compose.ui.graphics.Color(0xFFFF9500)

/**
 * Full-screen document reader (iOS DocumentDetailView): header meta, markdown
 * rendered to styled HTML, HTML documents shown as-is, plus version history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentDetailDialog(
    documentId: String,
    viewModel: DocsViewModel,
    onDismiss: () -> Unit,
) {
    val document = viewModel.documentCache[documentId]
    val isLoading = documentId in viewModel.loadingDocumentIds
    var showVersions by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) { viewModel.loadDocument(documentId) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = document?.title ?: "Document",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (document != null) {
                                IconButton(onClick = { showVersions = true }) {
                                    Icon(Icons.Outlined.History, contentDescription = "Version history")
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when {
                        document != null -> DocumentContent(document = document, viewModel = viewModel)
                        isLoading -> LoadingIndicator("Loading document…")
                        else -> UnavailableDocument(
                            error = viewModel.uiState.error,
                            onRetry = { viewModel.loadDocument(documentId, force = true) },
                        )
                    }
                }
            }
        }
    }

    if (showVersions && document != null) {
        DocumentVersionsSheet(
            document = document,
            viewModel = viewModel,
            onDismiss = { showVersions = false },
        )
    }
}

@Composable
private fun DocumentContent(document: WorkspaceDocument, viewModel: DocsViewModel) {
    val isDark = isSystemInDarkTheme()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (document.documentKind == DocumentKind.HTML) "HTML" else "MARKDOWN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                if (document.documentStatus == DocumentStatus.ARCHIVED) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ARCHIVED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = WarningOrange,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${viewModel.locationName(document.projectId, document.folderId)} · " +
                    "Version ${document.revision}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (document.updatedAt != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Updated ${RelativeTime.format(document.updated)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (document.content.isBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Empty Document",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else if (document.documentKind == DocumentKind.HTML) {
            HtmlDocumentView(
                html = document.content,
                isDark = isDark,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MarkdownContent(
                content = document.content,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun UnavailableDocument(error: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Document Unavailable",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        ErrorBanner(message = error ?: "This document could not be loaded.", onRetry = onRetry)
    }
}

/** Version history sheet (iOS DocumentVersionsView). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentVersionsSheet(
    document: WorkspaceDocument,
    viewModel: DocsViewModel,
    onDismiss: () -> Unit,
) {
    var versions by remember { mutableStateOf<List<DocumentVersion>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var detailRevision by remember { mutableStateOf<Int?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(document.id, reload) {
        error = null
        val cached = viewModel.loadVersionsFromCache(document.id)
        if (cached.isNotEmpty()) versions = cached
        try {
            versions = viewModel.loadVersions(document.id)
            error = null
        } catch (e: Exception) {
            if (versions == null) error = e.message ?: "Could not load the versions."
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Version History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            when {
                error != null -> ErrorBanner(message = error!!, onRetry = { reload++ })
                versions == null -> LoadingIndicator("Loading versions…")
                versions!!.isEmpty() -> Text(
                    text = "No saved versions are available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                else -> LazyColumn {
                    items(versions!!, key = { it.id }) { version ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailRevision = version.revision }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Version ${version.revision} · ${version.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = version.source.replaceFirstChar { it.uppercase() } +
                                        (version.createdAt?.let { " · ${RelativeTime.format(version.created)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    detailRevision?.let { revision ->
        DocumentVersionDetailDialog(
            document = document,
            revision = revision,
            viewModel = viewModel,
            onDismiss = { detailRevision = null },
        )
    }
}

/** Read-only view of one saved revision (iOS DocumentVersionDetailView). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentVersionDetailDialog(
    document: WorkspaceDocument,
    revision: Int,
    viewModel: DocsViewModel,
    onDismiss: () -> Unit,
) {
    var version by remember { mutableStateOf<DocumentVersion?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(document.id, revision, reload) {
        error = null
        viewModel.loadVersionFromCache(document.id, revision)?.let { version = it }
        try {
            version = viewModel.loadVersion(document.id, revision)
            error = null
        } catch (e: Exception) {
            if (version == null) error = e.message ?: "Could not load the version."
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Version $revision") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when {
                        version != null -> {
                            val content = version!!.content.orEmpty()
                            if (document.documentKind == DocumentKind.HTML) {
                                HtmlDocumentView(html = content, isDark = isDark, modifier = Modifier.fillMaxSize())
                            } else {
                                MarkdownContent(
                                    content = content,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                                )
                            }
                        }
                        error != null -> UnavailableDocument(error = error, onRetry = { reload++ })
                        else -> LoadingIndicator("Loading version…")
                    }
                }
            }
        }
    }
}

/** Styled WebView for HTML document bodies; dark/light aware, JavaScript disabled. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlDocumentView(html: String, isDark: Boolean, modifier: Modifier = Modifier) {
    val page = remember(html, isDark) { wrapHtml(html, isDark) }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
        },
        modifier = modifier,
    )
}

/** Wraps body HTML in a minimal document with readable, theme-aware typography. */
private fun wrapHtml(body: String, isDark: Boolean): String {
    val textColor = if (isDark) "#E5E5E5" else "#1A1A1A"
    val secondaryColor = if (isDark) "#A3A3A3" else "#6B7280"
    val codeBackground = if (isDark) "#262626" else "#F3F4F6"
    val borderColor = if (isDark) "#404040" else "#E5E7EB"
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          body {
            font-family: -apple-system, Roboto, sans-serif;
            font-size: 15px; line-height: 1.65;
            color: $textColor; margin: 0; padding: 4px 20px 32px 20px;
            word-wrap: break-word;
          }
          h1, h2, h3, h4 { line-height: 1.3; margin: 1.2em 0 0.5em; }
          h1 { font-size: 1.6em; } h2 { font-size: 1.35em; } h3 { font-size: 1.15em; }
          p { margin: 0.6em 0; }
          a { color: $textColor; }
          code {
            font-family: Menlo, monospace; font-size: 0.88em;
            background: $codeBackground; padding: 2px 5px; border-radius: 5px;
          }
          pre {
            background: $codeBackground; padding: 12px 14px; border-radius: 10px;
            overflow-x: auto;
          }
          pre code { background: none; padding: 0; }
          blockquote {
            margin: 0.8em 0; padding: 2px 14px;
            border-left: 3px solid $borderColor; color: $secondaryColor;
          }
          ul, ol { padding-left: 1.4em; }
          img { max-width: 100%; height: auto; border-radius: 8px; }
          table { border-collapse: collapse; width: 100%; margin: 0.8em 0; }
          th, td { border: 1px solid $borderColor; padding: 6px 10px; text-align: left; }
          hr { border: none; border-top: 1px solid $borderColor; margin: 1.4em 0; }
        </style>
        </head>
        <body>$body</body>
        </html>
    """.trimIndent()
}
