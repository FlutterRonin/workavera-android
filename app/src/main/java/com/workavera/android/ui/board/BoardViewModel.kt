package com.workavera.android.ui.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.BoardRepository
import com.workavera.android.data.model.ArchivedBoardTask
import com.workavera.android.data.model.BoardProject
import com.workavera.android.data.model.BoardProjectShare
import com.workavera.android.data.model.BoardTask
import com.workavera.android.data.model.TaskPriority
import com.workavera.android.data.network.ApiException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BoardUiState(
    val projects: List<BoardProject> = emptyList(),
    val selectedProjectId: String? = null,
    val selectedStateId: String? = null,
    /** UI page number, 0-based; the API layer is 1-based, converted at the ViewModel boundary. */
    val page: Int = 0,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    /** Ids of tasks being moved / reprioritized / re-dated, for inline loading. */
    val updatingTaskIds: Set<String> = emptySet(),
    /** Archived tasks of [archivedProjectId], paged separately from the board itself. */
    val archivedTasks: List<ArchivedBoardTask> = emptyList(),
    val archivedProjectId: String? = null,
    val archivedPage: Int = 0,
    val archivedTotalPages: Int = 1,
    val archivedTotalItems: Int = 0,
    val isLoadingArchived: Boolean = false,
    val hasLoadedArchived: Boolean = false,
    val archivedError: String? = null,
    val restoringTaskIds: Set<String> = emptySet(),
    /** Public preview links, keyed by project; never cached to disk. */
    val shares: Map<String, BoardProjectShare> = emptyMap(),
    /** Projects whose share state has been resolved; absent from [shares] means known-not-shared. */
    val resolvedShareIds: Set<String> = emptySet(),
)

/**
 * Board state and actions, mirroring the iOS BoardStore (without selection
 * persistence). Move / priority / due-date changes are optimistic updates
 * rolled back on failure; a successful move triggers a full refresh; realtime
 * signals (SSE) only act as refresh triggers, reconnecting with 1s→30s
 * exponential backoff.
 */
