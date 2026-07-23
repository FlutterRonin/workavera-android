package com.workavera.android.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.model.MemoryCategory
import com.workavera.android.data.model.SavedChatMemory
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.LoadingIndicator
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val memoryDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/** Material icon for each memory category (SF Symbol equivalents from iOS). */
val MemoryCategory.icon: ImageVector
    get() = when (this) {
        MemoryCategory.PREFERENCE -> Icons.Outlined.Tune
        MemoryCategory.PERSONAL -> Icons.Outlined.Person
        MemoryCategory.WORK -> Icons.Outlined.Work
        MemoryCategory.GOAL -> Icons.Outlined.Adjust
        MemoryCategory.CONSTRAINT -> Icons.Outlined.PanTool
    }

/**
 * Saved Memories management, mirroring the iOS SavedMemoriesView: searchable,
 * category-filterable list with per-item active toggle, edit, delete, and a
 * delete-all action, plus a create/edit editor. Capped at [MEMORY_LIMIT] items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMemoriesScreen(memoryEnabled: Boolean, onDismiss: () -> Unit) {
    val viewModel: SavedMemoriesViewModel = viewModel(factory = SavedMemoriesViewModel.Factory)
    val ui = viewModel.uiState
    var editorFor by remember { mutableStateOf<EditorTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedChatMemory?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Saved Memories") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { filterExpanded = true }) {
                                    Icon(Icons.Outlined.FilterList, contentDescription = "Filter memories")
                                }
                                DropdownMenu(
                                    expanded = filterExpanded,
                                    onDismissRequest = { filterExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Categories") },
                                        onClick = {
                                            filterExpanded = false
                                            viewModel.setCategoryFilter(null)
                                        },
                                    )
                                    MemoryCategory.entries.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category.label) },
                                            leadingIcon = {
                                                Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                filterExpanded = false
                                                viewModel.setCategoryFilter(category)
                                            },
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { editorFor = EditorTarget(null) },
                                enabled = !ui.atLimit,
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add memory")
                            }
                        },
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    if (!ui.hasLoaded && ui.isLoading) {
                        LoadingIndicator("Loading memories…")
                    } else {
                        MemoryList(
                            viewModel = viewModel,
                            memoryEnabled = memoryEnabled,
                            onEdit = { editorFor = EditorTarget(it) },
                            onDeleteRequest = { deleteTarget = it },
                            onDeleteAll = { confirmDeleteAll = true },
                        )
                    }
                }
            }
        }
    }

    editorFor?.let { target ->
        MemoryEditorDialog(
            memory = target.memory,
            onSave = viewModel::save,
            onDismiss = { editorFor = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete memory?") },
            text = {
                Text("This memory will be permanently removed and will no longer be available to Chat.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all memories?") },
            text = { Text("Every saved Chat memory will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAll()
                        confirmDeleteAll = false
                    },
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MemoryList(
    viewModel: SavedMemoriesViewModel,
    memoryEnabled: Boolean,
    onEdit: (SavedChatMemory) -> Unit,
    onDeleteRequest: (SavedChatMemory) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val ui = viewModel.uiState
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search memories") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (ui.error != null) {
            item { ErrorBanner(message = ui.error, onRetry = viewModel::refresh) }
        }
        if (!memoryEnabled) {
            item { MemoryDisabledBanner() }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ui.categoryFilter?.label ?: "All Memories",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${ui.memories.size} of $MEMORY_LIMIT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val visible = ui.visible
        if (visible.isEmpty()) {
            item {
                EmptyState(
                    hasAny = ui.memories.isNotEmpty(),
                )
            }
        } else {
            items(visible, key = { it.id }) { memory ->
                MemoryRow(
                    memory = memory,
                    isMutating = memory.id in ui.mutatingIds,
                    onToggle = { viewModel.setActive(memory, it) },
                    onEdit = { onEdit(memory) },
                    onDelete = { onDeleteRequest(memory) },
                )
            }
        }

        if (ui.memories.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDeleteAll, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete All Memories", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MemoryRow(
    memory: SavedChatMemory,
    isMutating: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                memory.memoryCategory.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = memory.memoryCategory.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = memory.memoryOrigin.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (isMutating) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Switch(
                checked = memory.active,
                onCheckedChange = onToggle,
                enabled = !isMutating,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = memory.content,
            style = MaterialTheme.typography.bodyMedium,
            color = if (memory.active) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (memory.fromChat) {
                Icon(
                    Icons.AutoMirrored.Outlined.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "From Chat",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = memory.updatedAt?.let {
                    memoryDateFormat.format(it.atZone(ZoneId.systemDefault()))
                } ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit, enabled = !isMutating, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit memory",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, enabled = !isMutating, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete memory",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MemoryDisabledBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Psychology,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Chat memory is currently disabled. You can still review and edit saved items.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(hasAny: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Psychology,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasAny) "No Memories Found" else "No Saved Memories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasAny) {
                "Try another search or category."
            } else {
                "Add a memory here or ask Chat to remember something."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private data class EditorTarget(val memory: SavedChatMemory?)

/** Create/edit editor for a single memory. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryEditorDialog(
    memory: SavedChatMemory?,
    onSave: (String?, MemoryCategory, String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var category by remember { mutableStateOf(memory?.memoryCategory ?: MemoryCategory.PREFERENCE) }
    var content by remember { mutableStateOf(memory?.content ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (memory == null) "New Memory" else "Edit Memory") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, enabled = !isSaving) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    isSaving = true
                                    error = null
                                    onSave(memory?.id, category, content) { err ->
                                        isSaving = false
                                        if (err == null) onDismiss() else error = err
                                    }
                                },
                                enabled = content.isNotBlank() && !isSaving,
                            ) {
                                Text(if (isSaving) "Saving…" else "Save")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Text(
                        text = "CATEGORY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable(enabled = !isSaving) { categoryExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(category.label, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.UnfoldMore, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                        ) {
                            MemoryCategory.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = {
                                        Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        categoryExpanded = false
                                        category = option
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { if (it.length <= 500) content = it },
                        label = { Text("Memory") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "One concise, durable fact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${content.length}/500",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
