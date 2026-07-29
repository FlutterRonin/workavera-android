package com.workavera.android.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Mirrors the iOS Workavera/Models/ChatMessage.swift + ChatConversation.swift models.
// The iOS JSONValue enum maps to kotlinx-serialization's JsonElement here.

private val chatJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private val chatPrettyJson = Json {
    prettyPrint = true
}

/** Tool outputs may arrive either as a JSON object or as a string holding serialized JSON. */
fun JsonElement.decodingEmbeddedJson(): JsonElement {
    val string = (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return this
    return try {
        chatJson.parseToJsonElement(string)
    } catch (_: Exception) {
        this
    }
}

/** Pretty-printed JSON with sorted keys, matching iOS JSONValue.prettyPrinted. */
fun JsonElement.prettyPrintedJson(): String = try {
    chatPrettyJson.encodeToString(JsonElement.serializer(), sortedDeep())
} catch (_: Exception) {
    toString()
}

private fun JsonElement.sortedDeep(): JsonElement = when (this) {
    is JsonObject -> JsonObject(
        entries
            .sortedBy { it.key }
            .associate { it.key to it.value.sortedDeep() },
    )
    is JsonArray -> JsonArray(map { it.sortedDeep() })
    else -> this
}

enum class ChatMessageRole(val raw: String) {
    USER("user"),
    ASSISTANT("assistant");

    companion object {
        fun fromRaw(raw: String?): ChatMessageRole =
            entries.firstOrNull { it.raw == raw } ?: USER
    }
}

enum class ChatMessageStatus(val raw: String) {
    PENDING("pending"),
    STREAMING("streaming"),
    COMPLETE("complete"),
    ERROR("error"),
    CANCELLED("cancelled");

    companion object {
        fun fromRaw(raw: String?): ChatMessageStatus? =
            entries.firstOrNull { it.raw == raw }
    }
}

@Serializable
data class ChatModelSnapshot(
    val name: String = "",
    @SerialName("configId") val configID: String = "",
    @SerialName("modelId") val modelID: String = "",
    @SerialName("protocol") val protocolName: String = "",
)

@Serializable
data class ChatTokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val cacheCreationTokens: Int = 0,
    val cacheReadTokens: Int = 0,
)

@Serializable
data class ChatSafeError(
    val code: String = "",
    val message: String = "",
)

@Serializable
data class ChatMessageMetadata(
    @SerialName("conversationId") var conversationID: String? = null,
    var status: String? = null,
    @SerialName("runId") var runID: String? = null,
    var model: ChatModelSnapshot? = null,
    var usage: ChatTokenUsage? = null,
    var contextUsage: ChatTokenUsage? = null,
    var contextTokens: Int? = null,
    var contextTokensEstimated: Boolean? = null,
    var finishReason: String? = null,
    var stepCount: Int? = null,
    var createdAt: String? = null,
    var updatedAt: String? = null,
    var error: ChatSafeError? = null,
) {
    val statusEnum: ChatMessageStatus? get() = ChatMessageStatus.fromRaw(status)
    val createdDate: Instant? get() = createdAt?.let { parseServerDate(it) }

    /** Field-wise merge; a non-null field in [update] wins (mirrors iOS metadata.merge). */
    fun merge(update: ChatMessageMetadata?) {
        if (update == null) return
        conversationID = update.conversationID ?: conversationID
        status = update.status ?: status
        runID = update.runID ?: runID
        model = update.model ?: model
        usage = update.usage ?: usage
        contextUsage = update.contextUsage ?: contextUsage
        contextTokens = update.contextTokens ?: contextTokens
        contextTokensEstimated = update.contextTokensEstimated ?: contextTokensEstimated
        finishReason = update.finishReason ?: finishReason
        stepCount = update.stepCount ?: stepCount
        createdAt = update.createdAt ?: createdAt
        updatedAt = update.updatedAt ?: updatedAt
        error = update.error ?: error
    }
}

@Serializable
data class ChatToolApprovalState(
    val id: String,
    var approved: Boolean? = null,
)

/**
 * One part of a chat message. The [type] string discriminates:
 * text, reasoning, step-start, dynamic-tool, source-url, source-document,
 * file, and data-* (generic data parts).
 */
@Serializable
data class ChatMessagePart(
    var type: String,
    var text: String? = null,
    var state: String? = null,
    @SerialName("toolCallId") var toolCallID: String? = null,
    var toolName: String? = null,
    var input: JsonElement? = null,
    var output: JsonElement? = null,
    var errorText: String? = null,
    var approval: ChatToolApprovalState? = null,
    @SerialName("sourceId") var sourceID: String? = null,
    var url: String? = null,
    var title: String? = null,
    var mediaType: String? = null,
    var filename: String? = null,
    var data: JsonElement? = null,
    var id: String? = null,
)

@Serializable
data class ChatMessage(
    var id: String,
    var role: String,
    var metadata: ChatMessageMetadata? = null,
    var parts: MutableList<ChatMessagePart> = mutableListOf(),
) {
    val roleEnum: ChatMessageRole get() = ChatMessageRole.fromRaw(role)
}