class BoardViewModel(
    private val repository: BoardRepository,
) : ViewModel() {

    var uiState by mutableStateOf(BoardUiState())
        private set

    val selectedProject: BoardProject?
        get() = uiState.projects.firstOrNull { it.id == uiState.selectedProjectId }
            ?: uiState.projects.firstOrNull()

    /**
     * Shared load-on-enter strategy (matching the iOS CachedPageLoader):
     * render the disk cache first (silently skipped when absent), then do one
     * silent network refresh.
     */
    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        viewModelScope.launch {
            repository.cachedPage(uiState.page + 1)?.let { cached ->
                uiState = uiState.copy(
                    projects = cached.projects,
                    page = cached.page - 1,
                    totalPages = cached.totalPages,
                    totalItems = cached.totalItems,
                    hasLoaded = true,
                )
                reconcileSelection()
            }
            refresh()
        }
    }

    fun refresh(page: Int = uiState.page) {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            try {
                val loaded = repository.loadPage(page + 1, PROJECTS_PER_PAGE)
                uiState = uiState.copy(
                    projects = loaded.projects,
                    page = loaded.page - 1,
                    totalPages = loaded.totalPages,
                    totalItems = loaded.totalItems,
                    isLoading = false,
                    hasLoaded = true,
                    error = null,
                )
                reconcileSelection()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                uiState = uiState.copy(isLoading = false, hasLoaded = true, error = e.userMessage)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    hasLoaded = true,
                    error = e.message ?: "Could not load the board.",
                )
            }
        }
    }

    fun loadPage(page: Int) {
        val target = page.coerceIn(0, uiState.totalPages - 1)
        if (target != uiState.page) refresh(page = target)
    }

    fun selectProject(id: String) {
        uiState = uiState.copy(selectedProjectId = id, selectedStateId = null)
    }

    fun selectState(id: String) {
        uiState = uiState.copy(selectedStateId = id)
    }

    /** Falls back to the first project when the selection is not on the current page (matching iOS reconcileSelection). */
    private fun reconcileSelection() {
        val project = selectedProject ?: return
        if (project.id != uiState.selectedProjectId) {
            uiState = uiState.copy(selectedProjectId = project.id, selectedStateId = null)
        }
    }

    /**
     * Moves a task to a target state (appended to the end of the column,
     * rank = column max rank + 1024). Optimistic update: full refresh on
     * success, rollback on failure; onComplete reports the outcome.
     */
    fun moveTask(taskId: String, toStateId: String, onComplete: (Boolean) -> Unit = {}) {
        val location = findTask(taskId) ?: return
        if (location.task.stateId == toStateId) return
        if (taskId in uiState.updatingTaskIds) return

        val snapshot = uiState.projects
        val targetState = location.project.states.firstOrNull { it.id == toStateId } ?: return
        val nextRank = (targetState.tasks.maxOfOrNull { it.rank } ?: 0.0) + RANK_GAP

        applyTaskMove(location.task, toStateId, nextRank)
        uiState = uiState.copy(updatingTaskIds = uiState.updatingTaskIds + taskId)
        viewModelScope.launch {
            try {
                repository.moveTask(taskId, toStateId, nextRank)
                uiState = uiState.copy(updatingTaskIds = uiState.updatingTaskIds - taskId)
                refresh()
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    projects = snapshot,
                    updatingTaskIds = uiState.updatingTaskIds - taskId,
                    error = errorMessage(e, "Could not move the task."),
                )
                onComplete(false)
            }
        }
    }

    /** Changes the priority: optimistic single-field update; only that field is rolled back on failure. */
    fun updatePriority(taskId: String, priority: TaskPriority, onComplete: (Boolean) -> Unit = {}) {
        val location = findTask(taskId) ?: return
        if (location.task.priority == priority) return
        if (taskId in uiState.updatingTaskIds) return

        val oldPriority = location.task.priority
        mutateTask(taskId) { it.copy(priority = priority) }
        uiState = uiState.copy(updatingTaskIds = uiState.updatingTaskIds + taskId)
        viewModelScope.launch {
            try {
                repository.updatePriority(taskId, priority)
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutateTask(taskId) { it.copy(priority = oldPriority) }
                uiState = uiState.copy(error = errorMessage(e, "Could not update the priority."))
                onComplete(false)
            } finally {
                uiState = uiState.copy(updatingTaskIds = uiState.updatingTaskIds - taskId)
            }
        }
    }

    /** Changes the due date: optimistic single-field update, rolled back on failure; null clears it. */
    fun updateDueDate(taskId: String, dueDate: Instant?, onComplete: (Boolean) -> Unit = {}) {
        val location = findTask(taskId) ?: return
        if (taskId in uiState.updatingTaskIds) return
        // A due date before the start date would render as a negative span,
        // which the server rejects.
        val dueDay = dueDate?.atZone(java.time.ZoneOffset.UTC)?.toLocalDate()
        if (!BoardTask.isValidSpan(location.task.startDay, dueDay)) {
            onComplete(false)
            return
        }

        val oldDueDate = location.task.dueDate
        mutateTask(taskId) { it.copy(dueDate = dueDate) }
        uiState = uiState.copy(updatingTaskIds = uiState.updatingTaskIds + taskId)
        viewModelScope.launch {
            try {
                repository.updateDueDate(taskId, dueDate)
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutateTask(taskId) { it.copy(dueDate = oldDueDate) }
                uiState = uiState.copy(error = errorMessage(e, "Could not update the due date."))
                onComplete(false)
            } finally {
                uiState = uiState.copy(updatingTaskIds = uiState.updatingTaskIds - taskId)
            }
        }
    }

    /** Changes the start date: optimistic single-field update, rolled back on failure; null clears it. */
    fun updateStartDate(taskId: String, startDate: Instant?, onComplete: (Boolean) -> Unit = {}) {
        val location = findTask(taskId) ?: return
        if (taskId in uiState.updatingTaskIds) return
        // The server rejects a start after the due date, so refuse it here
        // before a save can fail on it.
        val startDay = startDate?.atZone(java.time.ZoneOffset.UTC)?.toLocalDate()
        if (!BoardTask.isValidSpan(startDay, location.task.dueDay)) {
            onComplete(false)
            return
        }

        val oldStartDate = location.task.startDate
        mutateTask(taskId) { it.copy(startDate = startDate) }
        uiState = uiState.copy(updatingTaskIds = uiState.updatingTaskIds + taskId)
        viewModelScope.launch {
            try {
                repository.updateStartDate(taskId, startDate)
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutateTask(taskId) { it.copy(startDate = oldStartDate) }
                uiState = uiState.copy(error = errorMessage(e, "Could not update the start date."))
                onComplete(false)
            } finally {
                uiState = uiState.copy(updatingTaskIds = uiState.updatingTaskIds - taskId)
            }
        }
    }

    // MARK: - Project public links

    /** Resolves one project's share; the toolbar icon reports it, so this runs with the project, not the sheet. */
    fun loadShare(projectId: String) {
        if (projectId in uiState.resolvedShareIds) return
        viewModelScope.launch {
            try {
                val share = repository.loadShare(projectId)
                applyShare(projectId, share)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A project that cannot report its share simply shows an unshared icon.
            }
        }
    }

    fun publishShare(projectId: String, expires: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                applyShare(projectId, repository.publishShare(projectId, expires))
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = errorMessage(e, "Could not update the share link."))
                onComplete(false)
            }
        }
    }

    fun revokeShare(projectId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.unpublishShare(projectId)
                applyShare(projectId, null)
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = errorMessage(e, "Could not revoke the share link."))
                onComplete(false)
            }
        }
    }

    fun publicShareUrl(projectId: String): String? =
        uiState.shares[projectId]?.let(repository::publicShareUrl)

    private fun applyShare(projectId: String, share: BoardProjectShare?) {
        val shares = uiState.shares.toMutableMap()
        if (share == null) shares.remove(projectId) else shares[projectId] = share
        uiState = uiState.copy(
            shares = shares,
            resolvedShareIds = uiState.resolvedShareIds + projectId,
        )
    }

    /** Archives a task optimistically; archived tasks are excluded from normal Board queries. */
    fun archiveTask(taskId: String, onComplete: (Boolean) -> Unit = {}) {
        val location = findTask(taskId) ?: return
        if (taskId in uiState.updatingTaskIds) return
        if (location.project.states.lastOrNull()?.id != location.task.stateId) return

        val snapshot = uiState.projects
        removeTask(taskId)
        uiState = uiState.copy(updatingTaskIds = uiState.updatingTaskIds + taskId)
        viewModelScope.launch {
            try {
                repository.archiveTask(taskId)
                uiState = uiState.copy(updatingTaskIds = uiState.updatingTaskIds - taskId)
                refresh()
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    projects = snapshot,
                    updatingTaskIds = uiState.updatingTaskIds - taskId,
                    error = errorMessage(e, "Could not archive the task."),
                )
                onComplete(false)
            }
        }
    }

    /**
     * Loads one page of a project's archived tasks. Switching projects starts
     * the list over rather than paging into the previous project's results.
     */
    fun loadArchivedTasks(projectId: String, requestedPage: Int? = null) {
        if (uiState.isLoadingArchived) return
        val restart = uiState.archivedProjectId != projectId
        if (restart) uiState = uiState.copy(archivedProjectId = projectId).clearArchivedList()
        val target = (requestedPage ?: if (restart) 0 else uiState.archivedPage).coerceAtLeast(0)

        viewModelScope.launch {
            uiState = uiState.copy(isLoadingArchived = true)
            try {
                val loaded = repository.loadArchivedTasks(
                    projectId,
                    target + 1,
                    ARCHIVED_TASKS_PER_PAGE,
                )
                uiState = uiState.copy(
                    archivedTasks = loaded.tasks,
                    archivedPage = loaded.page - 1,
                    archivedTotalPages = loaded.totalPages,
                    archivedTotalItems = loaded.totalItems,
                    isLoadingArchived = false,
                    hasLoadedArchived = true,
                    archivedError = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingArchived = false,
                    hasLoadedArchived = true,
                    archivedError = errorMessage(e, "Could not load the archived tasks."),
                )
            }
        }
    }

    /**
     * Restores an archived task to the state it was archived from, then
     * reloads both the archived list and the board it reappears on.
     */
    fun restoreTask(taskId: String, onComplete: (Boolean) -> Unit = {}) {
        val projectId = uiState.archivedProjectId ?: return
        if (taskId in uiState.restoringTaskIds) return

        uiState = uiState.copy(restoringTaskIds = uiState.restoringTaskIds + taskId)
        viewModelScope.launch {
            try {
                repository.restoreTask(taskId)
                uiState = uiState.copy(restoringTaskIds = uiState.restoringTaskIds - taskId)
                // Step back a page when the last remaining entry on it was restored.
                val nextPage = if (uiState.archivedTasks.size == 1) {
                    (uiState.archivedPage - 1).coerceAtLeast(0)
                } else {
                    uiState.archivedPage
                }
                loadArchivedTasks(projectId, requestedPage = nextPage)
                refresh()
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    restoringTaskIds = uiState.restoringTaskIds - taskId,
                    archivedError = errorMessage(e, "Could not restore the task."),
                )
                onComplete(false)
            }
        }
    }

    private fun BoardUiState.clearArchivedList() = copy(
        archivedTasks = emptyList(),
        archivedPage = 0,
        archivedTotalPages = 1,
        archivedTotalItems = 0,
        hasLoadedArchived = false,
        archivedError = null,
    )

    /** Realtime signal loop: a suspend function started from a LaunchedEffect; cancelled automatically on leaving the board. */
    suspend fun listenForRealtimeSignals() {
        var backoffMs = 1_000L
        while (currentCoroutineContext().isActive) {
            try {
                repository.realtimeSignals().collect {
                    backoffMs = 1_000L
                    refresh()
                }
                // Flow ended normally (server-side 35-minute timeout): reconnect immediately.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private data class TaskLocation(val project: BoardProject, val task: BoardTask)

    private fun findTask(taskId: String): TaskLocation? {
        for (project in uiState.projects) {
            for (state in project.states) {
                state.tasks.firstOrNull { it.id == taskId }?.let {
                    return TaskLocation(project, it)
                }
            }
        }
        return null
    }

    /** Mutates one task's fields (immutably rebuilding the projects tree). */
    private fun mutateTask(taskId: String, transform: (BoardTask) -> BoardTask) {
        uiState = uiState.copy(
            projects = uiState.projects.map { project ->
                project.copy(
                    states = project.states.map { state ->
                        state.copy(
                            tasks = state.tasks.map { task ->
                                if (task.id == taskId) transform(task) else task
                            },
                        )
                    },
                )
            },
        )
    }

    private fun removeTask(taskId: String) {
        uiState = uiState.copy(
            projects = uiState.projects.map { project ->
                project.copy(
                    states = project.states.map { state ->
                        state.copy(tasks = state.tasks.filterNot { it.id == taskId })
                    },
                )
            },
        )
    }

    /** Cross-column move: remove from the source column, append to the target column with the new rank. */
    private fun applyTaskMove(task: BoardTask, toStateId: String, rank: Double) {
        uiState = uiState.copy(
            projects = uiState.projects.map { project ->
                if (project.id != task.projectId) return@map project
                project.copy(
                    states = project.states.map { state ->
                        when (state.id) {
                            task.stateId -> state.copy(
                                tasks = state.tasks.filterNot { it.id == task.id },
                            )
                            toStateId -> state.copy(
                                tasks = state.tasks + task.copy(stateId = toStateId, rank = rank),
                            )
                            else -> state
                        }
                    },
                )
            },
        )
    }

    private fun errorMessage(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        /** Matches iOS: 4 projects per page. */
        const val PROJECTS_PER_PAGE = 4

        /** Matches iOS: 10 archived tasks per page. */
        const val ARCHIVED_TASKS_PER_PAGE = 10

        /** Rank step for append-to-end, matching iOS. */
        const val RANK_GAP = 1024.0

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                BoardViewModel(app.container.boardRepository)
            }
        }
    }
}
