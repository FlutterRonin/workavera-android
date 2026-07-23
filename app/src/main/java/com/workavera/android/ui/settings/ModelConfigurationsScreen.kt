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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.model.LlmModel
import com.workavera.android.data.model.LlmModelInput
import com.workavera.android.data.model.LlmProtocol
import com.workavera.android.data.model.LlmTokenFormat
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.theme.StatusOnline

/**
 * Model Configurations, mirroring the iOS ModelConfigurationsView: list of LLM
 * configs with set-default / edit / delete, and an editor covering the model,
 * protocol, optional API key, and token limits. Presented as a full-screen dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigurationsScreen(onDismiss: () -> Unit) {
    val viewModel: LlmModelsViewModel = viewModel(factory = LlmModelsViewModel.Factory)
    val ui = viewModel.uiState
    var editorFor by remember { mutableStateOf<ModelEditorTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<LlmModel?>(null) }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Model Configurations") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { editorFor = ModelEditorTarget(null) }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add model")
                            }
                        },
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when {
                        !ui.hasLoaded && ui.isLoading -> LoadingIndicator("Loading model configurations…")
                        ui.models.isEmpty() && ui.error == null ->
                            EmptyModels(onAdd = { editorFor = ModelEditorTarget(null) })
                        else -> ModelList(
                            viewModel = viewModel,
                            onEdit = { editorFor = ModelEditorTarget(it) },
                            onDeleteRequest = { deleteTarget = it },
                        )
                    }
                }
            }
        }
    }

    editorFor?.let { target ->
        ModelEditorDialog(
            model = target.model,
            onSave = viewModel::save,
            onDismiss = { editorFor = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete model configuration?") },
            text = {
                Text("\"${target.name}\" will be permanently removed. Copies previously shared with other users are not affected.")
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
}

@Composable
private fun ModelList(
    viewModel: LlmModelsViewModel,
    onEdit: (LlmModel) -> Unit,
    onDeleteRequest: (LlmModel) -> Unit,
) {
    val ui = viewModel.uiState
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ui.error != null) {
            item { ErrorBanner(message = ui.error, onRetry = viewModel::refresh) }
        }
        item {
            Text(
                text = "AVAILABLE TO YOUR ACCOUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        items(ui.models, key = { it.id }) { model ->
            ModelRow(
                model = model,
                isMutating = model.id in ui.mutatingIds,
                onSetDefault = { viewModel.setDefault(model.id) },
                onEdit = { onEdit(model) },
                onDelete = { onDeleteRequest(model) },
            )
        }
        item {
            Text(
                text = "Saved API keys are handled by protected server APIs and are never displayed again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun ModelRow(
    model: LlmModel,
    isMutating: Boolean,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (model.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "DEFAULT",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusOnline,
                        modifier = Modifier
                            .background(StatusOnline.copy(alpha = 0.12f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                if (model.hasApiKey) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.Key,
                        contentDescription = "API key configured",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${model.modelId} · ${model.baseUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${model.protocol.label} · ${LlmTokenFormat.display(model.effectiveContextTokens)} · " +
                    LlmTokenFormat.display(model.effectiveOutputTokens),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (model.isSharedCopy) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Group,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Shared by ${model.sharedFromName.ifEmpty { "another user" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (isMutating) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        } else {
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Model actions", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (!model.isDefault) {
                        DropdownMenuItem(
                            text = { Text("Set as Default") },
                            onClick = { menuExpanded = false; onSetDefault() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyModels(onAdd: () -> Unit) {
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
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("No Models Configured", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add a model configuration to use Chat and AI features.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd) { Text("Add Model") }
    }
}

private data class ModelEditorTarget(val model: LlmModel?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditorDialog(
    model: LlmModel?,
    onSave: (String?, LlmModelInput, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(model?.name ?: "") }
    var modelId by remember { mutableStateOf(model?.modelId ?: "") }
    var baseUrl by remember { mutableStateOf(model?.baseUrl ?: LlmProtocol.OPENAI.defaultBaseUrl) }
    var protocol by remember { mutableStateOf(model?.protocol ?: LlmProtocol.OPENAI) }
    var maxOutput by remember { mutableStateOf((model?.effectiveOutputTokens ?: 16_384).toString()) }
    var maxContext by remember { mutableStateOf((model?.effectiveContextTokens ?: 256_000).toString()) }
    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var clearSavedKey by remember { mutableStateOf(false) }
    var protocolExpanded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val trimmedBase = baseUrl.trim()
        if (name.isBlank() || modelId.isBlank() || !isValidBaseUrl(trimmedBase)) {
            error = "Name, model ID, and an absolute HTTP or HTTPS base URL are required."
            return
        }
        val output = LlmTokenFormat.parse(maxOutput)
        val context = LlmTokenFormat.parse(maxContext)
        if (output == null || context == null) {
            error = "Token limits must be positive values such as 16k, 256k, or 1000000."
            return
        }
        val trimmedKey = apiKey.trim()
        val keyValue: String? = when {
            model == null -> trimmedKey
            clearSavedKey -> ""
            else -> trimmedKey.ifEmpty { null }
        }
        val input = LlmModelInput(
            name = name.trim(),
            modelId = modelId.trim(),
            baseUrl = trimmedBase,
            protocol = protocol.raw,
            maxOutputTokens = output,
            maxContextTokens = context,
            apiKey = keyValue,
        )
        isSaving = true
        error = null
        onSave(model?.id, input) { err ->
            isSaving = false
            if (err == null) onDismiss() else error = err
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (model == null) "Add Model" else "Edit Model") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, enabled = !isSaving) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = { submit() },
                                enabled = name.isNotBlank() && modelId.isNotBlank() && !isSaving,
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (model?.isSharedCopy == true) {
                        Text(
                            text = "This is an independent copy shared by " +
                                "${model.sharedFromName.ifEmpty { "another user" }}. It cannot be shared onward.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 120) name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { if (it.length <= 255) modelId = it },
                        label = { Text("Model ID") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { if (it.length <= 2048) baseUrl = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Protocol picker
                    Box {
                        OutlinedTextField(
                            value = "${protocol.label} · ${protocol.details}",
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isSaving,
                            label = { Text("Protocol") },
                            trailingIcon = { Icon(Icons.Outlined.UnfoldMore, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(enabled = !isSaving) { protocolExpanded = true },
                        )
                        DropdownMenu(expanded = protocolExpanded, onDismissRequest = { protocolExpanded = false }) {
                            LlmProtocol.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(option.label)
                                            Text(
                                                option.details,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        protocolExpanded = false
                                        val known = LlmProtocol.defaultBaseUrls
                                        if (baseUrl.trim().isEmpty() || baseUrl in known) {
                                            baseUrl = option.defaultBaseUrl
                                        }
                                        protocol = option
                                    },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            if (it.length <= 4096) apiKey = it
                            if (it.isNotEmpty()) clearSavedKey = false
                        },
                        label = { Text("Optional API key") },
                        singleLine = true,
                        enabled = !isSaving,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (model?.hasApiKey == true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Remove saved key", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = clearSavedKey,
                                onCheckedChange = { clearSavedKey = it; if (it) apiKey = "" },
                                enabled = !isSaving,
                            )
                        }
                    }
                    Text(
                        text = apiKeyFooter(model?.hasApiKey == true, clearSavedKey),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = maxOutput,
                        onValueChange = { maxOutput = it },
                        label = { Text("Max output tokens") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = maxContext,
                        onValueChange = { maxContext = it },
                        label = { Text("Max context tokens") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Examples: 16384, 256k, or 1m. Context is compacted as conversations approach the limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (error != null) {
                        Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun apiKeyFooter(hasKey: Boolean, clear: Boolean): String = when {
    hasKey && clear -> "The saved key will be removed when you save."
    hasKey -> "Leave blank to keep the saved key, or enter a replacement."
    else -> "The key is optional and will not be displayed after saving."
}

private fun isValidBaseUrl(value: String): Boolean {
    val lower = value.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    val afterScheme = value.substringAfter("://", "")
    val host = afterScheme.substringBefore("/").substringBefore("?")
    return host.isNotEmpty()
}
