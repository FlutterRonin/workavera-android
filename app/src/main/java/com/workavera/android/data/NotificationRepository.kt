package com.workavera.android.data

import com.workavera.android.data.model.ModelShareStatus
import com.workavera.android.data.model.NotificationPage
import com.workavera.android.data.model.NotificationRealtimeEvent
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Snapshot of a notification page + unread count, the unit for both caching and refreshing. */
@Serializable
data class NotificationSnapshot(
    val page: NotificationPage,
    val unreadCount: Int,
)

/** Notification data entry point: pulls server/token from the current session and forwards to [PocketBaseApi]. */
class NotificationRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    private fun cacheKey(page: Int): String =
        "notifications|${session.server}|${session.user.id}|page=$page"

    /** Loads the list and unread count concurrently, writing through to the disk cache on success. page is 1-based. */
    suspend fun loadSnapshot(page: Int, pageSize: Int): NotificationSnapshot = coroutineScope {
        val pageDeferred = async { api.getNotifications(session.server, session.token, page, pageSize) }
        val unreadDeferred = async { api.getUnreadNotificationCount(session.server, session.token) }
        val snapshot = NotificationSnapshot(pageDeferred.await(), unreadDeferred.await())
        cache.write(cacheKey(page), NotificationSnapshot.serializer(), snapshot)
        snapshot
    }

    /** Cached snapshot rendered first when entering the screen; null when absent. */
    fun cachedSnapshot(page: Int): NotificationSnapshot? =
        cache.read(cacheKey(page), NotificationSnapshot.serializer())

    suspend fun markRead(id: String) =
        api.markNotificationRead(session.server, session.token, id)

    suspend fun markAllRead() =
        api.markAllNotificationsRead(session.server, session.token)

    suspend fun respondToModelShare(id: String, accept: Boolean): ModelShareStatus {
        val raw = api.respondToModelShare(
            session.server,
            session.token,
            id,
            decision = if (accept) "accept" else "reject",
        )
        return ModelShareStatus.fromRaw(raw) ?: ModelShareStatus.PENDING
    }

    suspend fun archive(id: String) =
        api.archiveNotification(session.server, session.token, id)

    suspend fun delete(id: String) =
        api.deleteNotification(session.server, session.token, id)

    /** Realtime notification stream; reads the current session per call, so reconnects naturally pick up the latest token. */
    fun realtimeEvents(): Flow<NotificationRealtimeEvent> =
        api.realtimeNotificationEvents(session.server, session.token)
}
