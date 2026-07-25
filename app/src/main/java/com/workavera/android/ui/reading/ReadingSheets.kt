package com.workavera.android.ui.reading

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.workavera.android.data.model.LlmModel
import com.workavera.android.data.model.ReadingItem
import com.workavera.android.data.model.ReadingItemInput
import com.workavera.android.data.model.ReadingStatus
import com.workavera.android.ui.notifications.RelativeTime
import java.net.URI

/**
 * Add-by-link sheet (iOS AddReadingItemSheet): Link (URL + title) and Details
 * (description + comma-separated tags) sections. No client-side metadata
 * fetching — the server extracts content when summarizing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddReadingItemSheet(
    viewModel: ReadingViewModel,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val normalizedUrl = url.trim()
    val isValid = title.isNotBlank() && hasValidHttpUrl(normalizedUrl)

    fun save() {
        if (!isValid || isSaving) return
        isSaving = true
        val input = ReadingItemInput(
            url = normalizedUrl,
            title = title.trim(),
            description = description.trim(),
            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        )
        viewModel.create(input) { error ->
            isSaving = false
            if (error == null) onDismiss() else errorMessage = error
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Add Reading Item",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SheetSection("Link") {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://example.com/article") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SheetSection("Details") {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Description (optional)") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    placeholder = { Text("Tags, separated by commas") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { save() }, enabled = isValid && !isSaving) {
                    Text(if (isSaving) "Saving…" else "Add")
                }
            }
        }
    }
}

/** Non-empty http(s) URL with a host, matching the iOS add-sheet validation. */
private fun hasValidHttpUrl(raw: String): Boolean {
    if (raw.isEmpty()) return false
    return try {
        val uri = URI(raw)
        val scheme = uri.scheme?.lowercase()
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

/**
 * Detail sheet (iOS ReadingDetailSheet): header meta, conditional description /
 * summary / key points / tags sections, open-original and summarize actions,
 * and pin / status menus. The summarize flow asks for AI data-sharing consent
 * once per user + model, then confirms the overwrite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingDetailSheet(
    initialItem: ReadingItem,
    viewModel: ReadingViewModel,
    onDismiss: () -> Unit,
) {
    var item by remember { mutableStateOf(initialItem) }
    var confirmSummarize by remember { mutableStateOf(false) }
    var consentModel by remember { mutableStateOf<LlmModel?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val ui = viewModel.uiState
    val isMutating = item.id in ui.mutatingItemIds
    val defaultModel = ui.defaultModel

    LaunchedEffect(Unit) { viewModel.loadModelsIfNeeded() }

    fun summarize() {
        viewModel.summarize(item) { updated -> item = updated }
    }

    fun requestSummary() {
        val model = defaultModel ?: return
        if (viewModel.isAiConsentAuthorized(model.id)) {
            confirmSummarize = true
        } else {
            consentModel = model
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Sheet actions (iOS sheet toolbar): pin toggle + status menu.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMutating) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                IconButton(
                    onClick = { viewModel.togglePin(item) { updated -> item = updated } },
                    enabled = item.readingStatus != ReadingStatus.ARCHIVED && !isMutating,
                ) {
                    Icon(
                        if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (item.pinned) "Unpin" else "Pin",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "Item actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        when (item.readingStatus) {
                            ReadingStatus.UNREAD -> DropdownMenuItem(
                                text = { Text("Mark as Read") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.updateStatus(item, ReadingStatus.READ) { updated -> item = updated }
                                },
                            )
                            ReadingStatus.READ -> DropdownMenuItem(
                                text = { Text("Mark as Unread") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Circle, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.updateStatus(item, ReadingStatus.UNREAD) { updated -> item = updated }
                                },
                            )
                            ReadingStatus.ARCHIVED -> {}
                        }
                        if (item.readingStatus != ReadingStatus.ARCHIVED) {
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.updateStatus(item, ReadingStatus.ARCHIVED) { updated -> item = updated }
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Restore as Unread") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.updateStatus(item, ReadingStatus.UNREAD) { updated -> item = updated }
                                },
                            )
                        }
                    }
                }
            }

            // Header: title + pin, then host · status · relative time.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.pinned) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = "${item.host} · ${item.readingStatus.label} · ${RelativeTime.format(item.updated)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.description.isNotEmpty()) {
                DetailSection(title = "Description", text = item.description)
            }
            if (item.summary.isNotEmpty()) {
                DetailSection(title = "Summary", text = item.summary)
            }
            if (item.keyPoints.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Key Points",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    item.keyPoints.forEach { point ->
                        Row {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (item.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.tags.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Button(
                onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Original Article")
            }
            OutlinedButton(
                onClick = { requestSummary() },
                enabled = !isMutating && !ui.isLoadingModels && defaultModel != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (item.summary.isEmpty()) "Fetch & Summarize" else "Regenerate Summary")
            }
        }
    }

    if (confirmSummarize) {
        AlertDialog(
            onDismissRequest = { confirmSummarize = false },
            title = { Text("Fetch and summarize?") },
            text = {
                Text("The server will fetch the URL and overwrite any existing extracted content, summary, and key points.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSummarize = false
                        summarize()
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSummarize = false }) { Text("Cancel") }
            },
        )
    }

    consentModel?.let { model ->
        AlertDialog(
            onDismissRequest = { consentModel = null },
            title = { Text("Allow AI Data Sharing?") },
            text = {
                Text(
                    "To generate a summary, the article content will be sent to this AI model:\n\n${model.baseUrl}",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.authorizeAiConsent(model.id)
                        consentModel = null
                        summarize()
                    },
                ) { Text("Allow and Summarize") }
            },
            dismissButton = {
                TextButton(onClick = { consentModel = null }) { Text("Not Now") }
            },
        )
    }
}

@Composable
private fun DetailSection(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