@Serializable
data class ChatStreamRequest(
    @SerialName("runId") val runID: String,
    @SerialName("conversationId") val conversationID: String,
    @SerialName("modelConfigId") val modelConfigID: String,
    val message: ChatMessage,
)

/** Flat decode of every possible SSE chunk field of the chat stream protocol. */
@Serializable
data class ChatStreamChunk(
    val type: String,
    val id: String? = null,
    val delta: String? = null,
    @SerialName("messageId") val messageID: String? = null,
    @SerialName("toolCallId") val toolCallID: String? = null,
    val toolName: String? = null,
    @SerialName("approvalId") val approvalID: String? = null,
    val approved: Boolean? = null,
    val inputTextDelta: String? = null,
    val input: JsonElement? = null,
    val output: JsonElement? = null,
    val errorText: String? = null,
    val reason: String? = null,
    @SerialName("sourceId") val sourceID: String? = null,
    val url: String? = null,
    val title: String? = null,
    val mediaType: String? = null,
    val filename: String? = null,
    val finishReason: String? = null,
    val messageMetadata: ChatMessageMetadata? = null,
    val data: JsonElement? = null,
) {
    /**
     * Whether this chunk must bypass the UI stream throttle. Tool lifecycle
     * boundaries are sparse and can be followed by a long execution pause, so
     * delaying one can hide the pending card until the result arrives. Tool
     * input deltas remain throttled because they may arrive for every token.
     */
    val requiresImmediateRender: Boolean
        get() = when (type) {
            "tool-input-start",
            "tool-input-available",
            "tool-input-error",
            "tool-approval-request",
            "tool-approval-response",
            "tool-output-available",
            "tool-output-error",
            "tool-output-denied",
            -> true
            else -> type.startsWith("data-")
        }
}

/**
 * Pure state machine that builds an assistant [ChatMessage] from stream chunks.
 * Ported 1:1 from the iOS ChatStreamReducer.
 */
class ChatStreamReducer(messageID: String, runID: String) {

    var message: ChatMessage = ChatMessage(
        id = messageID,
        role = ChatMessageRole.ASSISTANT.raw,
        metadata = ChatMessageMetadata(
            status = ChatMessageStatus.STREAMING.raw,
            runID = runID,
        ),
    )
        private set

    private val activePartIndices = mutableMapOf<String, Int>()
    private val approvalToolCalls = mutableMapOf<String, String>()

    fun apply(chunk: ChatStreamChunk) {
        if (chunk.type.startsWith("data-")) {
            upsertDataPart(chunk)
            return
        }

        when (chunk.type) {
            "start" -> {
                chunk.messageID?.let { message.id = it }
                mergeMetadata(chunk.messageMetadata)
            }
            "start-step" -> message.parts.add(ChatMessagePart(type = "step-start"))
            "text-start", "reasoning-start" -> {
                val kind = if (chunk.type == "text-start") "text" else "reasoning"
                val id = chunk.id ?: return
                activePartIndices["$kind:$id"] = message.parts.size
                message.parts.add(ChatMessagePart(type = kind, text = "", state = "streaming"))
            }
            "text-delta", "reasoning-delta" -> {
                val kind = if (chunk.type == "text-delta") "text" else "reasoning"
                appendText(chunk.delta ?: "", key = "$kind:${chunk.id ?: ""}", kind = kind)
            }
            "text-end", "reasoning-end" -> {
                val kind = if (chunk.type == "text-end") "text" else "reasoning"
                finishPart(key = "$kind:${chunk.id ?: ""}")
            }
            "tool-input-start" -> {
                val callID = chunk.toolCallID ?: return
                activePartIndices["tool:$callID"] = message.parts.size
                message.parts.add(
                    ChatMessagePart(
                        type = "dynamic-tool",
                        state = "input-streaming",
                        toolCallID = callID,
                        toolName = chunk.toolName,
                    ),
                )
            }
            "tool-input-available", "tool-input-error" ->
                mutateToolPart(chunk.toolCallID, chunk.toolName) { part ->
                    part.input = chunk.input
                    part.state =
                        if (chunk.type == "tool-input-error") "output-error" else "input-available"
                    part.errorText = chunk.errorText
                }
            "tool-approval-request" -> {
                val approvalID = chunk.approvalID ?: return
                val callID = chunk.toolCallID ?: return
                approvalToolCalls[approvalID] = callID
                mutateToolPart(callID, chunk.toolName) { part ->
                    part.state = "approval-requested"
                    part.approval = ChatToolApprovalState(id = approvalID, approved = null)
                }
            }
            "tool-approval-response" -> {
                val approvalID = chunk.approvalID ?: return
                val callID = approvalToolCalls[approvalID] ?: return
                mutateToolPart(callID, null) { part ->
                    part.state = "approval-responded"
                    part.approval = ChatToolApprovalState(id = approvalID, approved = chunk.approved)
                }
                approvalToolCalls.remove(approvalID)
            }
            "tool-output-available", "tool-output-error", "tool-output-denied" ->
                mutateToolPart(chunk.toolCallID, chunk.toolName) { part ->
                    if (chunk.type == "tool-output-available") {
                        part.state = "output-available"
                        part.output = chunk.output
                    } else {
                        part.state = "output-error"
                        part.errorText = chunk.errorText ?: "The tool action was denied."
                    }
                }
            "source-url", "source-document", "file" -> message.parts.add(
                ChatMessagePart(
                    type = chunk.type,
                    sourceID = chunk.sourceID,
                    url = chunk.url,
                    title = chunk.title,
                    mediaType = chunk.mediaType,
                    filename = chunk.filename,
                ),
            )
            "message-metadata" -> mergeMetadata(chunk.messageMetadata)
            "finish" -> {
                mergeMetadata(chunk.messageMetadata)
                message.metadata?.status = ChatMessageStatus.COMPLETE.raw
                chunk.finishReason?.let { message.metadata?.finishReason = it }
            }
            "error" -> {
                message.metadata?.status = ChatMessageStatus.ERROR.raw
                if (message.metadata?.error == null) {
                    message.metadata?.error = ChatSafeError(
                        code = "stream_error",
                        message = chunk.errorText ?: "The chat run failed.",
                    )
                }
            }
            "abort" -> message.metadata?.status = ChatMessageStatus.CANCELLED.raw
        }
    }

