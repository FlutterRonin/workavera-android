package com.workavera.android.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.workavera.android.data.AuthState
import com.workavera.android.data.SettingsRepository
import com.workavera.android.data.model.CalendarSnapshot
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/** What the Agenda widget renders. */
data class AgendaWidgetState(
    val snapshot: AgendaWidgetSnapshot?,
    val status: Status,
    val isRefreshing: Boolean = false,
) {
    enum class Status { READY, SIGNED_OUT, UNAVAILABLE }
}

/**
 * Feeds the Agenda widget (iOS AgendaWidgetStore + AgendaWidgetFetcher).
 *
 * The widget runs in the app's process, so it reads the signed-in server,
 * token, and user id straight from [SettingsRepository] and fetches the same
 * Calendar snapshot the Calendar screen loads. The latest agenda is kept in a
 * file so the widget renders immediately (and offline) after the process is
 * recreated. Every change pushes a widget update.
 */
class AgendaWidgetRepository(
    context: Context,
    private val api: PocketBaseApi,
    private val settings: SettingsRepository,
    private val json: Json,
) {
    private val context = context.applicationContext
    private val file = File(this.context.filesDir, "agenda-widget-snapshot.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** When the last fetch started, successful or not; spaces out automatic refreshes. */
    @Volatile
    private var lastAttemptAt = 0L

    private val _state = MutableStateFlow(
        readSnapshot().let { AgendaWidgetState(it, AgendaWidgetState.Status.READY) },
    )
    val state: StateFlow<AgendaWidgetState> = _state.asStateFlow()

    /**
     * Follows sign-in and sign-out: a new session refreshes the agenda, and
     * signing out clears it (iOS AuthStore.syncWidgetSession / clearSession).
     */
    fun followSession(authState: Flow<AuthState>) {
        scope.launch {
            authState
                .mapNotNull { state ->
                    when (state) {
                        is AuthState.LoggedIn -> state.user.id
                        AuthState.LoggedOut -> ""
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId.isEmpty()) clear() else refresh(force = true)
                }
        }
    }

    /** Refreshes in the background, e.g. when a widget renders or the app leaves the screen. */
    fun refreshInBackground(force: Boolean = false) {
        scope.launch { refresh(force) }
    }

    /**
     * Fetches a fresh agenda when any widget is placed. Without [force], skips
     * the network when the last attempt was under a minute ago — widget updates
     * that follow a fetch (or a failed one) would otherwise refetch in a loop.
     */
    suspend fun refresh(force: Boolean = false) {
        if (!hasWidgets()) return
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && now - lastAttemptAt < FRESHNESS_MILLIS) return
            lastAttemptAt = now

            val token = settings.authToken.first()
            val server = settings.authTokenServer.first()
            val userId = settings.authUserId.first()
            if (token.isBlank() || server.isBlank() || userId.isBlank()) {
                setSignedOut()
                return
            }

            _state.update { it.copy(isRefreshing = true) }
            notifyWidgets()
            val result = try {
                withTimeoutOrNull(FETCH_TIMEOUT_MILLIS) {
                    Result.success(api.getCalendarSnapshot(server, token, userId))
                } ?: Result.failure(ApiException(0, "Timed out"))
            } catch (e: CancellationException) {
                _state.update { it.copy(isRefreshing = false) }
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }

            // Signed out (or switched accounts) while the request was in flight.
            if (settings.authToken.first() != token) {
                _state.update { it.copy(isRefreshing = false) }
                notifyWidgets()
                return
            }

            result.fold(
                onSuccess = { calendar ->
                    val snapshot = AgendaWidgetSnapshot.from(calendar)
                    writeSnapshot(snapshot)
                    _state.value = AgendaWidgetState(snapshot, AgendaWidgetState.Status.READY)
                },
                onFailure = { error ->
                    if (error is ApiException && error.isAuthFailure) {
                        setSignedOut()
                        return
                    }
                    // Keep showing the cached agenda when the server is unreachable.
                    _state.update {
                        it.copy(
                            status = if (it.snapshot == null) {
                                AgendaWidgetState.Status.UNAVAILABLE
                            } else {
                                AgendaWidgetState.Status.READY
                            },
                            isRefreshing = false,
                        )
                    }
                },
            )
            notifyWidgets()
        }
    }

    /**
     * Hands a Calendar snapshot the app just loaded from the server to the
     * widget, updating it only when something changed.
     */
    fun publish(calendar: CalendarSnapshot) {
        scope.launch {
            mutex.withLock {
                val snapshot = AgendaWidgetSnapshot.from(calendar)
                val changed = !snapshot.hasSameItems(_state.value.snapshot) ||
                    _state.value.status != AgendaWidgetState.Status.READY
                writeSnapshot(snapshot)
                lastAttemptAt = snapshot.updatedAt
                _state.update { it.copy(snapshot = snapshot, status = AgendaWidgetState.Status.READY) }
                if (changed) notifyWidgets()
            }
        }
    }

    /** Removes the cached agenda, e.g. after sign-out. */
    private suspend fun clear() {
        mutex.withLock { setSignedOut() }
    }

    private suspend fun setSignedOut() {
        file.delete()
        _state.value = AgendaWidgetState(null, AgendaWidgetState.Status.SIGNED_OUT)
        notifyWidgets()
    }

    private suspend fun hasWidgets(): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(AgendaWidget::class.java).isNotEmpty()

    private suspend fun notifyWidgets() {
        runCatching { AgendaWidget().updateAll(context) }
    }

    private fun readSnapshot(): AgendaWidgetSnapshot? =
        runCatching {
            json.decodeFromString(AgendaWidgetSnapshot.serializer(), file.readText())
        }.getOrNull()

    private fun writeSnapshot(snapshot: AgendaWidgetSnapshot) {
        runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(AgendaWidgetSnapshot.serializer(), snapshot))
            temp.renameTo(file)
        }
    }

    private companion object {
        const val FRESHNESS_MILLIS = 60_000L
        const val FETCH_TIMEOUT_MILLIS = 20_000L
    }
}
