package com.workavera.android.data

import com.workavera.android.data.model.CalendarEvent
import com.workavera.android.data.model.CalendarEventInput
import com.workavera.android.data.model.CalendarSnapshot
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import kotlinx.coroutines.flow.Flow

/** Calendar data entry point: session passthrough + disk cache for the snapshot. */
class CalendarRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    private val cacheKey: String
        get() = "calendar|${session.server}|${session.user.id}"

    /** Cached snapshot rendered first when entering the screen; null when absent. */
    fun cachedSnapshot(): CalendarSnapshot? =
        cache.read(cacheKey, CalendarSnapshot.serializer())

    /** Network load, writing through to the disk cache on success. */
    suspend fun loadSnapshot(): CalendarSnapshot {
        val snapshot = api.getCalendarSnapshot(session.server, session.token)
        cache.write(cacheKey, CalendarSnapshot.serializer(), snapshot)
        return snapshot
    }

    suspend fun create(input: CalendarEventInput): CalendarEvent =
        api.createCalendarEvent(session.server, session.token, session.user.id, input)

    suspend fun update(id: String, input: CalendarEventInput): CalendarEvent =
        api.updateCalendarEvent(session.server, session.token, id, input)

    suspend fun delete(id: String) =
        api.deleteCalendarEvent(session.server, session.token, id)

    /** Realtime signal stream (payloads ignored; refresh trigger only). */
    fun realtimeSignals(): Flow<Unit> =
        api.calendarRealtimeSignals(session.server, session.token)
}
