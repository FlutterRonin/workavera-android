package com.workavera.android.ui.board

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.AuthState
import com.workavera.android.data.model.BoardLabel
import com.workavera.android.data.model.BoardMember
import com.workavera.android.data.model.BoardProject
import com.workavera.android.data.model.BoardState
import com.workavera.android.data.model.BoardTask
import com.workavera.android.data.model.TaskPriority
import com.workavera.android.ui.components.AvatarView
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.ExpressivePullToRefreshBox
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.components.PaginationBar
import com.workavera.android.ui.theme.color
import com.workavera.android.ui.theme.parseHexColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(14.dp)
private val CardBorder: BorderStroke
    @Composable get() = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)

private val dueDateShortFormat =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val dueDateLongFormat =
    DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm", Locale.getDefault())

/**
 * Board screen, laid out like the mobile form of the iOS BoardView: project
 * card → state chips → single-column task list. A task's state / priority /
 * due date are edited in the detail bottom sheet (iOS likewise has no drag &
 * drop or task creation). Realtime signals drive automatic refreshes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(state: AuthState.LoggedIn) {
    val boardViewModel: BoardViewModel =
        viewModel(key = state.user.id, factory = BoardViewModel.Factory)
    val ui = boardViewModel.uiState
    var showProjectSwitcher by remember { mutableStateOf(false) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        boardViewModel.loadIfNeeded()
        boardViewModel.listenForRealtimeSignals()
    }

    val project = boardViewModel.selectedProject
    val selectedState = project?.states?.firstOrNull { it.id == ui.selectedStateId }
        ?: project?.states?.firstOrNull()
    val selectedTask = selectedTaskId?.let { id ->
        ui.projects.flatMap { it.states }.flatMap { it.tasks }.firstOrNull { it.id == id }
    }
    LaunchedEffect(selectedTaskId, selectedTask) {
        // Auto-dismiss the detail sheet when the task is deleted or leaves the page.
        if (selectedTaskId != null && selectedTask == null) selectedTaskId = null
    }

    ExpressivePullToRefreshBox(
        isRefreshing = ui.isLoading,
        onRefresh = { boardViewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            !ui.hasLoaded && ui.isLoading -> LoadingIndicator("Loading board…")
            ui.hasLoaded && ui.projects.isEmpty() -> {
                BoardEmptyState(onRefresh = { boardViewModel.refresh() })
            }
            project != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (ui.error != null) {
                        ErrorBanner(
                            message = ui.error,
                            onRetry = { boardViewModel.refresh() },
                        )
                    }
                    ProjectCard(
                        project = project,
                        selected = false,
                        descriptionLines = 1,
                        accessory = {
                            Icon(
                                Icons.Outlined.UnfoldMore,
                                contentDescription = "Switch project",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { showProjectSwitcher = true },
                    )
                    StateChipRow(
                        states = project.states,
                        selectedStateId = selectedState?.id,
                        onSelect = boardViewModel::selectState,
                    )
                    TaskList(
                        state = selectedState,
                        updatingTaskIds = ui.updatingTaskIds,
                        onTaskClick = { selectedTaskId = it },
                    )
                }
            }
        }
    }

    if (showProjectSwitcher) {
        ProjectSwitcherSheet(
            ui = ui,
            selectedProjectId = project?.id,
            onSelect = {
                boardViewModel.selectProject(it)
                showProjectSwitcher = false
            },
            onPageChange = boardViewModel::loadPage,
            onDismiss = { showProjectSwitcher = false },
        )
    }

    if (selectedTask != null && project != null) {
        TaskDetailSheet(
            task = selectedTask,
            states = project.states,
            isUpdating = selectedTask.id in ui.updatingTaskIds,
            onMove = { stateId ->
                boardViewModel.moveTask(selectedTask.id, stateId) { success ->
                    if (success) selectedTaskId = null
                }
            },
            onPriorityChange = { boardViewModel.updatePriority(selectedTask.id, it) },
            onDueDateChange = { boardViewModel.updateDueDate(selectedTask.id, it) },
            onDismiss = { selectedTaskId = null },
        )
    }
}

@Composable
private fun TaskList(
    state: BoardState?,
    updatingTaskIds: Set<String>,
    onTaskClick: (String) -> Unit,
) {
    if (state == null) return
    if (state.tasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No tasks in ${state.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.tasks.forEach { task ->
            TaskCard(
                task = task,
                isUpdating = task.id in updatingTaskIds,
                onClick = { onTaskClick(task.id) },
            )
        }
    }
}

@Composable
private fun TaskCard(task: BoardTask, isUpdating: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (task.labels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.labels.forEach { LabelChip(it) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (task.description.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val hasMeta = task.priority != TaskPriority.NONE ||
                task.dueDate != null ||
                task.documentCount > 0
            if (hasMeta || task.assignees.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.priority != TaskPriority.NONE) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(task.priority.color, CircleShape),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = task.priority.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    task.dueDate?.let { dueDate ->
                        val overdue = task.isOverdue
                        Icon(
                            Icons.Outlined.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (overdue) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = dueDateShortFormat.format(
                                dueDate.atZone(ZoneId.systemDefault()),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    if (task.documentCount > 0) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${task.documentCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    MemberStack(members = task.assignees, size = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun LabelChip(label: BoardLabel) {
    val color = parseHexColor(label.color, MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        text = label.name,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Overlapping member avatars (up to 3, then +N), mirroring the iOS MemberStack. */
