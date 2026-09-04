package com.workavera.android.ui.reading

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workavera.android.data.model.CandidateSummary
import com.workavera.android.data.model.DiscoverCandidate
import com.workavera.android.data.model.ReadingSource
import com.workavera.android.data.model.ReadingSourceInput
import com.workavera.android.data.model.ReadingSourceKind
import com.workavera.android.data.model.SummaryLanguage
import com.workavera.android.data.model.TrendingSince
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.SelectMenu
import com.workavera.android.ui.components.SelectMenuItem
import com.workavera.android.ui.notifications.RelativeTime
import com.workavera.android.ui.settings.FormCard
import com.workavera.android.ui.settings.FormTextField
import java.net.URI

private val SheetCardShape = RoundedCornerShape(14.dp)

/**
 * Discover sheet (iOS ReadingDiscoverView / web ReadingDiscoverDialog): fetch
 * what the enabled subscriptions are showing right now, summarize anything
 * worth a closer look, and keep only what is wanted. The enable toggle on a
 * subscription is the selection — there is no second switch for that choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingDiscoverSheet(
    viewModel: ReadingDiscoverViewModel,
    onDismiss: () -> Unit,
) {
    var editorInput by remember { mutableStateOf<ReadingSourceInput?>(null) }
    var editingSourceId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ReadingSource?>(null) }

    LaunchedEffect(Unit) { viewModel.loadSources() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Discover",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }

            viewModel.errorMessage?.let { message ->
                item {
                    ErrorBanner(message = message, onRetry = { viewModel.loadSources() })
                }
            }

            item { FetchCard(viewModel) }

            if (viewModel.failures.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        viewModel.failures.forEach { failure ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Outlined.Warning,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .size(14.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${failure.sourceName}: ${failure.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item { CandidatesSection(viewModel) }
            item {
                SubscriptionsSection(
                    viewModel = viewModel,
                    onAdd = {
                        editingSourceId = null
                        editorInput = ReadingSourceInput()
                    },
                    onEdit = { source ->
                        editingSourceId = source.id
                        editorInput = ReadingSourceInput(
                            name = source.name,
                            kind = source.kind,
                            url = source.url,
                            language = source.language,
                            since = source.since,
                            enabled = source.enabled,
                        )
                    },
                    onDelete = { deleteTarget = it },
                )
            }
        }
    }

    editorInput?.let { initial ->
        SourceEditorSheet(
            initial = initial,
            editing = editingSourceId != null,
            isSaving = viewModel.isSavingSource,
            viewModel = viewModel,
            sourceId = editingSourceId,
            onDismiss = {
                editorInput = null
                editingSourceId = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove subscription?") },
            text = {
                Text(
                    "“${target.name}” stops feeding Discover. Reading items already kept " +
                        "are not affected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.deleteSource(target.id)
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

// MARK: - Fetch controls

/** Language picker and the Fetch button, wrapped in one card. */
@Composable
private fun FetchCard(viewModel: ReadingDiscoverViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, SheetCardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (viewModel.sources.isEmpty()) {
            Text(
                text = "No subscriptions yet. Add one below to start fetching.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DiscoverPickerRow(
            title = "Summary Language",
            value = viewModel.language.raw,
            options = SummaryLanguage.entries,
            optionLabel = { it.raw },
            enabled = true,
            onSelect = viewModel::selectLanguage,
        )
        Button(
            onClick = { viewModel.discover() },
            enabled = !viewModel.isFetching && viewModel.enabledSources.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (viewModel.isFetching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Fetching…")
            } else {
                Text("Fetch")
            }
        }
        if (viewModel.sources.isNotEmpty()) {
            // Fetch reads every subscription switched on below.
            Text(
                text = "Fetch reads every subscription enabled below " +
                    "(${viewModel.enabledSources.size} of ${viewModel.sources.size}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> DiscoverPickerRow(
    title: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
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
                    label = optionLabel(option),
                    selected = optionLabel(option) == value,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

// MARK: - Candidates

@Composable
private fun CandidatesSection(viewModel: ReadingDiscoverViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "CANDIDATES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        when {
            viewModel.isFetching && viewModel.candidates.isEmpty() -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Fetching subscriptions…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            viewModel.candidates.isEmpty() -> Text(
                text = if (viewModel.hasFetched) {
                    "Nothing came back from the selected subscriptions."
                } else {
                    "Pick your subscriptions and fetch to see what is out there."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, SheetCardShape)
                    .padding(horizontal = 16.dp),
            ) {
                viewModel.candidates.forEachIndexed { index, candidate ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    CandidateRow(
                        candidate = candidate,
                        summary = viewModel.summaries[candidate.url],
                        isSummarizing = viewModel.summarizingUrl == candidate.url,
                        isSaving = viewModel.savingUrl == candidate.url,
                        onSummarize = { viewModel.summarize(candidate) },
                        onSave = { viewModel.save(candidate) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: DiscoverCandidate,
    summary: CandidateSummary?,
    isSummarizing: Boolean,
    isSaving: Boolean,
    onSummarize: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = candidate.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(candidate.url)))
                }
            },
        )
        // One composed meta line: separate labels each wrapped onto their own
        // line once the row filled up.
        Text(
            text = candidate.metaText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (candidate.description.isNotEmpty()) {
            Text(
                text = candidate.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (summary != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = summary.summary, style = MaterialTheme.typography.bodySmall)
                summary.keyPoints.forEach { point ->
                    Row {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(4.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = point,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Text-only actions: the card above already says which language
        // summaries come in, and "Add" is enough next to a reading list.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (candidate.alreadySaved) {
                Text(
                    text = "Added",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                OutlinedButton(
                    onClick = onSummarize,
                    enabled = !isSummarizing && !isSaving,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    if (isSummarizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (summary == null) "Summarize" else "Summarize Again",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = !isSaving && !isSummarizing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Add", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

// MARK: - Subscriptions

@Composable
private fun SubscriptionsSection(
    viewModel: ReadingDiscoverViewModel,
    onAdd: () -> Unit,
    onEdit: (ReadingSource) -> Unit,
    onDelete: (ReadingSource) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "SUBSCRIPTIONS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, SheetCardShape)
                .padding(horizontal = 16.dp),
        ) {
            viewModel.sources.forEachIndexed { index, source ->
                if (index > 0) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                SourceRow(
                    source = source,
                    onToggle = { viewModel.setSourceEnabled(source.id, it) },
                    onEdit = { onEdit(source) },
                    onDelete = { onDelete(source) },
                )
            }
            if (viewModel.sources.isNotEmpty()) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !viewModel.isSavingSource, onClick = onAdd)
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Add Subscription",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (viewModel.isLoadingSources) {
            Text(
                text = "Loading subscriptions…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun SourceRow(
    source: ReadingSource,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (source.lastError.isNotEmpty()) {
                Text(
                    text = source.lastError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (source.lastFetchedAt != null) {
                Text(
                    text = "Fetched ${RelativeTime.format(source.lastFetchedAt.toString())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Long-press-free removal stays one tap away through the edit sheet;
        // the row itself mirrors iOS: edit on tap, enable on the switch.
        Switch(checked = source.enabled, onCheckedChange = onToggle)
    }
}

// MARK: - Subscription editor

/** Add / edit sheet for one subscription (iOS SourceEditorSheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceEditorSheet(
    initial: ReadingSourceInput,
    editing: Boolean,
    isSaving: Boolean,
    viewModel: ReadingDiscoverViewModel,
    sourceId: String?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(initial.kind) }
    var url by remember { mutableStateOf(initial.url) }
    var language by remember { mutableStateOf(initial.language) }
    var since by remember { mutableStateOf(initial.since) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank() && when (kind) {
        ReadingSourceKind.RSS -> hasValidFeedUrl(url.trim())
        ReadingSourceKind.GITHUB_TRENDING -> language.isNotBlank()
    }

    fun save() {
        if (!isValid || working || isSaving) return
        working = true
        val input = ReadingSourceInput(
            name = name,
            kind = kind,
            url = url,
            language = language,
            since = since,
            enabled = enabled,
        )
        viewModel.saveSource(sourceId, input) { error ->
            working = false
            if (error == null) onDismiss() else errorMessage = error
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!working) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (editing) "Edit Subscription" else "Add Subscription",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FormCard {
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Name",
                    enabled = !working,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    DiscoverPickerRow(
                        title = "Kind",
                        value = kind.label,
                        options = ReadingSourceKind.entries,
                        optionLabel = { it.label },
                        enabled = !working,
                        onSelect = { kind = it },
                    )
                }
            }

            when (kind) {
                ReadingSourceKind.RSS -> FormCard {
                    FormTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = "Feed URL",
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }

                ReadingSourceKind.GITHUB_TRENDING -> FormCard {
                    FormTextField(
                        value = language,
                        onValueChange = { language = it },
                        placeholder = "Language (e.g. go, typescript)",
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        DiscoverPickerRow(
                            title = "Period",
                            value = since.label,
                            options = TrendingSince.entries,
                            optionLabel = { it.label },
                            enabled = !working,
                            onSelect = { since = it },
                        )
                    }
                }
            }
            Text(
                text = when (kind) {
                    ReadingSourceKind.RSS -> "An RSS or Atom feed address."
                    ReadingSourceKind.GITHUB_TRENDING -> "GitHub's trending board for one language."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Enabled",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = !working)
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
                TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { save() }, enabled = isValid && !working && !isSaving) {
                    Text(
                        when {
                            working -> "Saving…"
                            editing -> "Save"
                            else -> "Add"
                        },
                    )
                }
            }
        }
    }
}

private fun hasValidFeedUrl(raw: String): Boolean {
    if (raw.isEmpty()) return false
    return try {
        URI(raw).host != null
    } catch (_: Exception) {
        false
    }
}
