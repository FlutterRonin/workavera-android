package com.workavera.android.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.NotificationRepository
import com.workavera.android.data.model.AppNotification
import com.workavera.android.data.model.AppNotificationType
import com.workavera.android.data.model.ModelShareStatus
import com.workavera.android.data.model.NotificationRealtimeEvent
import com.workavera.android.data.network.ApiException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NotificationUiState(
    val items: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    /** UI page number, 0-based; the API layer is 1-based, converted at the ViewModel boundary. */
    val page: Int = 0,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    /** Ids of notifications with a share response / archive / delete in flight, for inline loading. */
    val respondingShareIds: Set<String> = emptySet(),
    val managingIds: Set<String> = emptySet(),
)

/**
 * Notification state and actions, mirroring the iOS NotificationStore.
 * Mark-read / mark-all-read are optimistic updates rolled back on failure;
 * archive/delete refresh with the page-fallback rule; [listenForRealtimeEvents]
 * maintains the SSE connection, applying events locally before a full refresh.
 */
class NotificationViewModel(
    private val repository: NotificationRepository,
) : ViewModel() {

    var uiState by mutableStateOf(NotificationUiState())
        private set

    /**
     * Shared load-on-enter strategy (matching the iOS CachedPageLoader):
     * render the disk cache first (silently skipped when absent), then do one
     * silent network refresh.
     */
    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        viewModelScope.launch {
            repository.cachedSnapshot(uiState.page + 1)?.let { cached ->
                uiState = uiState.copy(
                    items = cached.page.items,
                    unreadCount = cached.unreadCount,
                    page = cached.page.page - 1,
                    totalPages = maxOf(1, cached.page.totalPages),
                    totalItems = cached.page.totalItems,
                    hasLoaded = true,
                )
            }
            refresh()
        }
    }

    /**
     * Realtime subscription loop: a suspend function started from a
     * LaunchedEffect, cancelled with the composition when the user signs out.
     * Reconnects with 1s→30s exponential backoff, reset on each received event
     * (matching the iOS app).
     */
    suspend fun listenForRealtimeEvents() {
        var backoffMs = 1_000L
        while (currentCoroutineContext().isActive) {
            try {
                repository.realtimeEvents().collect { event ->
                    backoffMs = 1_000L
                    applyRealtimeEvent(event)
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

    fun refresh(page: Int = uiState.page) {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            try {
                val loaded = repository.loadSnapshot(page + 1, PAGE_SIZE)
                uiState = uiState.copy(
                    items = loaded.page.items,
                    unreadCount = loaded.unreadCount,
                    page = loaded.page.page - 1,
                    totalPages = maxOf(1, loaded.page.totalPages),
                    totalItems = loaded.page.totalItems,
                    isLoading = false,
                    hasLoaded = true,
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                uiState = uiState.copy(isLoading = false, hasLoaded = true, error = e.userMessage)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    hasLoaded = true,
                    error = e.message ?: "Could not load notifications.",
                )
            }
        }
    }

    fun loadPage(page: Int) {
        val target = page.coerceIn(0, uiState.totalPages - 1)
        if (target != uiState.page) refresh(page = target)
    }

    /** Tapping a row marks it read: local-first, rolled back on failure. */
    fun markRead(id: String) {
        val item = uiState.items.firstOrNull { it.id == id } ?: return
        if (item.isRead) return
        val now = Instant.now().toString()
        replaceItem(item.copy(readAt = now))
        uiState = uiState.copy(unreadCount = (uiState.unreadCount - 1).coerceAtLeast(0))
        viewModelScope.launch {
            try {
                repository.markRead(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                replaceItem(item)
                uiState = uiState.copy(
                    unreadCount = uiState.unreadCount + 1,
                    error = errorMessage(e, "Could not mark the notification as read."),
                )
            }
        }
    }

    /** Mark all read: local-first, rolled back on failure. */
    fun markAllRead() {
        val unread = uiState.items.filter { !it.isRead }
        if (unread.isEmpty()) return
        val snapshot = uiState.items
        val snapshotCount = uiState.unreadCount
        val now = Instant.now().toString()
        uiState = uiState.copy(
            items = snapshot.map { if (it.isRead) it else it.copy(readAt = now) },
            unreadCount = 0,
        )
        viewModelScope.launch {
            try {
                repository.markAllRead()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    items = snapshot,
                    unreadCount = snapshotCount,
                    error = errorMessage(e, "Could not mark all notifications as read."),
                )
            }
        }
    }

    /** Responds to a model share; on success the notification becomes read and shows the outcome. */
    fun respondToModelShare(id: String, accept: Boolean) {
        val item = uiState.items.firstOrNull { it.id == id } ?: return
        if (item.notificationType != AppNotificationType.MODEL_SHARE) return
        if (item.data.modelShareStatus != ModelShareStatus.PENDING) return
        if (id in uiState.respondingShareIds) return

        uiState = uiState.copy(respondingShareIds = uiState.respondingShareIds + id)
        viewModelScope.launch {
            try {
                val status = repository.respondToModelShare(id, accept)
                val latest = uiState.items.firstOrNull { it.id == id }
                if (latest != null) {
                    replaceItem(
                        latest.copy(
                            readAt = latest.readAt.ifEmpty { Instant.now().toString() },
                            data = latest.data.copy(shareStatus = status.raw),
                        ),
                    )
                    if (!latest.isRead) {
                        uiState = uiState.copy(
                            unreadCount = (uiState.unreadCount - 1).coerceAtLeast(0),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    error = errorMessage(e, "Could not respond to the share invitation."),
                )
            } finally {
                uiState = uiState.copy(respondingShareIds = uiState.respondingShareIds - id)
            }
        }
    }

    fun archive(id: String) = manage(id) { repository.archive(id) }

    fun delete(id: String) = manage(id) { repository.delete(id) }

    /** Shared archive / delete flow: remove locally first, then refresh with the page-fallback rule. */
    private fun manage(id: String, action: suspend () -> Unit) {
        val item = uiState.items.firstOrNull { it.id == id } ?: return
        if (id in uiState.managingIds || id in uiState.respondingShareIds) return

        uiState = uiState.copy(managingIds = uiState.managingIds + id)
        viewModelScope.launch {
            try {
                action()
                val remaining = uiState.items.filterNot { it.id == id }
                uiState = uiState.copy(
                    items = remaining,
                    totalItems = (uiState.totalItems - 1).coerceAtLeast(0),
                    unreadCount = if (item.isRead) {
                        uiState.unreadCount
                    } else {
                        (uiState.unreadCount - 1).coerceAtLeast(0)
                    },
                )
                // Fall back one page when this page emptied out and is not the
                // first; otherwise refresh in place to realign with server paging.
                val target = if (remaining.isEmpty() && uiState.page > 0) uiState.page - 1 else uiState.page
                uiState = uiState.copy(isLoading = false)
                refresh(page = target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    error = errorMessage(e, "Could not update the notification."),
                )
            } finally {
                uiState = uiState.copy(managingIds = uiState.managingIds - id)
            }
        }
    }

    private fun replaceItem(updated: AppNotification) {
        uiState = uiState.copy(
            items = uiState.items.map { if (it.id == updated.id) updated else it },
        )
    }

    /** Local incremental apply of a realtime event (matching iOS applyRealtimeEvent), followed by a full refresh. */
    private fun applyRealtimeEvent(event: NotificationRealtimeEvent) {
        val record = event.record
        val existing = uiState.items.firstOrNull { it.id == record.id }
        when (event.action) {
            "create" -> {
                if (record.status != "active" || existing != null) return
                uiState = uiState.copy(
                    totalItems = uiState.totalItems + 1,
                    unreadCount = if (record.isRead) uiState.unreadCount else uiState.unreadCount + 1,
                )
                // Insert only on the first page, keeping the -pinned,-created
                // order and truncating to the page size.
                if (uiState.page == 0) {
                    val merged = (uiState.items + record)
                        .sortedWith(
                            compareByDescending<AppNotification> { it.pinned }
                                .thenByDescending { it.created },
                        )
                        .take(PAGE_SIZE)
                    uiState = uiState.copy(items = merged)
                }
            }
            "update" -> {
                // Updates that make the record inactive (e.g. archiving) are treated as deletes.
                if (record.status != "active") {
                    removeLocally(record.id)
                    return
                }
                if (existing != null) {
                    if (existing.isRead != record.isRead) {
                        uiState = uiState.copy(
                            unreadCount = (
                                uiState.unreadCount + if (record.isRead) -1 else 1
                                ).coerceAtLeast(0),
                        )
                    }
                    replaceItem(record)
                }
            }
            "delete" -> removeLocally(record.id)
        }
    }

    private fun removeLocally(id: String) {
        val existing = uiState.items.firstOrNull { it.id == id } ?: return
        uiState = uiState.copy(
            items = uiState.items.filterNot { it.id == id },
            totalItems = (uiState.totalItems - 1).coerceAtLeast(0),
            unreadCount = if (existing.isRead) {
                uiState.unreadCount
            } else {
                (uiState.unreadCount - 1).coerceAtLeast(0)
            },
        )
    }

    private fun errorMessage(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        const val PAGE_SIZE = 20

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                NotificationViewModel(app.container.notificationRepository)
            }
        }
    }
}
