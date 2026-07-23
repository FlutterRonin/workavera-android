package com.workavera.android.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.CalendarRepository
import com.workavera.android.data.model.CalendarEvent
import com.workavera.android.data.model.CalendarEventInput
import com.workavera.android.data.model.CalendarTaskDeadline
import com.workavera.android.data.network.ApiException
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CalendarUiState(
    val events: List<CalendarEvent> = emptyList(),
    val tasks: List<CalendarTaskDeadline> = emptyList(),
    val timezone: String = ZoneId.systemDefault().id,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    val mutatingIds: Set<String> = emptySet(),
) {
    val zone: ZoneId get() = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
}

/**
 * Calendar state and actions, mirroring the iOS CalendarStore: cache-first
 * snapshot load, event create/update/delete, and realtime refresh signals.
 */
class CalendarViewModel(
    private val repository: CalendarRepository,
) : ViewModel() {

    var uiState by mutableStateOf(CalendarUiState())
        private set

    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        repository.cachedSnapshot()?.let { cached ->
            uiState = uiState.copy(
                events = cached.events,
                tasks = cached.tasks,
                timezone = cached.timezone,
                hasLoaded = true,
            )
        }
        refresh()
    }

    fun refresh() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            uiState = try {
                val snapshot = repository.loadSnapshot()
                uiState.copy(
                    events = snapshot.events.sortedBy { it.startAt },
                    tasks = snapshot.tasks.sortedBy { it.dueDay },
                    timezone = snapshot.timezone,
                    isLoading = false,
                    hasLoaded = true,
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState.copy(isLoading = false, hasLoaded = true, error = message(e, "Could not load the calendar."))
            }
        }
    }

    /** Creates or updates an event; onComplete receives an error message or null on success. */
    fun save(existingId: String?, input: CalendarEventInput, onComplete: (String?) -> Unit) {
        if (existingId != null && existingId in uiState.mutatingIds) return
        if (existingId != null) uiState = uiState.copy(mutatingIds = uiState.mutatingIds + existingId)
        viewModelScope.launch {
            try {
                if (existingId != null) repository.update(existingId, input) else repository.create(input)
                refresh()
                onComplete(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(message(e, "Could not save the event."))
            } finally {
                if (existingId != null) uiState = uiState.copy(mutatingIds = uiState.mutatingIds - existingId)
            }
        }
    }

    fun delete(id: String) {
        if (id in uiState.mutatingIds) return
        uiState = uiState.copy(mutatingIds = uiState.mutatingIds + id)
        viewModelScope.launch {
            try {
                repository.delete(id)
                uiState = uiState.copy(events = uiState.events.filterNot { it.id == id })
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not delete the event."))
            } finally {
                uiState = uiState.copy(mutatingIds = uiState.mutatingIds - id)
            }
        }
    }

    /** Realtime loop started from a LaunchedEffect; reconnects with 1s→30s backoff. */
    suspend fun listenForRealtimeSignals() {
        var backoffMs = 1_000L
        while (currentCoroutineContext().isActive) {
            try {
                repository.realtimeSignals().collect {
                    backoffMs = 1_000L
                    refresh()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun message(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                CalendarViewModel(app.container.calendarRepository)
            }
        }
    }
}
