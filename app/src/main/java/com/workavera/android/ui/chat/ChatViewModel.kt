package com.workavera.android.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.AiConsentRepository
import com.workavera.android.data.ChatRepository
import com.workavera.android.data.LlmModelsRepository
import com.workavera.android.data.model.ChatConversation
import com.workavera.android.data.model.ChatConversationStatus
import com.workavera.android.data.model.ChatConversationUpdate
import com.workavera.android.data.model.ChatMessage
import com.workavera.android.data.model.ChatMessageMetadata
import com.workavera.android.data.model.ChatMessagePart
import com.workavera.android.data.model.ChatMessageRole
import com.workavera.android.data.model.ChatMessageStatus
import com.workavera.android.data.model.ChatStreamChunk
import com.workavera.android.data.model.ChatStreamReducer
import com.workavera.android.data.model.ChatStreamRequest
import com.workavera.android.data.model.LlmModel
import com.workavera.android.data.network.ApiException
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

data class ChatUiState(
    // Conversation list (active).
    val conversations: List<ChatConversation> = emptyList(),
    val query: String = "",
    val page: Int = 0,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    // Archived list.
    val archivedConversations: List<ChatConversation> = emptyList(),
    val archivedPage: Int = 0,
    val archivedTotalPages: Int = 1,
    val archivedTotalItems: Int = 0,
    val isLoadingArchived: Boolean = false,
    val hasLoadedArchived: Boolean = false,
    val archivedError: String? = null,
    // Mutations.
    val mutatingConversationIds: Set<String> = emptySet(),
    val isCreating: Boolean = false,
    // Per-conversation chat state.
    val messagesByConversationId: Map<String, List<ChatMessage>> = emptyMap(),
    val loadingMessageConversationIds: Set<String> = emptySet(),
    val activeRunIds: Map<String, String> = emptyMap(),
    val messageErrors: Map<String, String> = emptyMap(),
    val submittingApprovalIds: Set<String> = emptySet(),
    // LLM models for the composer picker (iOS LLMSettingsStore).
    val models: List<LlmModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val hasLoadedModels: Boolean = false,
) {
    fun messages(conversationId: String): List<ChatMessage> =
        messagesByConversationId[conversationId] ?: emptyList()

    fun isGenerating(conversationId: String): Boolean =
        activeRunIds.containsKey(conversationId)
}

