package com.workavera.android.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.model.McpApprovalPolicy
import com.workavera.android.data.model.McpRefreshReport
import com.workavera.android.data.model.McpRefreshResult
import com.workavera.android.data.model.McpServer
import com.workavera.android.data.model.McpTool
import com.workavera.android.data.model.McpTransport
import com.workavera.android.data.model.parseMcpHeaders
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.components.SelectMenu
import com.workavera.android.ui.components.SelectMenuItem
import com.workavera.android.ui.theme.StatusOnline

/** Amber warning tone for "may be out of date" hints (iOS .orange family). */
private val WarningAmber = Color(0xFFD97706)

private const val TOOL_CHIP_LIMIT = 8

/**
 * MCP Servers management, mirroring the Web McpServersCard: a list with
 * per-server enable toggles, an add/edit form, a refresh + tool review view,
 * and removal. Presented as a full-screen dialog from the Settings Developer
 * section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServersScreen(onDismiss: () -> Unit) {
    val viewModel: McpServersViewModel = viewModel(factory = McpServersViewModel.Factory)
    val ui = viewModel.uiState
    var showCreate by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf<McpServer?>(null) }
    var reviewServer by remember { mutableStateOf<McpServer?>(null) }
    var deleteServer by remember { mutableStateOf<McpServer?>(null) }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("MCP Servers") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showCreate = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add MCP server")
                            }
                        },
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when {
                        !ui.hasLoaded && ui.isLoading -> LoadingIndicator("Loading MCP servers…")
                        ui.servers.isEmpty() && ui.error == null ->
                            EmptyState(onCreate = { showCreate = true })
                        else -> ServerList(
                            viewModel = viewModel,
                            onReview = { reviewServer = it },
                            onEdit = { editServer = it },
                            onDeleteRequest = { deleteServer = it },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        McpServerSheet(
            server = null,
            onSave = { name, slug, transport, url, headers, policy, onComplete ->
                viewModel.create(name, slug, transport, url, headers.orEmpty(), policy, onComplete)
            },
            onDismiss = { showCreate = false },
        )
    }

    editServer?.let { server ->
        McpServerSheet(
            server = server,
            onSave = { name, slug, transport, url, headers, policy, onComplete ->
                // Credentials are write-only, so an untouched field must leave
                // the stored headers alone rather than replacing them with nothing.
                viewModel.update(server.id, name, url, transport, headers, policy, onComplete)
            },
            onDismiss = { editServer = null },
        )
    }

    reviewServer?.let { server ->
        ToolReviewSheet(
            // The sheet may outlive the refresh that changed this server, so it
            // keys its initial state off the list copy but stays on the id.
            server = ui.servers.firstOrNull { it.id == server.id } ?: server,
            onRefresh = { target, onComplete -> viewModel.refreshServer(target, onComplete) },
            onSave = { tools, onComplete -> viewModel.saveToolSelection(server, tools, onComplete) },
            onDismiss = { reviewServer = null },
        )
    }

    deleteServer?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteServer = null },
            title = { Text("Remove MCP server?") },
            text = {
                Text("\"${target.name}\" and its stored credentials will be deleted. " +
                    "Chat will stop offering its tools immediately.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target)
                        deleteServer = null
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteServer = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ServerList(
    viewModel: McpServersViewModel,
    onReview: (McpServer) -> Unit,
    onEdit: (McpServer) -> Unit,
    onDeleteRequest: (McpServer) -> Unit,
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
        items(ui.servers, key = { it.id }) { server ->
            ServerRow(
                server = server,
                isBusy = server.id in ui.busyIds,
                onToggle = { enabled -> viewModel.setEnabled(server, enabled) },
                onReview = { onReview(server) },
                onEdit = { onEdit(server) },
                onDelete = { onDeleteRequest(server) },
            )
        }
        item {
            Text(
                text = "Servers and their credentials are private to your account. " +
                    "Only tools you enable after a refresh are offered to Chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

private data class ServerStatus(val label: String, val tone: StatusTone)

private enum class StatusTone { ERROR, WARNING, MUTED, SUCCESS }

/**
 * Status wording mirrors the Web card: a credential or reachability problem
 * needs different action from a changed tool, so it is never described as one.
 */