@Composable
private fun MemberStack(members: List<BoardMember>, size: Dp) {
    if (members.isEmpty()) return
    val shown = members.take(3)
    Row(horizontalArrangement = Arrangement.spacedBy(-(size * 0.3f))) {
        shown.forEach { member ->
            Box(modifier = Modifier.border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)) {
                AvatarView(url = member.avatarUrl, name = member.name, size = size)
            }
        }
        if (members.size > shown.size) {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+${members.size - shown.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun StateChipRow(
    states: List<BoardState>,
    selectedStateId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        states.forEach { state ->
            val selected = state.id == selectedStateId
            Surface(
                onClick = { onSelect(state.id) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = if (selected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                parseHexColor(
                                    state.color,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${state.tasks.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Project card shared by the header and the project switcher list, mirroring the iOS ProjectCard. */
@Composable
private fun ProjectCard(
    project: BoardProject,
    selected: Boolean,
    descriptionLines: Int,
    accessory: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardBorder
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (project.description.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = descriptionLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                accessory()
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProjectStat(Icons.Outlined.Layers, "${project.taskCount}")
                Spacer(Modifier.width(16.dp))
                ProjectStat(Icons.Outlined.Sell, "${project.labelCount}")
                Spacer(Modifier.width(16.dp))
                ProjectStat(Icons.Outlined.Group, "${project.members.size}")
                Spacer(Modifier.weight(1f))
                MemberStack(members = project.members, size = 22.dp)
            }
        }
    }
}

@Composable
private fun ProjectStat(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Project switcher bottom sheet, mirroring the iOS ProjectSwitcherSheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSwitcherSheet(
    ui: BoardUiState,
    selectedProjectId: String?,
    onSelect: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "SWITCH PROJECT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ui.projects, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    selected = project.id == selectedProjectId,
                    descriptionLines = 2,
                    accessory = {
                        if (project.id == selectedProjectId) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = { onSelect(project.id) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        PaginationBar(
            page = ui.page,
            totalPages = ui.totalPages,
            onPageChange = onPageChange,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BoardEmptyState(onRefresh: () -> Unit) {
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
                Icons.Outlined.ViewKanban,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No Projects",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Create or join a project in Workavera to see it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onRefresh) {
            Text("Refresh")
        }
    }
}

/** Task detail bottom sheet, mirroring the iOS TaskDetailSheet; state / priority / due date are editable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailSheet(
    task: BoardTask,
    states: List<BoardState>,
    isUpdating: Boolean,
    onMove: (String) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onDueDateChange: (Instant?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            if (task.labels.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.labels.forEach { LabelChip(it) }
                }
            }
            if (task.description.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = CardBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column {
                    StatusRow(
                        task = task,
                        states = states,
                        enabled = !isUpdating,
                        onMove = onMove,
                    )
                    DetailDivider()
                    PriorityRow(
                        task = task,
                        enabled = !isUpdating,
                        onPriorityChange = onPriorityChange,
                    )
                    DetailDivider()
                    DueDateRow(
                        task = task,
                        enabled = !isUpdating,
                        onPick = {
                            pickDateTime(context, task.dueDate, onDueDateChange)
                        },
                        onClear = { onDueDateChange(null) },
                    )
                    if (task.documentCount > 0) {
                        DetailDivider()
                        DetailRow(
                            icon = Icons.Outlined.Description,
                            title = "Documents",
                            value = "${task.documentCount}",
                        )
                    }
                    if (task.assignees.isNotEmpty()) {
                        DetailDivider()
                        DetailRow(
                            icon = Icons.Outlined.Group,
                            title = "Assignees",
                            value = task.assignees.joinToString(", ") { it.name },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 48.dp),
    )
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusRow(
    task: BoardTask,
    states: List<BoardState>,
    enabled: Boolean,
    onMove: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = states.firstOrNull { it.id == task.stateId }
    Box {
        DetailRow(
            icon = Icons.Outlined.SwapHoriz,
            title = "Status",
            value = current?.name ?: "—",
            onClick = if (enabled) ({ expanded = true }) else null,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            states.forEach { state ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        parseHexColor(
                                            state.color,
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(state.name)
                        }
                    },
                    trailingIcon = {
                        if (state.id == task.stateId) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onMove(state.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun PriorityRow(
    task: BoardTask,
    enabled: Boolean,
    onPriorityChange: (TaskPriority) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        DetailRow(
            icon = Icons.Outlined.Flag,
            title = "Priority",
            value = task.priority.label,
            valueColor = task.priority.color,
            onClick = if (enabled) ({ expanded = true }) else null,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TaskPriority.entries.forEach { priority ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(priority.color, CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(priority.label)
                        }
                    },
                    trailingIcon = {
                        if (priority == task.priority) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onPriorityChange(priority)
                    },
                )
            }
        }
    }
}

@Composable
private fun DueDateRow(
    task: BoardTask,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val dueDate = task.dueDate
    Column {
        DetailRow(
            icon = Icons.Outlined.DateRange,
            title = "Due Date",
            value = dueDate?.let {
                dueDateLongFormat.format(it.atZone(ZoneId.systemDefault()))
            } ?: "No due date",
            valueColor = if (task.isOverdue) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            onClick = if (enabled) onPick else null,
        )
        if (dueDate != null && enabled) {
            TextButton(
                onClick = onClear,
                modifier = Modifier.padding(start = 32.dp),
            ) {
                Text("Clear Due Date", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** System date + time pickers (Android convention), merged into a single Instant. */
private fun pickDateTime(
    context: Context,
    current: Instant?,
    onPicked: (Instant) -> Unit,
) {
    val calendar = Calendar.getInstance()
    current?.let { calendar.time = Date.from(it) }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    calendar.set(year, month, day, hour, minute, 0)
                    onPicked(calendar.toInstant())
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true,
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}