/**
 * Chat state and actions, mirroring the iOS ChatStore: cache-first paged
 * conversation lists, optimistic sends, one SSE run per conversation with an
 * 80 ms UI flush throttle, run resume after reloads, and approval responses.
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val llmModelsRepository: LlmModelsRepository,
    private val aiConsent: AiConsentRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ChatUiState())
        private set

    private var loadSequence = 0
    private var archivedLoadSequence = 0
    private val streamJobs = mutableMapOf<String, Job>()

    private val currentUserId: String? get() = repository.currentUserId

    // ---- Conversation list ----

    /** Cache-first load when entering the screen (iOS loadForPresentation). */
    fun loadForPresentation() {
        if (uiState.hasLoaded || uiState.isLoading) return
        repository.cachedPage(archived = false, query = uiState.query, page = uiState.page + 1)
            ?.let { cached ->
                uiState = uiState.copy(
                    conversations = cached.items,
                    page = maxOf(0, cached.page - 1),
                    totalPages = cached.totalPages,
                    totalItems = cached.totalItems,
                    hasLoaded = true,
                )
            }
        viewModelScope.launch { doRefresh() }
    }

    fun refresh() {
        if (uiState.isLoading) return
        viewModelScope.launch { doRefresh() }
    }

    fun loadPage(newPage: Int) {
        if (uiState.isLoading) return
        viewModelScope.launch { doRefresh(newPage.coerceIn(0, uiState.totalPages - 1)) }
    }

    fun search(newQuery: String) {
        val normalized = newQuery.trim()
        if (uiState.query == normalized) return
        uiState = uiState.copy(query = normalized, page = 0, totalPages = 1)
        viewModelScope.launch { doRefresh(requestedPage = 0) }
    }

    private suspend fun doRefresh(requestedPage: Int? = null) {
        val targetPage = maxOf(0, minOf(requestedPage ?: uiState.page, uiState.totalPages - 1))
        loadSequence += 1
        val sequence = loadSequence
        uiState = uiState.copy(isLoading = true)
        try {
            val result = repository.loadConversations(
                page = targetPage + 1,
                pageSize = PAGE_SIZE,
                query = uiState.query,
                archived = false,
            )
            if (sequence != loadSequence) return
            uiState = uiState.copy(
                conversations = result.items,
                page = maxOf(0, result.page - 1),
                totalPages = maxOf(1, result.totalPages),
                totalItems = result.totalItems,
                isLoading = false,
                hasLoaded = true,
                error = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (sequence != loadSequence) return
            uiState = uiState.copy(
                isLoading = false,
                hasLoaded = true,
                error = message(e, "Could not load the conversations."),
            )
        }
    }

    // ---- Archived list ----

    fun loadArchived(requestedPage: Int? = null) {
        if (uiState.isLoadingArchived) return
        viewModelScope.launch { doLoadArchived(requestedPage) }
    }

    private suspend fun doLoadArchived(requestedPage: Int? = null) {
        val targetPage = maxOf(
            0,
            minOf(requestedPage ?: uiState.archivedPage, uiState.archivedTotalPages - 1),
        )
        archivedLoadSequence += 1
        val sequence = archivedLoadSequence
        uiState = uiState.copy(isLoadingArchived = true)
        try {
            val result = repository.loadConversations(
                page = targetPage + 1,
                pageSize = ARCHIVED_PAGE_SIZE,
                query = "",
                archived = true,
            )
            if (sequence != archivedLoadSequence) return
            uiState = uiState.copy(
                archivedConversations = result.items,
                archivedPage = maxOf(0, result.page - 1),
                archivedTotalPages = maxOf(1, result.totalPages),
                archivedTotalItems = result.totalItems,
                isLoadingArchived = false,
                hasLoadedArchived = true,
                archivedError = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (sequence != archivedLoadSequence) return
            uiState = uiState.copy(
                isLoadingArchived = false,
                hasLoadedArchived = true,
                archivedError = message(e, "Could not load the archived conversations."),
            )
        }
    }

    // ---- Create / mutate conversations ----

    /** Creates a conversation and hands it to the caller for navigation (iOS create). */
    fun create(onCreated: (ChatConversation) -> Unit) {
        if (uiState.isCreating) return
        uiState = uiState.copy(isCreating = true)
        viewModelScope.launch {
            try {
                val created = repository.createConversation("New conversation")
                val totalItems = uiState.totalItems + 1
                uiState = uiState.copy(
                    query = "",
                    page = 0,
                    conversations = listOf(created) +
                        uiState.conversations.filter { it.id != created.id },
                    totalItems = totalItems,
                    totalPages = pageCount(totalItems, PAGE_SIZE),
                    isCreating = false,
                    hasLoaded = true,
                    error = null,
                )
                // The list isn't refreshed here: the created conversation is shown
                // optimistically and the caller navigates straight into it.
                onCreated(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isCreating = false,
                    error = message(e, "Could not create the conversation."),
                )
            }
        }
    }

    fun rename(conversation: ChatConversation, title: String) {
        val normalized = title.trim()
        if (normalized.isEmpty() || normalized == conversation.title) return
        if (normalized.length > 200) {
            uiState = uiState.copy(error = "Conversation titles cannot exceed 200 characters.")
            return
        }
        mutate(conversation) {
            repository.updateConversation(it.id, ChatConversationUpdate(title = normalized))
        }
    }

    fun togglePin(conversation: ChatConversation) {
        mutate(conversation, sortActive = true) {
            repository.updateConversation(it.id, ChatConversationUpdate(pinned = !it.pinned))
        }
    }

    fun archive(conversation: ChatConversation, onComplete: (Boolean) -> Unit = {}) {
        if (conversation.id in uiState.mutatingConversationIds) {
            onComplete(false)
            return
        }
        uiState = uiState.copy(mutatingConversationIds = uiState.mutatingConversationIds + conversation.id)
        viewModelScope.launch {
            try {
                val archived = repository.updateConversation(
                    conversation.id,
                    ChatConversationUpdate(status = ChatConversationStatus.ARCHIVED.raw),
                )
                removeActiveConversation(conversation.id)
                if (uiState.hasLoadedArchived) {
                    uiState = uiState.copy(
                        archivedConversations = listOf(archived) +
                            uiState.archivedConversations.filter { it.id != archived.id },
                        archivedTotalItems = uiState.archivedTotalItems + 1,
                        archivedTotalPages = pageCount(
                            uiState.archivedTotalItems + 1,
                            ARCHIVED_PAGE_SIZE,
                        ),
                        error = null,
                    )
                }
                fillActivePageIfNeeded()
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not archive the conversation."))
                onComplete(false)
            } finally {
                uiState = uiState.copy(
                    mutatingConversationIds = uiState.mutatingConversationIds - conversation.id,
                )
            }
        }
    }

    fun unarchive(conversation: ChatConversation) {
        if (conversation.id in uiState.mutatingConversationIds) return
        uiState = uiState.copy(mutatingConversationIds = uiState.mutatingConversationIds + conversation.id)
        viewModelScope.launch {
            try {
                val active = repository.updateConversation(
                    conversation.id,
                    ChatConversationUpdate(status = ChatConversationStatus.ACTIVE.raw),
                )
                removeArchivedConversation(conversation.id)
                val totalItems = uiState.totalItems + 1
                uiState = uiState.copy(
                    conversations = (listOf(active) +
                        uiState.conversations.filter { it.id != active.id })
                        .sortedWith(activeSort),
                    totalItems = totalItems,
                    totalPages = pageCount(totalItems, PAGE_SIZE),
                    hasLoaded = true,
                    archivedError = null,
                )
                fillArchivedPageIfNeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    archivedError = message(e, "Could not restore the conversation."),
                )
            } finally {
                uiState = uiState.copy(
                    mutatingConversationIds = uiState.mutatingConversationIds - conversation.id,
                )
            }
        }
    }

    /** Returns the outcome through [onComplete] so the UI can leave a deleted detail view. */
    fun delete(conversation: ChatConversation, onComplete: (Boolean) -> Unit = {}) {
        if (conversation.id in uiState.mutatingConversationIds) {
            onComplete(false)
            return
        }
        uiState = uiState.copy(mutatingConversationIds = uiState.mutatingConversationIds + conversation.id)
        viewModelScope.launch {
            try {
                repository.deleteConversation(conversation.id)
                streamJobs.remove(conversation.id)?.cancel()
                uiState = uiState.copy(
                    messagesByConversationId = uiState.messagesByConversationId - conversation.id,
                    activeRunIds = uiState.activeRunIds - conversation.id,
                    messageErrors = uiState.messageErrors - conversation.id,
                )
                if (conversation.statusEnum == ChatConversationStatus.ARCHIVED) {
                    removeArchivedConversation(conversation.id)
                    fillArchivedPageIfNeeded()
                } else {
                    removeActiveConversation(conversation.id)
                    fillActivePageIfNeeded()
                }
                uiState = uiState.copy(error = null, archivedError = null)
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMessage = message(e, "Could not delete the conversation.")
                uiState = if (conversation.statusEnum == ChatConversationStatus.ARCHIVED) {
                    uiState.copy(archivedError = errorMessage)
                } else {
                    uiState.copy(error = errorMessage)
                }
                onComplete(false)
            } finally {
                uiState = uiState.copy(
                    mutatingConversationIds = uiState.mutatingConversationIds - conversation.id,
                )
            }
        }
    }

    private fun mutate(
        conversation: ChatConversation,
        sortActive: Boolean = false,
        operation: suspend (ChatConversation) -> ChatConversation,
    ) {
        if (conversation.id in uiState.mutatingConversationIds) return
        uiState = uiState.copy(mutatingConversationIds = uiState.mutatingConversationIds + conversation.id)
        viewModelScope.launch {
            try {
                val updated = operation(conversation)
                replace(updated)
                uiState = uiState.copy(
                    error = null,
                    conversations = if (sortActive) {
                        uiState.conversations.sortedWith(activeSort)
                    } else {
                        uiState.conversations
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not update the conversation."))
            } finally {
                uiState = uiState.copy(
                    mutatingConversationIds = uiState.mutatingConversationIds - conversation.id,
                )
            }
        }
    }

    // ---- Messages & streaming ----

    fun loadMessages(conversationId: String, force: Boolean = false) {
        viewModelScope.launch { doLoadMessages(conversationId, force) }
    }

    /**
     * Loads persisted history; when the tail is a still-streaming assistant
     * message, drops it and re-attaches to the run stream (iOS loadMessages).
     */
    private suspend fun doLoadMessages(
        conversationId: String,
        force: Boolean = false,
        recoverActiveRun: Boolean = true,
    ) {
        if (!force && uiState.messagesByConversationId.containsKey(conversationId)) return
        if (conversationId in uiState.loadingMessageConversationIds) return
        uiState = uiState.copy(
            loadingMessageConversationIds = uiState.loadingMessageConversationIds + conversationId,
        )
        try {
            val loaded = repository.loadMessages(conversationId)
            setMessageError(conversationId, null)
            val last = loaded.lastOrNull()
            val lastStatus = last?.metadata?.statusEnum
            val runId = last?.metadata?.runID
            if (recoverActiveRun &&
                last != null &&
                last.roleEnum == ChatMessageRole.ASSISTANT &&
                lastStatus == ChatMessageStatus.STREAMING &&
                runId != null
            ) {
                updateMessages(conversationId, loaded.dropLast(1))
                resumeRun(conversationId, runId)
            } else {
                updateMessages(conversationId, loaded)
                setActiveRun(conversationId, null)
                if (!recoverActiveRun && lastStatus == ChatMessageStatus.STREAMING) {
                    setMessageError(
                        conversationId,
                        "The response is still running, but its stream could not be reconnected.",
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setMessageError(conversationId, message(e, "Could not load the messages."))
        } finally {
            uiState = uiState.copy(
                loadingMessageConversationIds = uiState.loadingMessageConversationIds - conversationId,
            )
        }
    }

    /** Optimistic send: user message + empty streaming assistant message, then consume (iOS sendMessage). */
    fun sendMessage(text: String, conversationId: String, modelConfigId: String) {
        val content = text.trim()
        if (content.isEmpty() || modelConfigId.isEmpty()) return
        if (uiState.activeRunIds.containsKey(conversationId)) return

        val runId = UUID.randomUUID().toString().lowercase()
        val clientMessageId = UUID.randomUUID().toString().lowercase()
        val userMessage = ChatMessage(
            id = clientMessageId,
            role = ChatMessageRole.USER.raw,
            metadata = ChatMessageMetadata(
                conversationID = conversationId,
                status = ChatMessageStatus.COMPLETE.raw,
                createdAt = Instant.now().toString(),
            ),
            parts = mutableListOf(ChatMessagePart(type = "text", text = content, state = "done")),
        )
        val request = ChatStreamRequest(
            runID = runId,
            conversationID = conversationId,
            modelConfigID = modelConfigId,
            message = ChatMessage(
                id = clientMessageId,
                role = ChatMessageRole.USER.raw,
                parts = mutableListOf(ChatMessagePart(type = "text", text = content)),
            ),
        )

        try {
            val stream = repository.stream(request)
            setMessageError(conversationId, null)
            setActiveRun(conversationId, runId)
            val reducer = ChatStreamReducer(messageID = "stream-$runId", runID = runId)
            updateMessages(
                conversationId,
                uiState.messages(conversationId) + userMessage + snapshotOf(reducer.message),
            )
            consume(stream, reducer, conversationId, runId)

            val conversation = uiState.conversations.firstOrNull { it.id == conversationId }
            if (conversation != null && conversation.title == "New conversation") {
                rename(conversation, content.take(16))
            }
        } catch (e: Exception) {
            setActiveRun(conversationId, null)
            setMessageError(conversationId, message(e, "Could not send the message."))
        }
    }

    fun stopRun(conversationId: String) {
        val runId = uiState.activeRunIds[conversationId] ?: return
        viewModelScope.launch {
            try {
                repository.stopRun(runId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setMessageError(conversationId, message(e, "Could not stop the response."))
            }
        }
    }

    fun respondToApproval(conversationId: String, approvalId: String, approved: Boolean) {
        val runId = uiState.activeRunIds[conversationId] ?: return
        if (approvalId in uiState.submittingApprovalIds) return
        uiState = uiState.copy(submittingApprovalIds = uiState.submittingApprovalIds + approvalId)
        viewModelScope.launch {
            try {
                repository.respondApproval(runId, approvalId, approved)
                setMessageError(conversationId, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setMessageError(conversationId, message(e, "Could not submit the approval."))
            } finally {
                uiState = uiState.copy(
                    submittingApprovalIds = uiState.submittingApprovalIds - approvalId,
                )
            }
        }
    }

    private fun resumeRun(conversationId: String, runId: String) {
        if (streamJobs.containsKey(conversationId)) return
        try {
            val stream = repository.resumeStream(runId)
            setActiveRun(conversationId, runId)
            consume(
                stream,
                ChatStreamReducer(messageID = "stream-$runId", runID = runId),
                conversationId,
                runId,
            )
        } catch (e: Exception) {
            setActiveRun(conversationId, null)
            setMessageError(conversationId, message(e, "Could not resume the response."))
        }
    }

    /**
     * Consumes one run's chunk stream: applies chunks to the reducer and
     * flushes to UI state at most once per 80 ms (first chunk immediately,
     * one forced flush at the tail). When the stream ends, the authoritative
     * persisted messages are reloaded and the conversation list refreshed.
     */
    private fun consume(
        stream: Flow<ChatStreamChunk>,
        reducer: ChatStreamReducer,
        conversationId: String,
        runId: String,
    ) {
        if (streamJobs.containsKey(conversationId)) return
        streamJobs[conversationId] = viewModelScope.launch {
            try {
                var lastFlushAt = 0L
                stream.collect { chunk ->
                    if (uiState.activeRunIds[conversationId] != runId) {
                        throw CancellationException("The run is no longer active.")
                    }
                    reducer.apply(chunk)
                    // Tool state boundaries are exempt because execution can
                    // leave no later chunk to flush a skipped pending card.
                    // High-frequency text, reasoning, and tool-input deltas
                    // still pass through the throttle.
                    val mustRender = chunk.requiresImmediateRender
                    val now = System.currentTimeMillis()
                    if (!mustRender &&
                        lastFlushAt != 0L &&
                        now - lastFlushAt < STREAM_FLUSH_INTERVAL_MS
                    ) {
                        return@collect
                    }
                    lastFlushAt = now
                    upsertStreamMessage(snapshotOf(reducer.message), conversationId, runId)
                }
                // Flush whatever the throttle may have skipped at the tail end.
                upsertStreamMessage(snapshotOf(reducer.message), conversationId, runId)
                setMessageError(conversationId, null)
            } catch (e: CancellationException) {
                streamJobs.remove(conversationId)
                throw e
            } catch (e: Exception) {
                val failed = snapshotOf(reducer.message)
                if (failed.metadata?.statusEnum == ChatMessageStatus.STREAMING) {
                    failed.metadata?.status = ChatMessageStatus.ERROR.raw
                }
                upsertStreamMessage(failed, conversationId, runId)
                setMessageError(conversationId, message(e, "The chat run failed."))
            }

            streamJobs.remove(conversationId)
            if (uiState.activeRunIds[conversationId] != runId) return@launch
            setActiveRun(conversationId, null)
            doLoadMessages(conversationId, force = true, recoverActiveRun = false)
            doRefresh()
        }
    }

    private fun upsertStreamMessage(
        message: ChatMessage,
        conversationId: String,
        runId: String,
    ) {
        val messages = uiState.messages(conversationId)
        val index = messages.indexOfFirst {
            it.metadata?.runID == runId || it.id == "stream-$runId"
        }
        updateMessages(
            conversationId,
            if (index >= 0) {
                messages.toMutableList().apply { set(index, message) }
            } else {
                messages + message
            },
        )
    }

    /**
     * The reducer mutates its message between flushes, so state always stores
     * a snapshot; Compose would otherwise observe a silently-mutated object.
     */
    private fun snapshotOf(message: ChatMessage): ChatMessage =
        message.copy(
            metadata = message.metadata?.copy(),
            parts = message.parts.map { part ->
                part.copy(approval = part.approval?.copy())
            }.toMutableList(),
        )

    // ---- Models & AI consent ----

    /** Cache-first model list load for the composer picker (iOS loadModelsForPresentation). */
    fun loadModelsIfNeeded() {
        if (uiState.hasLoadedModels || uiState.isLoadingModels) return
        llmModelsRepository.cachedModels()?.let { cached ->
            uiState = uiState.copy(models = cached, hasLoadedModels = true)
        }
        uiState = uiState.copy(isLoadingModels = true)
        viewModelScope.launch {
            try {
                val models = llmModelsRepository.loadModels()
                uiState = uiState.copy(
                    models = models,
                    isLoadingModels = false,
                    hasLoadedModels = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The composer simply stays disabled without a model list.
                uiState = uiState.copy(isLoadingModels = false, hasLoadedModels = true)
            }
        }
    }

    /** Model for a conversation: its configured model, else the default, else the first. */
    fun preferredModel(conversation: ChatConversation?): LlmModel? =
        conversation?.modelConfigID?.let { id -> uiState.models.firstOrNull { it.id == id } }
            ?: uiState.models.firstOrNull { it.isDefault }
            ?: uiState.models.firstOrNull()

    fun isAiConsentAuthorized(modelId: String): Boolean {
        val userId = currentUserId ?: return false
        return aiConsent.isAuthorized(userId, modelId)
    }

    fun authorizeAiConsent(modelId: String) {
        val userId = currentUserId ?: return
        aiConsent.authorize(userId, modelId)
    }

    /** File URL for an avatar surfaced by a tool card; contacts default to the users collection. */
    fun toolFileUrl(collectionId: String?, recordId: String, fileName: String): String? =
        repository.fileUrl(collectionId ?: "users", recordId, fileName)

    // ---- Error helpers ----

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    fun dismissArchivedError() {
        uiState = uiState.copy(archivedError = null)
    }

    fun dismissMessageError(conversationId: String) {
        setMessageError(conversationId, null)
    }

    // ---- Private state helpers ----

    private fun replace(conversation: ChatConversation) {
        val activeIndex = uiState.conversations.indexOfFirst { it.id == conversation.id }
        val archivedIndex = uiState.archivedConversations.indexOfFirst { it.id == conversation.id }
        uiState = uiState.copy(
            conversations = if (activeIndex >= 0) {
                uiState.conversations.toMutableList().apply { set(activeIndex, conversation) }
            } else {
                uiState.conversations
            },
            archivedConversations = if (archivedIndex >= 0) {
                uiState.archivedConversations.toMutableList().apply {
                    set(archivedIndex, conversation)
                }
            } else {
                uiState.archivedConversations
            },
        )
    }

    private fun removeActiveConversation(id: String) {
        if (uiState.conversations.none { it.id == id }) return
        val totalItems = maxOf(0, uiState.totalItems - 1)
        uiState = uiState.copy(
            conversations = uiState.conversations.filter { it.id != id },
            totalItems = totalItems,
            totalPages = pageCount(totalItems, PAGE_SIZE),
        )
    }

    private fun removeArchivedConversation(id: String) {
        if (uiState.archivedConversations.none { it.id == id }) return
        val totalItems = maxOf(0, uiState.archivedTotalItems - 1)
        uiState = uiState.copy(
            archivedConversations = uiState.archivedConversations.filter { it.id != id },
            archivedTotalItems = totalItems,
            archivedTotalPages = pageCount(totalItems, ARCHIVED_PAGE_SIZE),
        )
    }

    private suspend fun fillActivePageIfNeeded() {
        if (uiState.conversations.isEmpty() && uiState.page > 0) {
            doRefresh(requestedPage = uiState.page - 1)
        } else {
            doRefresh(requestedPage = uiState.page)
        }
    }

    private suspend fun fillArchivedPageIfNeeded() {
        if (uiState.archivedConversations.isEmpty() && uiState.archivedPage > 0) {
            doLoadArchived(requestedPage = uiState.archivedPage - 1)
        } else {
            doLoadArchived(requestedPage = uiState.archivedPage)
        }
    }

    private fun updateMessages(conversationId: String, messages: List<ChatMessage>) {
        uiState = uiState.copy(
            messagesByConversationId = uiState.messagesByConversationId +
                (conversationId to messages),
        )
    }

    private fun setActiveRun(conversationId: String, runId: String?) {
        uiState = uiState.copy(
            activeRunIds = if (runId != null) {
                uiState.activeRunIds + (conversationId to runId)
            } else {
                uiState.activeRunIds - conversationId
            },
        )
    }

    private fun setMessageError(conversationId: String, error: String?) {
        uiState = uiState.copy(
            messageErrors = if (error != null) {
                uiState.messageErrors + (conversationId to error)
            } else {
                uiState.messageErrors - conversationId
            },
        )
    }

    private fun message(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        const val PAGE_SIZE = 20
        const val ARCHIVED_PAGE_SIZE = 10

        /** Minimum interval between UI refreshes while a response streams in. */
        private const val STREAM_FLUSH_INTERVAL_MS = 80L

        private fun pageCount(totalItems: Int, pageSize: Int): Int =
            maxOf(1, (totalItems + pageSize - 1) / pageSize)

        /**
         * Mirrors the server list order ("-pinned,-last_message_at,-updated"), so a
         * list reordered after a local mutation matches the next page the server
         * returns. A conversation without a last_message_at sorts below every dated
         * one, the way an empty date does on the server (iOS activeSort).
         */
        internal val activeSort =
            compareByDescending<ChatConversation> { it.pinned }
                .thenByDescending { it.lastMessageAtDate ?: Instant.EPOCH }
                .thenByDescending { it.updatedAt ?: Instant.EPOCH }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                ChatViewModel(
                    app.container.chatRepository,
                    app.container.llmModelsRepository,
                    app.container.aiConsentRepository,
                )
            }
        }
    }
}
