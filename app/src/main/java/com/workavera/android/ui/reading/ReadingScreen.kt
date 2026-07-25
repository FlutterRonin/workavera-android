package com.workavera.android.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workavera.android.data.model.ReadingItem
import com.workavera.android.data.model.ReadingListFilter
import com.workavera.android.data.model.ReadingStatus
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.ExpressivePullToRefreshBox
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.components.PaginationBar
import com.workavera.android.ui.components.SearchTextField
import com.workavera.android.ui.notifications.RelativeTime

/**
 * Trigger for the reading action that lives in the home top bar (the iOS
 * toolbar plus button). The button is rendered by HomeScaffold; the sheet it
 * opens stays here. Mark-all-read is invoked on the hoisted view model
 * directly so the top bar can reflect unreadCount / isMarkingAllRead.
 */
class ReadingTopBarState {
    var addRequests by mutableStateOf(0)
        private set

    fun requestAdd() {
        addRequests++
    }
}

/**
 * Reading list, mirroring the iOS ReadingView: filter chips, search-on-submit,
 * a summary line, paged item cards with pin / status / delete menus, and
 * add / detail sheets.
 */
@Composable
fun ReadingScreen(
    viewModel: ReadingViewModel,
    topBarState: ReadingTopBarState,
) {
    val ui = viewModel.uiState
    var searchInput by remember { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<ReadingItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ReadingItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
        searchInput = viewModel.uiState.query
    }

    // Add button in the home top bar: each request opens the add sheet.
    LaunchedEffect(topBarState.addRequests) {
        if (topBarState.addRequests > 0) showAddSheet = true
    }

    ExpressivePullToRefreshBox(
        isRefreshing = ui.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!ui.hasLoaded && ui.isLoading) {
            LoadingIndicator("Loading reading list…")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ui.error != null) {
                    item { ErrorBanner(message = ui.error, onRetry = { viewModel.refresh() }) }
                }
                item {
                    SearchTextField(
                        value = searchInput,
                        onValueChange = { newValue ->
                            val oldValue = searchInput
                            searchInput = newValue
                            // Clearing the field clears an active server-side search.
                            if (oldValue.isNotEmpty() && newValue.isEmpty() && ui.query.isNotEmpty()) {
                                viewModel.search("")
                            }
                        },
                        placeholder = "Search title, URL, tags or summary",
                        onSearch = { viewModel.search(searchInput) },
                    )
                }
                item {
                    ReadingFilterRow(selected = ui.filter, onSelect = viewModel::selectFilter)
                }
                item {
                    Text(
                        text = "${ui.totalItems} " +
                            (if (ui.filter == ReadingListFilter.ARCHIVED) "archived" else "matching") +
                            " · ${ui.unreadCount} unread",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                if (ui.items.isEmpty()) {
                    item {
                        EmptyReading(
                            filter = ui.filter,
                            hasQuery = ui.query.isNotEmpty(),
                            onAdd = { showAddSheet = true },
                        )
                    }
                } else {
                    items(ui.items, key = { it.id }) { readingItem ->
                        ReadingCard(
                            item = readingItem,
                            isBusy = readingItem.id in ui.mutatingItemIds,
                            onClick = { selectedItem = readingItem },
                            onTogglePin = { viewModel.togglePin(readingItem) },
                            onSetStatus = { status -> viewModel.updateStatus(readingItem, status) },
                            onDelete = { deleteTarget = readingItem },
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
    }

    if (showAddSheet) {
        AddReadingItemSheet(viewModel = viewModel, onDismiss = { showAddSheet = false })
    }

    selectedItem?.let { item ->
        ReadingDetailSheet(
            initialItem = item,
            viewModel = viewModel,
            onDismiss = { selectedItem = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete reading item?") },
            text = {
                Text("“${target.title}” will be permanently removed. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target) { success ->
                            if (success) deleteTarget = null
                        }
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

/** Horizontally scrolling capsule chips, one per iOS ReadingListFilter. */
@Composable
private fun ReadingFilterRow(
    selected: ReadingListFilter,
    onSelect: (ReadingListFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReadingListFilter.entries.forEach { filter ->
            ReadingFilterChip(
                filter = filter,
                selected = selected == filter,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun ReadingFilterChip(
    filter: ReadingListFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            filterIcon(filter),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = contentColor,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = filter.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
        )
    }
}

private fun filterIcon(filter: ReadingListFilter): ImageVector = when (filter) {
    ReadingListFilter.ACTIVE -> Icons.AutoMirrored.Outlined.MenuBook
    ReadingListFilter.UNREAD -> Icons.Filled.Circle
    ReadingListFilter.READ -> Icons.Outlined.CheckCircle
    ReadingListFilter.PINNED -> Icons.Outlined.PushPin
    ReadingListFilter.ARCHIVED -> Icons.Outlined.Archive
}

/** One reading item card with its actions menu (iOS readingCard + itemMenu). */
@Composable
private fun ReadingCard(
    item: ReadingItem,
    isBusy: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onSetStatus: (ReadingStatus) -> Unit,
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
            Text(
                text = item.host.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.pinned) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${item.host} · ${item.readingStatus.label} · ${RelativeTime.format(item.updated)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isBusy) {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        } else {
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "Item actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (item.readingStatus != ReadingStatus.ARCHIVED) {
                        DropdownMenuItem(
                            text = { Text(if (item.pinned) "Unpin" else "Pin") },
                            leadingIcon = {
                                Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = { menuExpanded = false; onTogglePin() },
                        )
                        if (item.readingStatus == ReadingStatus.UNREAD) {
                            DropdownMenuItem(
                                text = { Text("Mark as Read") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = { menuExpanded = false; onSetStatus(ReadingStatus.READ) },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Mark as Unread") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Circle, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = { menuExpanded = false; onSetStatus(ReadingStatus.UNREAD) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = { menuExpanded = false; onSetStatus(ReadingStatus.ARCHIVED) },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Restore as Unread") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = { menuExpanded = false; onSetStatus(ReadingStatus.UNREAD) },
                        )
                    }
                    HorizontalDivider()
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
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** Empty state per filter (iOS emptyDescription), with an Add Link action on the default view. */
@Composable
private fun EmptyReading(
    filter: ReadingListFilter,
    hasQuery: Boolean,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            filterIcon(filter),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Nothing Here Yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = emptyDescription(filter, hasQuery),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (filter == ReadingListFilter.ACTIVE && !hasQuery) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) { Text("Add Link") }
        }
    }
}

private fun emptyDescription(filter: ReadingListFilter, hasQuery: Boolean): String {
    if (hasQuery) return "Try another search."
    return when (filter) {
        ReadingListFilter.ACTIVE -> "Add a link to start your reading library."
        ReadingListFilter.UNREAD -> "No unread items remain."
        ReadingListFilter.READ -> "Items you mark as read will appear here."
        ReadingListFilter.PINNED -> "Pin important references for quick access."
        ReadingListFilter.ARCHIVED -> "Archived items will appear here."
    }
}
