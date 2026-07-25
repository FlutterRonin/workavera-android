package com.workavera.android.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workavera.android.data.model.ChatConversation
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.ExpressivePullToRefreshBox
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.components.PaginationBar
import com.workavera.android.ui.components.SearchTextField
import com.workavera.android.ui.notifications.RelativeTime

/**
 * Chat top-bar actions (iOS toolbar archivebox / square.and.pencil), hoisted
 * so the Home top bar can trigger them while the sheets live in this screen.
 */
class ChatTopBarState {
    var createRequests by mutableStateOf(0)
        private set
    var showArchived by mutableStateOf(false)

    /**
     * True while the conversation detail is open; the top bar hides the
     * archive/new actions then — they belong to the list only.
     */
    var detailOpen by mutableStateOf(false)

    fun requestCreate() {
        createRequests++
    }
}

/**
 * Conversation list, mirroring the iOS ChatView: search-on-submit, paged rows
 * with pin / rename / archive / delete menus, an archived sheet, and
 * navigation into the conversation detail.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    topBarState: ChatTopBarState,
) {
    val ui = viewModel.uiState
    var searchInput by remember { mutableStateOf("") }
    var openConversationId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatConversation?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadForPresentation()
        viewModel.loadModelsIfNeeded()
        searchInput = viewModel.uiState.query
    }
    LaunchedEffect(topBarState.createRequests) {
        if (topBarState.createRequests == 0) return@LaunchedEffect
        searchInput = ""
        viewModel.create { created -> openConversationId = created.id }
    }
    SideEffect {
        topBarState.detailOpen = openConversationId != null
    }

    val openId = openConversationId
    if (openId != null) {
        ChatConversationView(
            conversationId = openId,
            viewModel = viewModel,
            onBack = {
                openConversationId = null
                // Refresh the list only when returning from a conversation.
                viewModel.refresh()
            },
        )
        return
    }

    ExpressivePullToRefreshBox(
        isRefreshing = ui.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!ui.hasLoaded && ui.isLoading) {
            LoadingIndicator("Loading conversations…")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ui.error != null) {
                    item {
                        ErrorBanner(message = ui.error, onRetry = viewModel::refresh)
                    }
                }
                item {
                    SearchTextField(
                        value = searchInput,
                        onValueChange = { newValue ->
                            val oldValue = searchInput
                            searchInput = newValue
                            if (oldValue.isNotEmpty() && newValue.isEmpty() && ui.query.isNotEmpty()) {
                                viewModel.search("")
                            }
                        },
                        placeholder = "Search conversations",
                        onSearch = { viewModel.search(searchInput) },
                    )
                }
                if (ui.conversations.isEmpty() && ui.hasLoaded) {
                    item {
                        ConversationListEmpty(
                            query = ui.query,
                            isCreating = ui.isCreating,
                            onCreate = {
                                searchInput = ""
                                viewModel.create { created -> openConversationId = created.id }
                            },
                            onClearSearch = {
                                searchInput = ""
                                viewModel.search("")
                            },
                        )
                    }
                }
                items(ui.conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        isGenerating = ui.isGenerating(conversation.id),
                        isMutating = conversation.id in ui.mutatingConversationIds,
                        onClick = { openConversationId = conversation.id },
                        onTogglePin = { viewModel.togglePin(conversation) },
                        onRename = { renameTarget = conversation },
                        onArchive = { viewModel.archive(conversation) },
                        onDelete = { deleteTarget = conversation },
                    )
                }
                if (ui.hasLoaded && ui.totalItems > 0) {
                    item {
                        PaginationBar(
                            page = ui.page,
                            totalPages = ui.totalPages,
                            onPageChange = viewModel::loadPage,
                        )
                    }
                }
            }
        }
    }

    if (topBarState.showArchived) {
        ArchivedConversationsSheet(
            viewModel = viewModel,
            onDismiss = { topBarState.showArchived = false },
            onDelete = { deleteTarget = it },
        )
    }

    renameTarget?.let { target ->
        var renameDraft by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Title") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(target, renameDraft)
                        renameTarget = null
                    },
                    enabled = renameDraft.trim().isNotEmpty(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete conversation?") },
            text = {
                Text("This permanently deletes ${target.title} and all of its messages.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target) { ok -> if (ok) deleteTarget = null }
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
private fun ConversationListEmpty(
    query: String,
    isCreating: Boolean,
    onCreate: () -> Unit,
    onClearSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (query.isEmpty()) Icons.AutoMirrored.Outlined.Chat else Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (query.isEmpty()) "No Conversations" else "No Matches",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (query.isEmpty()) {
                "Create a conversation to start working with your assistant."
            } else {
                "No conversations match “$query”."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (query.isEmpty()) {
            Button(onClick = onCreate, enabled = !isCreating) {
                Text("New Conversation")
            }
        } else {
            OutlinedButton(onClick = onClearSearch) {
                Text("Clear Search")
            }
        }
    }
}

/** One conversation row with its actions menu (iOS conversationRow + conversationMenu). */
@Composable
private fun ConversationRow(
    conversation: ChatConversation,
    isGenerating: Boolean,
    isMutating: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Chat,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conversation.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (isGenerating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Responding",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    text = "${conversation.activityText()} · ${conversation.messageSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isMutating) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(6.dp)
                    .size(16.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "Conversation actions",
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (conversation.pinned) "Unpin" else "Pin") },
                        leadingIcon = {
                            Icon(Icons.Outlined.PushPin, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onTogglePin()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Archive") },
                        leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onArchive()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

internal fun ChatConversation.activityText(): String =
    RelativeTime.format(lastMessageAt.ifEmpty { updated })

/** Archived conversations sheet: restore / delete with its own paging (iOS ArchivedConversationsView). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedConversationsSheet(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onDelete: (ChatConversation) -> Unit,
) {
    val ui = viewModel.uiState

    LaunchedEffect(Unit) { viewModel.loadArchived() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Archived",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (ui.archivedError != null) {
                ErrorBanner(
                    message = ui.archivedError,
                    onRetry = { viewModel.loadArchived() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (!ui.hasLoadedArchived && ui.isLoadingArchived) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (ui.archivedConversations.isEmpty()) {
                Text(
                    "No archived conversations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ui.archivedConversations, key = { it.id }) { conversation ->
                        ArchivedConversationRow(
                            conversation = conversation,
                            isMutating = conversation.id in ui.mutatingConversationIds,
                            onRestore = { viewModel.unarchive(conversation) },
                            onDelete = { onDelete(conversation) },
                        )
                    }
                    if (ui.archivedTotalItems > 0) {
                        item {
                            PaginationBar(
                                page = ui.archivedPage,
                                totalPages = ui.archivedTotalPages,
                                onPageChange = { viewModel.loadArchived(requestedPage = it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedConversationRow(
    conversation: ChatConversation,
    isMutating: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Archive,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${conversation.activityText()} · ${conversation.messageSummary}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isMutating) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(6.dp)
                    .size(16.dp),
                strokeWidth = 2.dp,
            )
        } else {
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Outlined.Unarchive,
                    contentDescription = "Restore",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
