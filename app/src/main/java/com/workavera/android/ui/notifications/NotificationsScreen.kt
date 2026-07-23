package com.workavera.android.ui.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workavera.android.data.model.AppNotification
import com.workavera.android.data.model.AppNotificationType
import com.workavera.android.data.model.ModelShareStatus
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.ExpressivePullToRefreshBox
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.components.PaginationBar
import com.workavera.android.ui.theme.StatusOnline

/**
 * Notifications screen, mirroring the iOS NotificationsView: card list (tap to
 * mark read) + per-row menu (archive / delete with confirmation) + model-share
 * accept/reject + bottom pager. Realtime SSE updates are driven by the shared
 * [NotificationViewModel], which HomeScaffold keeps subscribed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: NotificationViewModel) {
    val ui = viewModel.uiState
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ExpressivePullToRefreshBox(
            isRefreshing = ui.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            when {
                !ui.hasLoaded && ui.isLoading -> LoadingIndicator("Loading notifications…")
                ui.items.isEmpty() && !ui.isLoading -> EmptyState(onRefresh = { viewModel.refresh() })
                else -> NotificationList(
                    ui = ui,
                    viewModel = viewModel,
                    onDeleteRequest = { pendingDeleteId = it },
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        PaginationBar(
            page = ui.page,
            totalPages = ui.totalPages,
            onPageChange = viewModel::loadPage,
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete Notification?") },
            text = {
                Text("This notification will be permanently deleted. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId = null
                        viewModel.delete(id)
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun NotificationList(
    ui: NotificationUiState,
    viewModel: NotificationViewModel,
    onDeleteRequest: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ui.error != null) {
            item {
                ErrorBanner(message = ui.error, onRetry = { viewModel.refresh() })
            }
        }
        if (ui.unreadCount > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${ui.unreadCount} unread",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::markAllRead) {
                        Text("Mark All Read")
                    }
                }
            }
        }
        items(ui.items, key = { it.id }) { notification ->
            NotificationCard(
                notification = notification,
                isManaging = notification.id in ui.managingIds,
                isResponding = notification.id in ui.respondingShareIds,
                onMarkRead = { viewModel.markRead(notification.id) },
                onArchive = { viewModel.archive(notification.id) },
                onDelete = { onDeleteRequest(notification.id) },
                onRespond = { accept -> viewModel.respondToModelShare(notification.id, accept) },
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    isManaging: Boolean,
    isResponding: Boolean,
    onMarkRead: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onRespond: (Boolean) -> Unit,
) {
    Card(
        onClick = onMarkRead,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TypeIcon(
                type = notification.notificationType,
                unread = !notification.isRead,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (notification.pinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (notification.body.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = notification.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = RelativeTime.format(notification.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (notification.notificationType == AppNotificationType.MODEL_SHARE) {
                    ModelShareBlock(
                        notification = notification,
                        isResponding = isResponding,
                        onRespond = onRespond,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isManaging) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                NotificationMenu(onArchive = onArchive, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun TypeIcon(type: AppNotificationType, unread: Boolean) {
    val icon: ImageVector = when (type) {
        AppNotificationType.MODEL_SHARE -> Icons.Outlined.IosShare
        AppNotificationType.TASK_DUE -> Icons.Outlined.Checklist
        AppNotificationType.CALENDAR_EVENT -> Icons.Outlined.DateRange
        AppNotificationType.UNKNOWN -> Icons.Outlined.Notifications
    }
    Box {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (unread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-3).dp, y = (-3).dp)
                    .size(9.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
        }
    }
}

@Composable
private fun NotificationMenu(onArchive: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "Notification actions",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Archive") },
                leadingIcon = {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    expanded = false
                    onArchive()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** Action area of a model-share notification: accept/reject while pending, the outcome afterwards. */
@Composable
private fun ModelShareBlock(
    notification: AppNotification,
    isResponding: Boolean,
    onRespond: (Boolean) -> Unit,
) {
    val data = notification.data
    Column(modifier = Modifier.padding(top = 10.dp)) {
        if (data.modelShareStatus == ModelShareStatus.PENDING) {
            if (!data.modelName.isNullOrEmpty()) {
                Text(
                    text = data.modelName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
            }
            if (!data.senderName.isNullOrEmpty()) {
                Text(
                    text = "Shared by ${data.senderName}. Accepting creates an independent model configuration.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onRespond(true) },
                    enabled = !isResponding,
                ) {
                    Text("Accept")
                }
                OutlinedButton(
                    onClick = { onRespond(false) },
                    enabled = !isResponding,
                ) {
                    Text("Reject")
                }
            }
        } else {
            val accepted = data.modelShareStatus == ModelShareStatus.ACCEPTED
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (accepted) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (accepted) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (accepted) "Model configuration added" else "Share invitation declined",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                Icons.Outlined.NotificationsOff,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No Notifications",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "New task reminders, calendar events, and model shares will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onRefresh) {
            Icon(
                Icons.AutoMirrored.Outlined.Undo,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Refresh")
        }
    }
}
