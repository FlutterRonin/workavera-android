package com.workavera.android.ui.docs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.model.DocsViewFilter
import com.workavera.android.data.model.DocumentKind
import com.workavera.android.data.model.DocumentStatus
import com.workavera.android.data.model.DocumentSummary
import com.workavera.android.ui.components.ActionMenu
import com.workavera.android.ui.components.ActionMenuDivider
import com.workavera.android.ui.components.ActionMenuItem
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.ExpressivePullToRefreshBox
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.components.PaginationBar
import com.workavera.android.ui.components.SearchTextField
import com.workavera.android.ui.notifications.RelativeTime

/** HTML docs get a purple tint (iOS .purple); markdown uses the accent color. */
private val HtmlTint = Color(0xFF8B5CF6)

/**
 * Docs browser, mirroring the iOS DocsView: a location header opening the
 * browse switcher, a search-on-submit list of document cards with pin /
 * archive / delete menus, and pagination for the paged views.
 */
@Composable
fun DocsScreen() {
    val viewModel: DocsViewModel = viewModel(factory = DocsViewModel.Factory)
    val ui = viewModel.uiState
    var searchInput by remember { mutableStateOf("") }
    var showLocationSwitcher by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DocumentSummary?>(null) }
    var detailDocumentId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
        searchInput = viewModel.uiState.query
    }

    ExpressivePullToRefreshBox(
        isRefreshing = ui.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!ui.hasLoaded && ui.isLoading) {
            LoadingIndicator("Loading documents…")
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
                        placeholder = "Search documents",
                        onSearch = { viewModel.search(searchInput) },
                    )
                }
                item {
                    LocationHeader(
                        view = ui.view,
                        label = viewModel.label(),
                        totalItems = ui.totalItems,
                        onClick = { showLocationSwitcher = true },
                    )
                }

                if (ui.documents.isEmpty()) {
                    item {
                        EmptyDocuments(
                            hasQuery = ui.query.isNotEmpty(),
                            onRefresh = { viewModel.refresh() },
                            onClearSearch = {
                                searchInput = ""
                                viewModel.search("")
                            },
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = viewModel.label().uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${ui.totalItems} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(ui.documents, key = { it.id }) { document ->
                        DocumentCard(
                            document = document,
                            location = viewModel.locationName(document.projectId, document.folderId),
                            isBusy = document.id in ui.pinningIds || document.id in ui.mutatingIds,
                            onClick = { detailDocumentId = document.id },
                            onTogglePin = { viewModel.togglePin(document.id) },
                            onSetArchived = { viewModel.setArchived(document.id, it) },
                            onDelete = { deleteTarget = document },
                        )
                    }
                    if (ui.view.usesPagination && ui.hasLoaded && ui.totalItems > 0) {
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

    if (showLocationSwitcher) {
        LocationSwitcherSheet(
            viewModel = viewModel,
            onSelect = { viewModel.selectView(it) },
            onDismiss = { showLocationSwitcher = false },
        )
    }

    detailDocumentId?.let { id ->
        DocumentDetailDialog(
            documentId = id,
            viewModel = viewModel,
            onDismiss = { detailDocumentId = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete document?") },
            text = {
                Text("“${target.title}” will be permanently removed. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.id) { success ->
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

/** Tappable current-location card (iOS locationHeader). */
@Composable
private fun LocationHeader(
    view: DocsViewFilter,
    label: String,
    totalItems: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
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
                    locationIcon(view),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$totalItems documents",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.UnfoldMore,
                contentDescription = "Browse documents",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun locationIcon(view: DocsViewFilter): ImageVector = when (view) {
    DocsViewFilter.MyDocuments -> Icons.Outlined.Inbox
    is DocsViewFilter.Folder -> Icons.Outlined.Folder
    is DocsViewFilter.Project -> Icons.Outlined.Layers
    DocsViewFilter.Archived -> Icons.Outlined.Archive
    DocsViewFilter.Recent -> Icons.Outlined.Schedule
    DocsViewFilter.Pinned -> Icons.Outlined.PushPin
}

/** One document card with its actions menu (iOS DocumentRow + documentMenu). */
@Composable
private fun DocumentCard(
    document: DocumentSummary,
    location: String,
    isBusy: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val isHtml = document.documentKind == DocumentKind.HTML
    val tint = if (isHtml) HtmlTint else MaterialTheme.colorScheme.primary
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isHtml) Icons.Outlined.Code else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (document.pinned) {
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
                    text = documentMeta(document, location),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            contentDescription = "Document actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ActionMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (document.documentStatus == DocumentStatus.DRAFT) {
                            ActionMenuItem(
                                label = if (document.pinned) "Unpin" else "Pin",
                                icon = Icons.Outlined.PushPin,
                                onClick = { menuExpanded = false; onTogglePin() },
                            )
                            ActionMenuItem(
                                label = "Archive",
                                icon = Icons.Outlined.Archive,
                                onClick = { menuExpanded = false; onSetArchived(true) },
                            )
                        } else {
                            ActionMenuItem(
                                label = "Restore",
                                icon = Icons.AutoMirrored.Outlined.Undo,
                                onClick = { menuExpanded = false; onSetArchived(false) },
                            )
                        }
                        ActionMenuDivider()
                        ActionMenuItem(
                            label = "Delete",
                            icon = Icons.Outlined.Delete,
                            destructive = true,
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

/** Location, version, and last-updated time on a single line. */
private fun documentMeta(document: DocumentSummary, location: String): String {
    val parts = mutableListOf("$location · v${document.revision}")
    if (document.updatedAt != null) parts += RelativeTime.format(document.updated)
    return parts.joinToString(" · ")
}

@Composable
private fun EmptyDocuments(
    hasQuery: Boolean,
    onRefresh: () -> Unit,
    onClearSearch: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No Documents",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "No documents match this location and search.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (hasQuery) {
            OutlinedButton(onClick = onClearSearch) { Text("Clear Search") }
        } else {
            Button(onClick = onRefresh) { Text("Refresh") }
        }
    }
}

/** Browse-by-location sheet (iOS DocsLocationSwitcherSheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSwitcherSheet(
    viewModel: DocsViewModel,
    onSelect: (DocsViewFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val ui = viewModel.uiState
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SwitcherSection("Quick Access") {
                SwitcherRow(Icons.Outlined.Schedule, "Recent", ui.view == DocsViewFilter.Recent) {
                    onSelect(DocsViewFilter.Recent)
                    onDismiss()
                }
                SwitcherDivider()
                SwitcherRow(Icons.Outlined.PushPin, "Pinned", ui.view == DocsViewFilter.Pinned) {
                    onSelect(DocsViewFilter.Pinned)
                    onDismiss()
                }
            }

            SwitcherSection("Personal") {
                SwitcherRow(Icons.Outlined.Inbox, "My Documents", ui.view == DocsViewFilter.MyDocuments) {
                    onSelect(DocsViewFilter.MyDocuments)
                    onDismiss()
                }
                ui.folders.forEach { folder ->
                    SwitcherDivider()
                    SwitcherRow(
                        Icons.Outlined.Folder,
                        folder.name,
                        ui.view == DocsViewFilter.Folder(folder.id),
                        indented = true,
                    ) {
                        onSelect(DocsViewFilter.Folder(folder.id))
                        onDismiss()
                    }
                }
            }

            SwitcherSection("Projects") {
                if (ui.projects.isEmpty()) {
                    Text(
                        text = "No projects available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ProjectsPage(viewModel = viewModel, onSelect = onSelect, onDismiss = onDismiss)
                }
            }

            SwitcherSection("Other") {
                SwitcherRow(Icons.Outlined.Archive, "Archived", ui.view == DocsViewFilter.Archived) {
                    onSelect(DocsViewFilter.Archived)
                    onDismiss()
                }
            }
        }
    }
}

private const val PROJECTS_PER_PAGE = 4

/** Paged project rows inside the switcher (4 per page, like the iOS sheet). */
@Composable
private fun ProjectsPage(
    viewModel: DocsViewModel,
    onSelect: (DocsViewFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val ui = viewModel.uiState
    val pageCount = maxOf(1, (ui.projects.size + PROJECTS_PER_PAGE - 1) / PROJECTS_PER_PAGE)
    val selectedIndex = ui.projects.indexOfFirst { ui.view == DocsViewFilter.Project(it.id) }
        .coerceAtLeast(0)
    var page by remember { mutableIntStateOf(selectedIndex / PROJECTS_PER_PAGE) }
    val paged = ui.projects.drop(page * PROJECTS_PER_PAGE).take(PROJECTS_PER_PAGE)

    Column {
        paged.forEachIndexed { index, project ->
            if (index > 0) SwitcherDivider()
            SwitcherRow(
                Icons.Outlined.Layers,
                project.name,
                ui.view == DocsViewFilter.Project(project.id),
            ) {
                onSelect(DocsViewFilter.Project(project.id))
                onDismiss()
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PaginationBar(page = page, totalPages = pageCount, onPageChange = { page = it })
    }
}

@Composable
private fun SwitcherSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SwitcherDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SwitcherRow(
    icon: ImageVector,
    name: String,
    selected: Boolean,
    indented: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (indented) 40.dp else 16.dp, end = 16.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
