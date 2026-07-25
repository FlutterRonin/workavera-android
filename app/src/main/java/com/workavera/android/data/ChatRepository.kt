package com.workavera.android.data

import com.workavera.android.data.model.ChatConversation
import com.workavera.android.data.model.ChatConversationUpdate
import com.workavera.android.data.model.ChatConversationsPage
import com.workavera.android.data.model.ChatMessage
import com.workavera.android.data.model.ChatStreamChunk
import com.workavera.android.data.model.ChatStreamRequest
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import kotlinx.coroutines.flow.Flow

/** Chat data entry point: session passthrough + disk cache for conversation pages. */
class ChatRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    /** Signed-in user id without throwing; used for the AI consent key. */
    val currentUserId: String?
        get() = (authRepository.state.value as? AuthState.LoggedIn)?.user?.id

    private fun pageCacheKey(archived: Boolean, query: String, page: Int) =
        "chat.page|${session.server}|${session.user.id}|$archived|${query.trim()}|$page"

    /** Cached page rendered first when entering the screen; null when absent. */
    fun cachedPage(archived: Boolean, query: String, page: Int): ChatConversationsPage? =
        runCatching {
            cache.read(pageCacheKey(archived, query, page), ChatConversationsPage.serializer())
        }.getOrNull()

    /** Network load, writing through to the disk cache on success. [page] is 1-based. */
    suspend fun loadConversations(
        page: Int,
        pageSize: Int,
        query: String,
        archived: Boolean,
    ): ChatConversationsPage {
        val result = api.getChatConversations(
            session.server, session.token, page, pageSize, query, archived,
        )
        cache.write(
            pageCacheKey(archived, query, page),
            ChatConversationsPage.serializer(),
            result,
        )
        return result
    }

    suspend fun createConversation(title: String): ChatConversation =
        api.createChatConversation(session.server, session.token, title)

    suspend fun updateConversation(id: String, update: ChatConversationUpdate): ChatConversation =
        api.updateChatConversation(session.server, session.token, id, update)

    suspend fun deleteConversation(id: String) =
        api.deleteChatConversation(session.server, session.token, id)

    suspend fun loadMessages(conversationId: String): List<ChatMessage> =
        api.getChatMessages(session.server, session.token, conversationId)

    fun stream(request: ChatStreamRequest): Flow<ChatStreamChunk> =
        api.chatStream(session.server, session.token, request)

    fun resumeStream(runId: String): Flow<ChatStreamChunk> =
        api.resumeChatStream(session.server, session.token, runId)

    suspend fun stopRun(runId: String) =
        api.stopChatRun(session.server, session.token, runId)

    suspend fun respondApproval(runId: String, approvalId: String, approved: Boolean) =
        api.respondChatApproval(session.server, session.token, runId, approvalId, approved)

    /** PocketBase file URL for a record asset surfaced by a chat tool card (iOS auth.fileURL). */
    fun fileUrl(collectionId: String, recordId: String, fileName: String): String? =
        (authRepository.state.value as? AuthState.LoggedIn)
            ?.let { api.fileUrl(it.server, collectionId, recordId, fileName) }
}
