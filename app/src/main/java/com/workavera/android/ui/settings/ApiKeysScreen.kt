package com.workavera.android.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.model.ApiKey
import com.workavera.android.data.model.ApiKeyExpiration
import com.workavera.android.data.model.CreatedApiKey
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.components.SelectMenu
import com.workavera.android.ui.components.SelectMenuItem
import com.workavera.android.ui.notifications.RelativeTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val keyDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/** iOS .orange, used for the one-time-secret warning. */
private val WarningOrange = Color(0xFFFF9500)

/**
 * API Keys management, mirroring the iOS APIKeysView: list of keys with revoke,
 * and a create flow that shows the full secret + MCP client config exactly once.
 * Presented as a full-screen dialog from the Settings Developer section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(onDismiss: () -> Unit) {
    val viewModel: ApiKeysViewModel = viewModel(factory = ApiKeysViewModel.Factory)
    val ui = viewModel.uiState
    var showCreate by remember { mutableStateOf(false) }
    var revokeTarget by remember { mutableStateOf<ApiKey?>(null) }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("API Keys") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showCreate = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Create API key")
                            }
                        },
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when {
                        !ui.hasLoaded && ui.isLoading -> LoadingIndicator("Loading API keys…")
                        ui.keys.isEmpty() && ui.error == null ->
                            EmptyState(onCreate = { showCreate = true })
                        else -> KeyList(
                            viewModel = viewModel,
                            onRevokeRequest = { revokeTarget = it },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateApiKeyDialog(
            serverAddress = viewModel.serverAddress,
            onCreate = viewModel::create,
            onDismiss = { showCreate = false },
        )
    }

    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revoke API key?") },
            text = {
                Text("The key will stop working immediately. Connected MCP clients will lose access.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revoke(target.id)
                        revokeTarget = null
                    },
                ) {
                    Text("Revoke \"${target.name}\"", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun KeyList(
    viewModel: ApiKeysViewModel,
    onRevokeRequest: (ApiKey) -> Unit,
) {
    val ui = viewModel.uiState
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ui.error != null) {
            item { ErrorBanner(message = ui.error, onRetry = viewModel::refresh) }
        }
        item {
            Text(
                text = "MCP Access",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }
        items(ui.keys, key = { it.id }) { key ->
            ApiKeyRow(
                key = key,
                isRevoking = key.id in ui.revokingIds,
                onRevoke = { onRevokeRequest(key) },
            )
        }
        item {
            Text(
                text = "Keys act on your behalf. The full secret is only shown once when created.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun ApiKeyRow(key: ApiKey, isRevoking: Boolean, onRevoke: () -> Unit) {
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
                    text = key.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (key.allowDestructive) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "ALLOWS DELETION",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                CircleShape,
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${key.prefix}…",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${expirationText(key)}  ·  ${lastUsedText(key)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (key.isExpired) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isRevoking) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onRevoke, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Revoke ${key.name}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                Icons.Outlined.Key,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No API Keys",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Create a key to connect MCP clients such as Claude Code or Cursor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreate) { Text("Create Key") }
    }
}

/** Create form; after success it switches to the one-time secret view. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateApiKeyDialog(
    serverAddress: String,
    onCreate: (String, ApiKeyExpiration, Boolean, (CreatedApiKey?, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var expiration by remember { mutableStateOf(ApiKeyExpiration.NEVER) }
    var allowDestructive by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<CreatedApiKey?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isCreating && created == null) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (created == null) "Create API Key" else "Save API Key") },
                        navigationIcon = {
                            if (created == null) {
                                IconButton(onClick = onDismiss, enabled = !isCreating) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                                }
                            }
                        },
                        actions = {
                            if (created == null) {
                                TextButton(
                                    onClick = {
                                        isCreating = true
                                        error = null
                                        onCreate(name, expiration, allowDestructive) { result, err ->
                                            isCreating = false
                                            if (result != null) created = result else error = err
                                        }
                                    },
                                    enabled = name.isNotBlank() && !isCreating,
                                ) {
                                    Text(if (isCreating) "Creating…" else "Create")
                                }
                            } else {
                                TextButton(onClick = onDismiss) { Text("Done") }
                            }
                        },
                    )
                },
            ) { padding ->
                val createdKey = created
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    if (createdKey == null) {
                        CreateForm(
                            name = name,
                            onNameChange = { if (it.length <= 100) name = it },
                            expiration = expiration,
                            onExpirationChange = { expiration = it },
                            allowDestructive = allowDestructive,
                            onAllowDestructiveChange = { allowDestructive = it },
                            enabled = !isCreating,
                            error = error,
                        )
                    } else {
                        CreatedKeyView(createdKey = createdKey, serverAddress = serverAddress)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateForm(
    name: String,
    onNameChange: (String) -> Unit,
    expiration: ApiKeyExpiration,
    onExpirationChange: (ApiKeyExpiration) -> Unit,
    allowDestructive: Boolean,
    onAllowDestructiveChange: (Boolean) -> Unit,
    enabled: Boolean,
    error: String?,
) {
    FormSectionTitle("Key Details")
    FormCard {
        TextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("e.g. Claude Code on my laptop") },
            singleLine = true,
            enabled = enabled,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        ExpirationRow(expiration = expiration, enabled = enabled, onSelect = onExpirationChange)
    }
    Spacer(Modifier.height(20.dp))

    FormCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Allow destructive actions",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = allowDestructive,
                onCheckedChange = onAllowDestructiveChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.error,
                    checkedBorderColor = Color.Transparent,
                ),
            )
        }
    }
    FormFooter("Allows MCP clients to delete tasks or calendar events without another confirmation.")
    Spacer(Modifier.height(20.dp))

    // One-time-secret hint (iOS lock.shield label in its own section).
    FormCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "The full key is shown once. Workavera stores only its hash.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun FormSectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
internal fun FormFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 6.dp),
    )
}

/** Grouped card matching the Settings section style. */
@Composable
internal fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@Composable
private fun ExpirationRow(
    expiration: ApiKeyExpiration,
    enabled: Boolean,
    onSelect: (ApiKeyExpiration) -> Unit,
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
                "Expiration",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = expiration.label,
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
            ApiKeyExpiration.entries.forEach { option ->
                SelectMenuItem(
                    label = option.label,
                    selected = option == expiration,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun CreatedKeyView(createdKey: CreatedApiKey, serverAddress: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.GppMaybe,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = WarningOrange,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "This is the only time the full key is shown.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = WarningOrange,
        )
    }
    Spacer(Modifier.height(20.dp))
    CopyCard(title = "API Key", value = createdKey.key, toast = "API key copied")
    Spacer(Modifier.height(16.dp))
    CopyCard(
        title = "MCP Client Configuration",
        value = mcpConfiguration(serverAddress, createdKey.key),
        toast = "MCP configuration copied",
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Paste the configuration into .mcp.json for Claude Code or mcp.json for Cursor.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CopyCard(title: String, value: String, toast: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { copyToClipboard(context, title, value, toast) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun expirationText(key: ApiKey): String {
    val expiresAt = key.expiresAt ?: return "Never expires"
    val date = keyDateFormat.format(expiresAt.atZone(ZoneId.systemDefault()))
    return if (key.isExpired) "Expired $date" else "Expires $date"
}

private fun lastUsedText(key: ApiKey): String =
    if (key.lastUsedAt == null) "Never used" else "Last used ${RelativeTime.format(key.lastUsed)}"

private fun mcpConfiguration(serverAddress: String, key: String): String {
    val base = serverAddress.trim().trimEnd('/')
    return """
        {
          "mcpServers": {
            "workavera": {
              "type": "http",
              "url": "$base/api/mcp",
              "headers": {
                "Authorization": "Bearer $key"
              }
            }
          }
        }
    """.trimIndent()
}

private fun copyToClipboard(context: Context, label: String, value: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}
