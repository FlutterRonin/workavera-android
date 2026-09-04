package com.workavera.android.data

import com.workavera.android.data.model.ArchivedBoardTaskPage
import com.workavera.android.data.model.BoardPage
import com.workavera.android.data.model.BoardProjectShare
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

    suspend fun updateStartDate(id: String, startDate: Instant?) =
        api.updateBoardTaskStartDate(session.server, session.token, id, startDate)

    // MARK: - Project public links

    /** Share state is never cached: a stale link would be shown as live long after it was revoked. */
    suspend fun loadShare(projectId: String): BoardProjectShare? =
        api.getBoardProjectShare(session.server, session.token, projectId)

    suspend fun publishShare(projectId: String, expires: String): BoardProjectShare =
        api.publishBoardProject(session.server, session.token, projectId, expires)

    suspend fun unpublishShare(projectId: String) =
        api.unpublishBoardProject(session.server, session.token, projectId)

    /** Public address of a shared project's preview, as a reader would open it in a browser. */
    fun publicShareUrl(share: BoardProjectShare): String =
        "${session.server.trimEnd('/')}/p/${share.slug}"

    suspend fun archiveTask(id: String) =
        api.setBoardTaskArchived(session.server, session.token, id, archived = true)

    suspend fun restoreTask(id: String) =
        api.setBoardTaskArchived(session.server, session.token, id, archived = false)

    /** One page of a project's archived tasks; page is 1-based. */
    suspend fun loadArchivedTasks(
        projectId: String,
        page: Int,
        pageSize: Int,
    ): ArchivedBoardTaskPage =
        api.getArchivedBoardTasks(session.server, session.token, projectId, page, pageSize)

    /** Board realtime signal stream (payloads ignored; refresh trigger only). */
    fun realtimeSignals(): Flow<Unit> =
        api.boardRealtimeSignals(session.server, session.token)
}