private fun serverStatus(server: McpServer): ServerStatus = when {
    server.lastError.isNotEmpty() -> ServerStatus("Connection problem", StatusTone.ERROR)
    server.hasStaleTools -> ServerStatus("May need a refresh", StatusTone.WARNING)
    server.tools.isEmpty() -> ServerStatus("No tools loaded", StatusTone.MUTED)
    // Checked before the server's own switch: "select some tools" is the useful
    // next step, whereas "Disabled" would leave the user flipping a switch that
    // cannot change anything.
    server.enabledToolCount == 0 -> ServerStatus("No tools selected", StatusTone.MUTED)
    !server.enabled -> ServerStatus("Disabled", StatusTone.MUTED)
    else -> ServerStatus("${server.enabledToolCount} tools enabled", StatusTone.SUCCESS)
}

@Composable
private fun statusColor(tone: StatusTone): Color = when (tone) {
    StatusTone.ERROR -> MaterialTheme.colorScheme.error
    StatusTone.WARNING -> WarningAmber
    StatusTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
    StatusTone.SUCCESS -> StatusOnline
}

@Composable
private fun ServerRow(
    server: McpServer,
    isBusy: Boolean,
    onToggle: (Boolean) -> Unit,
    onReview: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = serverStatus(server)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor(status.tone),
                        modifier = Modifier
                            .background(statusColor(status.tone).copy(alpha = 0.1f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = server.url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isBusy) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            } else {
                Switch(
                    checked = server.enabled && server.enabledToolCount > 0,
                    // Enabling means "offer this server's tools to Chat", so it
                    // stays inoperable until at least one tool is selected.
                    enabled = server.enabledToolCount > 0,
                    onCheckedChange = onToggle,
                )
            }
        }
        if (server.lastError.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = server.lastError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        ToolChips(server)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onReview) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tools")
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit ${server.name}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove ${server.name}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Shows which tools a server actually contributes, so the enabled set is
 * readable without opening the review sheet. Only enabled tools are listed:
 * the rest are not offered to Chat, so naming them here would suggest a
 * capability the assistant does not have.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolChips(server: McpServer) {
    val enabled = server.tools.filter { it.enabled }
    if (enabled.isEmpty()) return
    val shown = enabled.take(TOOL_CHIP_LIMIT)
    val overflow = enabled.size - shown.size
    val idle = server.tools.size - enabled.size

    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shown.forEach { tool ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        if (tool.stale) {
                            WarningAmber.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (tool.stale) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tool.requiresApproval) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = "Asks before each call",
                        modifier = Modifier.size(12.dp),
                        tint = StatusOnline,
                    )
                }
            }
        }
        if (overflow > 0) {
            Text(
                text = "+$overflow more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (idle > 0) {
            Text(
                text = "· $idle not enabled",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
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
                Icons.Outlined.CloudQueue,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No MCP Servers",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add a server, refresh it, then choose which of its tools Chat may use.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreate) { Text("Add Server") }
    }
}

/** Add/edit form, mirroring the Web ServerSheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpServerSheet(
    server: McpServer?,
    onSave: (
        name: String,
        slug: String,
        transport: String,
        url: String,
        headers: Map<String, String>?,
        approvalPolicy: String,
        onComplete: (McpServer?, String?) -> Unit,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val editing = server != null
    var name by remember { mutableStateOf(server?.name ?: "") }
    var slug by remember { mutableStateOf(server?.slug ?: "") }
    var url by remember { mutableStateOf(server?.url ?: "") }
    var transport by remember { mutableStateOf(McpTransport.fromRaw(server?.transport ?: "http")) }
    var policy by remember { mutableStateOf(McpApprovalPolicy.fromRaw(server?.approvalPolicy ?: "writes")) }
    var headersText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSave = !saving && name.isNotBlank() && url.isNotBlank() && (editing || slug.isNotBlank())

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (editing) "Edit MCP Server" else "Add MCP Server") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, enabled = !saving) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    val parsed = parseMcpHeaders(headersText)
                                    if (parsed == null) {
                                        error = "Each header must be written as “Name: value”."
                                        return@TextButton
                                    }
                                    saving = true
                                    error = null
                                    // On edit an untouched field must leave the
                                    // stored credentials alone (write-only).
                                    val headers = if (editing && headersText.isBlank()) null else parsed
                                    onSave(name, slug, transport.raw, url, headers, policy.raw) { _, err ->
                                        saving = false
                                        if (err != null) {
                                            error = err
                                        } else {
                                            toast(
                                                context,
                                                if (editing) {
                                                    "MCP server updated"
                                                } else {
                                                    "MCP server added. Refresh it to load its tools."
                                                },
                                            )
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = canSave,
                            ) {
                                Text(if (saving) "Saving…" else if (editing) "Save" else "Add")
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
                    FormSectionTitle("Server")
                    FormCard {
                        FormTextField(
                            value = name,
                            onValueChange = { if (it.length <= 100) name = it },
                            placeholder = "Name, e.g. Notion",
                            enabled = !saving,
                        )
                        FormDivider()
                        FormTextField(
                            value = slug,
                            onValueChange = { if (it.length <= 20) slug = it },
                            placeholder = "Tool prefix, e.g. notion",
                            enabled = !saving && !editing,
                        )
                        FormDivider()
                        FormTextField(
                            value = url,
                            onValueChange = { url = it },
                            placeholder = "Endpoint URL, e.g. https://example.com/mcp",
                            enabled = !saving,
                        )
                    }
                    FormFooter(
                        "Tools appear to the assistant as mcp_${slug.ifBlank { "prefix" }}_toolname. " +
                            if (editing) {
                                "The prefix is fixed once tools exist, because changing it would " +
                                    "rename every tool the assistant knows."
                            } else {
                                "Lowercase letters, digits, and underscores."
                            },
                    )
                    Spacer(Modifier.height(20.dp))

                    FormCard {
                        SelectRow(
                            label = "Transport",
                            value = transport.label,
                            enabled = !saving,
                        ) { dismiss ->
                            McpTransport.entries.forEach { option ->
                                SelectMenuItem(
                                    label = option.label,
                                    selected = option == transport,
                                    onClick = {
                                        dismiss()
                                        transport = option
                                    },
                                )
                            }
                        }
                        FormDivider()
                        SelectRow(
                            label = "Approval for new tools",
                            value = policy.label,
                            enabled = !saving,
                        ) { dismiss ->
                            McpApprovalPolicy.entries.forEach { option ->
                                SelectMenuItem(
                                    label = option.label,
                                    supportingText = option.hint,
                                    selected = option == policy,
                                    onClick = {
                                        dismiss()
                                        policy = option
                                    },
                                )
                            }
                        }
                    }
                    FormFooter(
                        policy.hint + " This only pre-selects the setting when a refresh finds a " +
                            "new or changed tool; tools you already reviewed keep their setting.",
                    )
                    Spacer(Modifier.height(20.dp))

                    FormSectionTitle("Request Headers")
                    FormCard {
                        TextField(
                            value = headersText,
                            onValueChange = { headersText = it },
                            placeholder = { Text("Authorization: Bearer your-token") },
                            minLines = 3,
                            enabled = !saving,
                            colors = formTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    FormFooter(
                        "One per line, written as “Name: value”. These are your personal " +
                            "credentials for the upstream service, stored write-only and never shown again." +
                            if (editing) " Leave blank to keep the stored headers." else "",
                    )
                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Refresh + review view, mirroring the Web ToolReviewSheet: refresh fetches the
 * upstream diff (never enabling anything on its own), the user ticks tools and
 * sets per-tool approval, and Save applies the selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolReviewSheet(
    server: McpServer,
    onRefresh: (McpServer, (McpRefreshResult?, String?) -> Unit) -> Unit,
    onSave: (List<McpTool>, (McpServer?, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var tools by remember(server.id, server.updatedHash()) { mutableStateOf(server.tools) }
    var report by remember { mutableStateOf<McpRefreshReport?>(null) }
    var refreshError by remember { mutableStateOf(server.lastError) }
    var refreshing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun updateTool(name: String, patch: (McpTool) -> McpTool) {
        tools = tools.map { if (it.name == name) patch(it) else it }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, enabled = !saving) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    saving = true
                                    onSave(tools) { _, err ->
                                        saving = false
                                        if (err != null) {
                                            toast(context, err)
                                        } else {
                                            toast(context, "Tool selection saved")
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = !saving && !refreshing && tools.isNotEmpty(),
                            ) {
                                Text(if (saving) "Saving…" else "Save")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                refreshing = true
                                onRefresh(server) { result, err ->
                                    refreshing = false
                                    when {
                                        err != null -> {
                                            refreshError = err
                                            toast(context, err)
                                        }
                                        result == null -> Unit
                                        !result.ok -> {
                                            val message = result.error ?: "Could not reach the server"
                                            refreshError = message
                                            toast(context, message)
                                        }
                                        else -> {
                                            refreshError = ""
                                            tools = result.server.tools
                                            report = result.report
                                            val changes = (result.report?.added?.size ?: 0) +
                                                (result.report?.changed?.size ?: 0) +
                                                (result.report?.removed?.size ?: 0)
                                            toast(
                                                context,
                                                if (changes == 0) {
                                                    "No changes since the last refresh"
                                                } else {
                                                    "Review the changes, then choose which tools to enable"
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = !refreshing && !saving,
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (refreshing) "Refreshing…" else "Refresh from server")
                        }
                        report?.takeIf { it.removed.isNotEmpty() }?.let {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "${it.removed.size} removed upstream",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (refreshError.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(12.dp),
                        ) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = refreshError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    report?.takeIf { it.unsupported.isNotEmpty() }?.let {
                        Text(
                            text = "Not supported: ${it.unsupported.joinToString(", ")}. " +
                                "Their input schemas use references Workavera cannot resolve.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (tools.isEmpty()) {
                        Text(
                            text = "No tools loaded yet. Refresh to fetch them from the server.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(tools, key = { it.name }) { tool ->
                                ToolReviewRow(
                                    tool = tool,
                                    status = reviewStatus(tool, report),
                                    onEnabledChange = { enabled ->
                                        updateTool(tool.name) { it.copy(enabled = enabled) }
                                    },
                                    onApprovalChange = { always ->
                                        updateTool(tool.name) {
                                            it.copy(approval = if (always) "always" else "never")
                                        }
                                    },
                                )
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(start = 52.dp),
                                )
                            }
                            item {
                                Text(
                                    text = "Only the tools you enable here are offered to Chat. " +
                                        "Refreshing never enables anything on its own.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun reviewStatus(tool: McpTool, report: McpRefreshReport?): String? = when {
    report?.added?.contains(tool.name) == true -> "New"
    report?.changed?.contains(tool.name) == true -> "Changed"
    tool.stale -> "May be out of date"
    else -> null
}

@Composable
private fun ToolReviewRow(
    tool: McpTool,
    status: String?,
    onEnabledChange: (Boolean) -> Unit,
    onApprovalChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Checkbox(
            checked = tool.enabled,
            onCheckedChange = { onEnabledChange(it == true) },
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f, fill = false),
                )
                status?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber,
                        modifier = Modifier
                            .background(WarningAmber.copy(alpha = 0.1f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (tool.description.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tool.enabled) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = tool.requiresApproval,
                        onCheckedChange = onApprovalChange,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ask before each call",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---- Shared form pieces (mirroring the ApiKeys create form chrome) ----

@Composable
private fun formTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        enabled = enabled,
        colors = formTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FormDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** Select row ("label · value · ⌄") opening a [SelectMenu] of options. */
@Composable
private fun SelectRow(
    label: String,
    value: String,
    enabled: Boolean,
    options: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
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
            options { expanded = false }
        }
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** Local change key for reseeding the review sheet's tool state after a reload. */
private fun McpServer.updatedHash(): Int =
    tools.fold(lastError.hashCode()) { acc, tool ->
        31 * acc + tool.hash.hashCode() + tool.name.hashCode()
    }
