package com.workavera.android.ui.docs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.workavera.android.data.model.DocumentShare
import com.workavera.android.data.model.DocumentShareExpiry
import com.workavera.android.data.model.WorkspaceDocument
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.SelectMenu
import com.workavera.android.ui.components.SelectMenuItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

private val shareDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/**
 * Publishes a document to a public link (web app ShareDocumentButton).
 * Publishing pins the revision the link serves, so the snapshot stays where it
 * is while editing continues; changing the expiry leaves it alone and only
 * "Publish latest" moves it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentShareSheet(
    document: WorkspaceDocument,
    viewModel: DocsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The view model owns the link, so the top bar behind this sheet reflects
    // publishing and revoking without being told twice.
    val share = viewModel.shareOf(document.id)
    val loaded = document.id in viewModel.resolvedShareIds
    var expiry by remember { mutableStateOf(DocumentShareExpiry.NEVER) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    /** Brings the picker in line with the link the view model now holds. */
    fun syncExpiry() {
        expiry = if (viewModel.shareOf(document.id)?.expires.isNullOrEmpty()) {
            DocumentShareExpiry.NEVER
        } else {
            DocumentShareExpiry.KEEP
        }
    }

    suspend fun loadShare() {
        error = null
        syncExpiry()
        try {
            viewModel.refreshShare(document.id)
            syncExpiry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not load the share link."
        }
    }

    LaunchedEffect(document.id) { loadShare() }

    fun publish(republish: Boolean, nextExpiry: DocumentShareExpiry) {
        if (working) return
        working = true
        scope.launch {
            try {
                viewModel.publishShare(
                    document.id,
                    nextExpiry.requestValue(share),
                    republish,
                )
                syncExpiry()
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Could not update the share link."
            } finally {
                working = false
            }
        }
    }

    fun revoke() {
        if (working) return
        working = true
        scope.launch {
            try {
                viewModel.revokeShare(document.id)
                syncExpiry()
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Could not revoke the share link."
            } finally {
                working = false
            }
        }
    }

    // Publishing roughly triples the height of the sheet. Left partially
    // expanded it would hide the link it just created below the fold, so the
    // sheet opens on the whole of its content and scrolls if it has to.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Share Document",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Anyone with the link can read this document without signing in. " +
                    "The link serves the revision you publish, so later edits stay " +
                    "private until you publish again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (error != null) {
                ErrorBanner(message = error!!, onRetry = { scope.launch { loadShare() } })
                Spacer(Modifier.height(16.dp))
            }

            val current = share
            when {
                !loaded -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }

                current != null -> {
                    ShareLinkCard(url = viewModel.shareUrl(current), context = context)
                    Spacer(Modifier.height(16.dp))
                    PublishedRevisionRow(
                        share = current,
                        document = document,
                        working = working,
                        onPublishLatest = { publish(true, expiry) },
                    )
                    Spacer(Modifier.height(16.dp))
                    ExpiryRow(
                        share = current,
                        selected = expiry,
                        enabled = !working,
                        onSelect = { option ->
                            expiry = option
                            publish(false, option)
                        },
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { revoke() },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Revoke Link")
                    }
                }

                else -> {
                    ExpiryRow(
                        share = null,
                        selected = expiry,
                        enabled = !working,
                        onSelect = { expiry = it },
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { publish(false, expiry) },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (working) "Creating…" else "Create Link")
                    }
                }
            }
        }
    }
}

/** The link itself, with the two ways to hand it to someone. */
@Composable
private fun ShareLinkCard(url: String, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { copyLink(context, url) }) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            TextButton(onClick = { sendLink(context, url) }) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        }
    }
}

/** Which revision the link serves, and the way to move it to the newest one. */
@Composable
private fun PublishedRevisionRow(
    share: DocumentShare,
    document: WorkspaceDocument,
    working: Boolean,
    onPublishLatest: () -> Unit,
) {
    val behind = share.revision < document.revision
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (behind) {
                "Published revision ${share.revision} · document is at ${document.revision}"
            } else {
                "Published revision ${share.revision} · up to date"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (behind) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onPublishLatest, enabled = !working) {
                Text("Publish Latest")
            }
        }
    }
}

/**
 * Expiry picker. "Keep" is offered only for a link that already expires, so
 * every other change to it leaves that date where the creator set it.
 */
@Composable
private fun ExpiryRow(
    share: DocumentShare?,
    selected: DocumentShareExpiry,
    enabled: Boolean,
    onSelect: (DocumentShareExpiry) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = buildList {
        if (!share?.expires.isNullOrEmpty()) add(DocumentShareExpiry.KEEP)
        add(DocumentShareExpiry.NEVER)
        add(DocumentShareExpiry.SEVEN_DAYS)
        add(DocumentShareExpiry.THIRTY_DAYS)
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Expires",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = expiryLabel(selected, share),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SelectMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                SelectMenuItem(
                    label = expiryLabel(option, share),
                    selected = option == selected,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** "Keep" names the date it would keep, so the choice is never a guess. */
private fun expiryLabel(expiry: DocumentShareExpiry, share: DocumentShare?): String {
    if (expiry != DocumentShareExpiry.KEEP) return expiry.label
    val expiresAt = share?.expiresAt ?: return expiry.label
    return "Keep ${shareDateFormat.format(expiresAt.atZone(ZoneId.systemDefault()))}"
}

private fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Document link", url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}

private fun sendLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, "Share link"))
}
