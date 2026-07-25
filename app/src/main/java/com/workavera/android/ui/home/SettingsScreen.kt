package com.workavera.android.ui.home

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BackHand
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.BuildConfig
import com.workavera.android.data.AuthState
import com.workavera.android.data.model.AppAppearance
import com.workavera.android.ui.AuthViewModel
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.settings.ApiKeysScreen
import com.workavera.android.ui.settings.ModelConfigurationsScreen
import com.workavera.android.ui.settings.ModelSharingScreen
import com.workavera.android.ui.settings.SavedMemoriesScreen

private const val PRIVACY_POLICY_URL =
    "https://github.com/xusenlin/workavera-ios/blob/main/docs/PrivacyPolicy.md"
private const val REPOSITORY_URL = "https://github.com/xusenlin/workavera"
private const val REPOSITORY_LABEL = "xusenlin/workavera"

/**
 * Settings screen, mirroring the iOS SettingsView sections: Server, Large
 * Language Model (Model Configurations + Model Sharing), Developer (API Keys),
 * Memory, Appearance, Account (sign out + delete), and About.
 */
@Composable
fun SettingsScreen(state: AuthState.LoggedIn, authViewModel: AuthViewModel) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val ui = viewModel.uiState
    val context = LocalContext.current

    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showApiKeys by remember { mutableStateOf(false) }
    var showSavedMemories by remember { mutableStateOf(false) }
    var showModelConfigs by remember { mutableStateOf(false) }
    var showModelSharing by remember { mutableStateOf(false) }

    val openUrl: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        ) {
            if (ui.error != null) {
                ErrorBanner(message = ui.error, onRetry = viewModel::dismissError)
                Spacer(Modifier.height(16.dp))
            }

            SettingsGroup(title = "Server") {
                SettingsRow(icon = Icons.Outlined.Dns, title = "Address") {
                    Text(
                        text = state.server,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            SettingsGroup(
                title = "Large Language Model",
                footer = "Add at least one model configuration to use Chat and AI summaries.",
            ) {
                NavRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Model Configurations",
                    onClick = { showModelConfigs = true },
                )
                SettingsDivider()
                NavRow(
                    icon = Icons.Outlined.IosShare,
                    title = "Model Sharing",
                    onClick = { showModelSharing = true },
                )
            }
            Spacer(Modifier.height(20.dp))

            SettingsGroup(
                title = "Developer",
                footer = "Connect third-party MCP clients to your Workavera tools.",
            ) {
                NavRow(
                    icon = Icons.Outlined.Key,
                    title = "API Keys",
                    onClick = { showApiKeys = true },
                )
            }
            Spacer(Modifier.height(20.dp))

            SettingsGroup(
                title = "Memory",
                footer = "Reuse facts and preferences you allow across chat sessions. " +
                    "Off by default.",
            ) {
                SwitchRow(
                    icon = Icons.Outlined.Psychology,
                    title = "Use Chat Memory",
                    checked = memory.memoryEnabled,
                    onCheckedChange = viewModel::setMemoryEnabled,
                )
                SettingsDivider()
                SwitchRow(
                    icon = Icons.Outlined.AutoFixHigh,
                    title = "Automatic Capture",
                    checked = memory.memoryAutoCapture,
                    enabled = memory.memoryEnabled,
                    onCheckedChange = viewModel::setMemoryAutoCapture,
                )
                SettingsDivider()
                NavRow(
                    icon = Icons.Outlined.Archive,
                    title = "Saved Memories",
                    onClick = { showSavedMemories = true },
                )
            }
            Spacer(Modifier.height(20.dp))

            SettingsGroup(title = "Appearance") {
                AppearanceRow(
                    current = appearance,
                    onSelect = viewModel::updateAppearance,
                )
            }
            Spacer(Modifier.height(20.dp))

            SettingsGroup(title = "Account") {
                SettingsRow(icon = Icons.Outlined.Email, title = "Email") {
                    Text(
                        text = state.user.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SettingsDivider()
                ActionRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = "Sign Out",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showSignOutConfirm = true },
                )
            }
            Spacer(Modifier.height(20.dp))

            SettingsGroup(title = "About") {
                SettingsRow(icon = Icons.Outlined.Info, title = "Version") {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsDivider()
                LinkRow(
                    icon = Icons.Outlined.BackHand,
                    title = "Privacy Policy",
                    onClick = { openUrl(PRIVACY_POLICY_URL) },
                )
                SettingsDivider()
                LinkRow(
                    icon = Icons.Outlined.Link,
                    title = "GitHub",
                    value = REPOSITORY_LABEL,
                    onClick = { openUrl(REPOSITORY_URL) },
                )
            }
        }
    }

    if (showModelConfigs) {
        ModelConfigurationsScreen(onDismiss = { showModelConfigs = false })
    }
    if (showModelSharing) {
        ModelSharingScreen(onDismiss = { showModelSharing = false })
    }
    if (showApiKeys) {
        ApiKeysScreen(onDismiss = { showApiKeys = false })
    }
    if (showSavedMemories) {
        SavedMemoriesScreen(
            memoryEnabled = memory.memoryEnabled,
            onDismiss = { showSavedMemories = false },
        )
    }

    if (showSignOutConfirm) {
        ConfirmDialog(
            title = "Sign out of Workavera?",
            message = "Your session will be removed from this device.",
            confirmLabel = "Sign Out",
            onConfirm = {
                showSignOutConfirm = false
                authViewModel.logout()
            },
            onDismiss = { showSignOutConfirm = false },
        )
    }
}

@Composable
private fun AppearanceRow(
    current: AppAppearance,
    onSelect: (AppAppearance) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(Icons.Outlined.Brightness6)
            Spacer(Modifier.width(14.dp))
            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = current.label,
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppAppearance.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = {
                        if (option == current) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
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
private fun SettingsGroup(
    title: String,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 6.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
    if (footer != null) {
        Spacer(Modifier.height(6.dp))
        SettingsFooter(footer)
    }
}

@Composable
private fun SettingsFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 48.dp),
    )
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = tint,
    )
}

/** Static row: leading icon + title on the left, trailing [value] content on the right. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        value()
    }
}

/** Tappable row with a leading icon and a trailing chevron, opening a sub-screen. */
@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** External-link row: leading icon + title, optional [value] + open-in-new icon on the right. */
@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
