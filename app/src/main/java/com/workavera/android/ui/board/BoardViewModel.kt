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
import com.workavera.android.data.model.BoardProject
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