    private fun upsertDataPart(chunk: ChatStreamChunk) {
        val key = "${chunk.type}:${chunk.id ?: ""}"
        val existing = activePartIndices[key]
        if (existing != null && existing in message.parts.indices) {
            message.parts[existing].data = chunk.data
            return
        }
        activePartIndices[key] = message.parts.size
        message.parts.add(ChatMessagePart(type = chunk.type, data = chunk.data, id = chunk.id))
    }

    private fun appendText(delta: String, key: String, kind: String) {
        val existing = activePartIndices[key]
        if (existing != null && existing in message.parts.indices) {
            val part = message.parts[existing]
            part.text = (part.text ?: "") + delta
            return
        }
        activePartIndices[key] = message.parts.size
        message.parts.add(ChatMessagePart(type = kind, text = delta, state = "streaming"))
    }

    private fun finishPart(key: String) {
        val index = activePartIndices.remove(key) ?: return
        if (index in message.parts.indices) {
            message.parts[index].state = "done"
        }
    }

    private fun mutateToolPart(
        callID: String?,
        toolName: String?,
        mutation: (ChatMessagePart) -> Unit,
    ) {
        if (callID == null) return
        val key = "tool:$callID"
        val existing = activePartIndices[key]
        val index = if (existing != null && existing in message.parts.indices) {
            existing
        } else {
            val newIndex = message.parts.size
            activePartIndices[key] = newIndex
            message.parts.add(
                ChatMessagePart(
                    type = "dynamic-tool",
                    state = "input-available",
                    toolCallID = callID,
                    toolName = toolName,
                ),
            )
            newIndex
        }
        if (message.parts[index].toolName.isNullOrEmpty()) {
            message.parts[index].toolName = toolName
        }
        mutation(message.parts[index])
    }

    private fun mergeMetadata(update: ChatMessageMetadata?) {
        if (message.metadata == null) message.metadata = ChatMessageMetadata()
        message.metadata?.merge(update)
    }
}

// ---- Conversations (mirrors iOS ChatConversation.swift) ----

enum class ChatConversationStatus(val raw: String) {
    ACTIVE("active"),
    ARCHIVED("archived");

    companion object {
        fun fromRaw(raw: String?): ChatConversationStatus =
            entries.firstOrNull { it.raw == raw } ?: ACTIVE
    }
}

/** A record of the PocketBase chat_conversations collection. */
@Serializable
data class ChatConversation(
    val id: String,
    val owner: String = "",
    val title: String = "New conversation",
    val status: String = "active",
    val pinned: Boolean = false,
    @SerialName("model_config") val modelConfigID: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("tool_call_count") val toolCallCount: Int = 0,
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("context_tokens") val contextTokens: Int = 0,
    @SerialName("last_message_at") val lastMessageAt: String = "",
    val created: String = "",
    val updated: String = "",
) {
    val statusEnum: ChatConversationStatus get() = ChatConversationStatus.fromRaw(status)
    val lastMessageAtDate: Instant? get() = parseServerDate(lastMessageAt)
    val createdAt: Instant? get() = parseServerDate(created)
    val updatedAt: Instant? get() = parseServerDate(updated)
    val activityDate: Instant? get() = lastMessageAtDate ?: updatedAt

    val messageSummary: String
        get() {
            val parts = mutableListOf("$messageCount ${if (messageCount == 1) "message" else "messages"}")
            if (toolCallCount > 0) {
                parts.add("$toolCallCount ${if (toolCallCount == 1) "tool" else "tools"}")
            }
            return parts.joinToString(" · ")
        }
}

@Serializable
data class ChatConversationsPage(
    val items: List<ChatConversation>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Int,
)

/** Update payload for rename / pin / archive; null fields are left untouched. */
@Serializable
data class ChatConversationUpdate(
    val title: String? = null,
    val status: String? = null,
    val pinned: Boolean? = null,
)
