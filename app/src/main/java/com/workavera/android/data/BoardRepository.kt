package com.workavera.android.data

import com.workavera.android.data.model.BoardPage
import com.workavera.android.data.model.TaskPriority
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Board data entry point: pulls server/token from the current session and forwards to [PocketBaseApi]. */
class BoardRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    private fun cacheKey(page: Int): String =
        "board|${session.server}|${session.user.id}|page=$page"

    /** Writes through to the disk cache after a successful network load. page is 1-based. */
    suspend fun loadPage(page: Int, pageSize: Int): BoardPage {
        val loaded = api.getBoardPage(session.server, session.token, session.user.id, page, pageSize)
        cache.write(cacheKey(page), BoardPage.serializer(), loaded)
        return loaded
    }

    /** Cached snapshot rendered first when entering the screen; null when absent. */
    fun cachedPage(page: Int): BoardPage? =
        cache.read(cacheKey(page), BoardPage.serializer())

    suspend fun moveTask(id: String, stateId: String, rank: Double) =
        api.moveBoardTask(session.server, session.token, id, stateId, rank)

    suspend fun updatePriority(id: String, priority: TaskPriority) =
        api.updateBoardTaskPriority(session.server, session.token, id, priority)

    suspend fun updateDueDate(id: String, dueDate: Instant?) =
        api.updateBoardTaskDueDate(session.server, session.token, id, dueDate)

    /** Board realtime signal stream (payloads ignored; refresh trigger only). */
    fun realtimeSignals(): Flow<Unit> =
        api.boardRealtimeSignals(session.server, session.token)
}
