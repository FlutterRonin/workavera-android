package com.workavera.android.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.model.LlmModel
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.LoadingIndicator

private const val MAX_RECIPIENTS = 100

/**
 * Model Sharing, mirroring the iOS ModelSharingView: pick one of your own model
 * configurations and invite up to 100 users; each recipient gets a notification
 * to accept (creating an independent copy) or reject. Full-screen dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSharingScreen(onDismiss: () -> Unit) {
    val viewModel: LlmModelsViewModel = viewModel(factory = LlmModelsViewModel.Factory)
    val ui = viewModel.uiState
    var selectedModelId by remember { mutableStateOf<String?>(null) }
    var selectedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var modelExpanded by remember { mutableStateOf(false) }
    var successCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
        viewModel.loadShareTargets()
    }
    // Default the selection to the first shareable model once loaded.
    LaunchedEffect(ui.shareableModels) {
        if (selectedModelId == null || ui.shareableModels.none { it.id == selectedModelId }) {
            selectedModelId = ui.shareableModels.firstOrNull()?.id
        }
    }

    val selectedModel = ui.shareableModels.firstOrNull { it.id == selectedModelId }
    val visibleTargets = ui.shareTargets.filter {
        query.isBlank() || it.name.lowercase().contains(query.trim().lowercase())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Model Sharing") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when {
                        !ui.hasLoaded && ui.isLoading -> LoadingIndicator("Loading sharing options…")
                        ui.shareableModels.isEmpty() && ui.error == null ->
                            EmptyShare(hasAnyModel = ui.models.isNotEmpty())
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (ui.error != null) {
                                item { ErrorBanner(message = ui.error, onRetry = viewModel::refresh) }
                            }
                            item {
                                Text(
                                    text = "Recipients get a notification and may accept or reject. Accepting " +
                                        "creates an independent copy; the API key is never displayed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item {
                                ModelPicker(
                                    models = ui.shareableModels,
                                    selected = selectedModel,
                                    expanded = modelExpanded,
                                    onExpandedChange = { modelExpanded = it },
                                    onSelect = { selectedModelId = it },
                                )
                            }
                            item {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = { Text("Search users") },
                                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "RECIPIENTS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (selectedUserIds.isNotEmpty()) {
                                        Text(
                                            text = "${selectedUserIds.size} selected",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (ui.isLoadingTargets && ui.shareTargets.isEmpty()) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Loading users…", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else if (visibleTargets.isEmpty()) {
                                item {
                                    Text(
                                        text = if (ui.shareTargets.isEmpty()) "There are no available recipients." else "No users found.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp),
                                    )
                                }
                            } else {
                                items(visibleTargets, key = { it.id }) { target ->
                                    val checked = target.id in selectedUserIds
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedUserIds = when {
                                                    checked -> selectedUserIds - target.id
                                                    selectedUserIds.size < MAX_RECIPIENTS -> selectedUserIds + target.id
                                                    else -> selectedUserIds
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            if (checked) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                                            contentDescription = null,
                                            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(target.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(
                                            text = target.id.takeLast(6),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        val modelId = selectedModel?.id ?: return@Button
                                        viewModel.share(modelId, selectedUserIds.sorted()) { count, _ ->
                                            if (count != null) {
                                                selectedUserIds = emptySet()
                                                successCount = count
                                            }
                                        }
                                    },
                                    enabled = selectedModel != null && selectedUserIds.isNotEmpty() && !ui.isSharing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (ui.isSharing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        if (ui.isSharing) {
                                            "Sending Invitations…"
                                        } else {
                                            "Share with ${selectedUserIds.size} User${if (selectedUserIds.size == 1) "" else "s"}"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    successCount?.let { count ->
        AlertDialog(
            onDismissRequest = { successCount = null },
            title = { Text("Invitation Sent") },
            text = { Text("Share invitation sent to $count user${if (count == 1) "" else "s"}.") },
            confirmButton = {
                TextButton(onClick = { successCount = null }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun ModelPicker(
    models: List<LlmModel>,
    selected: LlmModel?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    Box {
        OutlinedTextField(
            value = selected?.let { "${it.name} · ${it.modelId}" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Configuration") },
            trailingIcon = { Icon(Icons.Outlined.UnfoldMore, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { onExpandedChange(true) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text("${model.name} · ${model.modelId}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onExpandedChange(false)
                        onSelect(model.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyShare(hasAnyModel: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.IosShare,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("No Models to Share", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasAnyModel) {
                "Configurations received from another user cannot be shared onward."
            } else {
                "Create a model configuration before sharing it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
