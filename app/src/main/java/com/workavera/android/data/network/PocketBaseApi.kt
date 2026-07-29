package com.workavera.android.data.network

import com.workavera.android.data.model.ApiKey
import com.workavera.android.data.model.AppAppearance
import com.workavera.android.data.model.AuthResponse
import com.workavera.android.data.model.AvatarUpload
import com.workavera.android.data.model.CalendarEvent
import com.workavera.android.data.model.CalendarEventInput
import com.workavera.android.data.model.CalendarSnapshot
import com.workavera.android.data.model.CalendarTaskDeadline
import com.workavera.android.data.model.ChatConversation
import com.workavera.android.data.model.ChatConversationUpdate
import com.workavera.android.data.model.ChatConversationsPage
import com.workavera.android.data.model.ChatMessage
import com.workavera.android.data.model.ChatStreamChunk
import com.workavera.android.data.model.ChatStreamRequest
import com.workavera.android.data.model.Contact
import com.workavera.android.data.model.ContactFavoriteRecord
import com.workavera.android.data.model.ContactsSnapshot
import com.workavera.android.data.model.CreatedApiKey
import com.workavera.android.data.model.AppNotification
import com.workavera.android.data.model.BoardLabel
import com.workavera.android.data.model.BoardMember
import com.workavera.android.data.model.BoardPage
import com.workavera.android.data.model.BoardProject
import com.workavera.android.data.model.BoardState
import com.workavera.android.data.model.BoardStateCategory
import com.workavera.android.data.model.BoardTask
import com.workavera.android.data.model.DashboardSummary
import com.workavera.android.data.model.DocsPage
import com.workavera.android.data.model.DocsViewFilter
import com.workavera.android.data.model.DocumentFolder
import com.workavera.android.data.model.DocumentProject
import com.workavera.android.data.model.DocumentSummary
import com.workavera.android.data.model.DocumentVersion
import com.workavera.android.data.model.LlmModel
import com.workavera.android.data.model.LlmModelInput
import com.workavera.android.data.model.LlmShareTarget
import com.workavera.android.data.model.McpRefreshResult
import com.workavera.android.data.model.McpServer
import com.workavera.android.data.model.McpServerResponse
import com.workavera.android.data.model.McpToolDecision
import com.workavera.android.data.model.MemoryCategory
import com.workavera.android.data.model.NotificationPage
import com.workavera.android.data.model.NotificationRealtimeEvent
import com.workavera.android.data.model.ProfileUpdate
import com.workavera.android.data.model.ReadingItem
import com.workavera.android.data.model.ReadingItemInput
import com.workavera.android.data.model.ReadingListFilter
import com.workavera.android.data.model.ReadingPage
import com.workavera.android.data.model.ReadingStatus
import com.workavera.android.data.model.ReadingSummaryResponse
import com.workavera.android.data.model.RecordListResponse
import com.workavera.android.data.model.SavedChatMemory
import com.workavera.android.data.model.TaskPriority
import com.workavera.android.data.model.User
import com.workavera.android.data.model.UserPreferences
import com.workavera.android.data.model.UserStatus
import com.workavera.android.data.model.WorkspaceDocument
import com.workavera.android.data.model.parseServerDate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Workavera (PocketBase) API client. The server address is configured by the
 * user at runtime, so every method takes an explicit server parameter instead
 * of a fixed baseUrl.
 */
class PocketBaseApi(
    private val http: HttpClient,
    private val json: Json,
) {

    @Serializable
    private data class AuthWithPasswordPayload(val identity: String, val password: String)

    @Serializable
    private data class UpdateStatusPayload(val status: String)

    @Serializable
    private data class ChangePasswordPayload(
        val oldPassword: String,
        val password: String,
        val passwordConfirm: String,
    )

    @Serializable
    private data class CreateApiKeyPayload(
        val name: String,
        val allowDestructive: Boolean,
        val expires: String,
    )

    @Serializable
    private data class MemoryContentPayload(val category: String, val content: String)

    @Serializable
    private data class MemoryActivePayload(val active: Boolean)

    @Serializable
    private data class ShareModelPayload(val userIds: List<String>)

    @Serializable
    private data class ShareModelResponse(val shared: Int = 0)

    @Serializable
    private data class SystemConfig(val timezone: String = "")

    @Serializable
    private data class CalendarEventPayload(
        val owner: String? = null,
        val title: String,
        val description: String,
        @SerialName("start_at") val startAt: String,
        @SerialName("end_at") val endAt: String,
        @SerialName("all_day") val allDay: Boolean,
        val timezone: String,
        val location: String,
        val color: String,
        @SerialName("recurrence_frequency") val recurrenceFrequency: String,
        @SerialName("recurrence_interval") val recurrenceInterval: Int,
        @SerialName("reminder_minutes_before") val reminderMinutesBefore: Int,
    ) {
        constructor(owner: String?, input: CalendarEventInput) : this(
            owner = owner,
            title = input.title,
            description = input.description,
            startAt = formatServerInstant(input.startAt),
            endAt = formatServerInstant(input.endAt),
            allDay = input.allDay,
            timezone = input.timezone,
            location = input.location,
            color = input.color.raw,
            recurrenceFrequency = input.recurrenceFrequency.raw,
            recurrenceInterval = input.recurrenceInterval,
            reminderMinutesBefore = input.reminder.minutes,
        )
    }

    @Serializable
    private data class UpdatePreferencesPayload(
        val theme: String? = null,
        @SerialName("memory_enabled") val memoryEnabled: Boolean? = null,
        @SerialName("memory_auto_capture") val memoryAutoCapture: Boolean? = null,
    )

    /** PocketBase list response envelope; count queries only read totalItems. */
    @Serializable
    private data class ListCountResponse(val totalItems: Int = 0)

    suspend fun authWithPassword(server: String, identity: String, password: String): AuthResponse =
        request(server, "api/collections/users/auth-with-password", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(AuthWithPasswordPayload(identity = identity, password = password))
        }

    suspend fun authRefresh(server: String, token: String): AuthResponse =
        request(server, "api/collections/users/auth-refresh", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
        }

    suspend fun updateStatus(server: String, token: String, userId: String, status: UserStatus): User =
        request(server, "api/collections/users/records/$userId", HttpMethod.Patch) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(UpdateStatusPayload(status = status.raw))
        }

    /** Updates profile fields (optionally with an avatar) via multipart PATCH, matching the iOS app. */
    suspend fun updateProfile(
        server: String,
        token: String,
        userId: String,
        profile: ProfileUpdate,
        avatar: AvatarUpload?,
    ): User = request(server, "api/collections/users/records/$userId", HttpMethod.Patch) {
        header(HttpHeaders.Authorization, token)
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("name", profile.name)
                    append("phone", profile.phone)
                    append("title", profile.title)
                    append("bio", profile.bio)
                    append("status", profile.status.raw)
                    if (avatar != null) {
                        append(
                            "avatar",
                            avatar.data,
                            Headers.build {
                                // Only the filename parameter: Ktor writes its
                                // own `form-data; name="avatar"` disposition
                                // and merges this in — a full disposition here
                                // would duplicate and PocketBase would treat
                                // the part as a plain form field, not a file.
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"${avatar.fileName}\"",
                                )
                                append(HttpHeaders.ContentType, avatar.mimeType)
                            },
                        )
                    }
                },
            ),
        )
    }

    /** Changes the password. PocketBase requires the old password plus the new password twice. */
    suspend fun changePassword(
        server: String,
        token: String,
        userId: String,
        oldPassword: String,
        newPassword: String,
    ): User = request(server, "api/collections/users/records/$userId", HttpMethod.Patch) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(
            ChangePasswordPayload(
                oldPassword = oldPassword,
                password = newPassword,
                passwordConfirm = newPassword,
            ),
        )
    }

    /** Loads the single user_preferences record for the owner (theme + memory flags). */
    suspend fun getPreferences(server: String, token: String, userId: String): UserPreferences {
        val response = request<RecordListResponse<UserPreferences>>(
            server,
            "api/collections/user_preferences/records",
            HttpMethod.Get,
        ) {
            header(HttpHeaders.Authorization, token)
            parameter("filter", "owner = \"$userId\"")
            parameter("page", "1")
            parameter("perPage", "1")
        }
        return response.items.firstOrNull()
            ?: throw ApiException(404, "User preferences were not found.")
    }

    /** Partially updates preferences; only non-null fields are sent. */
    suspend fun updatePreferences(
        server: String,
        token: String,
        id: String,
        theme: AppAppearance? = null,
        memoryEnabled: Boolean? = null,
        memoryAutoCapture: Boolean? = null,
    ): UserPreferences =
        request(server, "api/collections/user_preferences/records/$id", HttpMethod.Patch) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(
                UpdatePreferencesPayload(
                    theme = theme?.raw,
                    memoryEnabled = memoryEnabled,
                    memoryAutoCapture = memoryAutoCapture,
                ),
            )
        }

    // ---- API keys (MCP access) ----

    /** Lists the signed-in user's API keys, newest first. */
    suspend fun getApiKeys(server: String, token: String): List<ApiKey> =
        request<RecordListResponse<ApiKey>>(
            server,
            "api/collections/api_keys/records",
            HttpMethod.Get,
        ) {
            header(HttpHeaders.Authorization, token)
            parameter("sort", "-created")
            parameter("perPage", "200")
        }.items

    /**
     * Creates a key via the custom endpoint (the server generates and hashes the
     * secret) and returns the one-time plaintext key. [expires] is an ISO-8601
     * timestamp, or empty for a key that never expires.
     */
    suspend fun createApiKey(
        server: String,
        token: String,
        name: String,
        expires: String,
        allowDestructive: Boolean,
    ): CreatedApiKey = request(server, "api/apikeys", HttpMethod.Post) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(CreateApiKeyPayload(name = name, allowDestructive = allowDestructive, expires = expires))
    }

    /** Revokes (hard-deletes) a key; connected MCP clients lose access immediately. */
    suspend fun revokeApiKey(server: String, token: String, id: String) =
        requestEmpty(server, "api/collections/api_keys/records/$id", HttpMethod.Delete) {
            header(HttpHeaders.Authorization, token)
        }

    // ---- MCP servers (third-party servers whose tools Chat may call) ----

    @Serializable
    private data class CreateMcpServerPayload(
        val name: String,
        val slug: String,
        val transport: String,
        val url: String,
        val headers: Map<String, String>,
        val approvalPolicy: String,
    )

    /** Only non-null fields are sent; a null headers map leaves the stored credentials untouched. */
    @Serializable
    private data class UpdateMcpServerPayload(
        val name: String? = null,
        val url: String? = null,
        val transport: String? = null,
        val headers: Map<String, String>? = null,
        val approvalPolicy: String? = null,
        val enabled: Boolean? = null,
    )

    @Serializable
    private data class UpdateMcpToolsPayload(val tools: List<McpToolDecision>)

    /** Lists the signed-in user's MCP servers, oldest first (matching the Web settings card). */
    suspend fun getMcpServers(server: String, token: String): List<McpServer> =
        request<RecordListResponse<McpServer>>(
            server,
            "api/collections/mcp_servers/records",
            HttpMethod.Get,
        ) {
            header(HttpHeaders.Authorization, token)
            parameter("sort", "created")
            parameter("perPage", "200")
        }.items

    /**
     * Registers a server via the feature route, which validates the endpoint and
     * keeps credentials write-only. The record starts with no tools; a refresh
     * is what loads them.
     */
    suspend fun createMcpServer(
        server: String,
        token: String,
        name: String,
        slug: String,
        transport: String,
        url: String,
        headers: Map<String, String>,
        approvalPolicy: String,
    ): McpServer = request<McpServerResponse>(server, "api/mcp-servers", HttpMethod.Post) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(
            CreateMcpServerPayload(
                name = name,
                slug = slug,
                transport = transport,
                url = url,
                headers = headers,
                approvalPolicy = approvalPolicy,
            ),
        )
    }.toModel()

    /** Partially updates a server; pass null for every field that stays unchanged. */
    suspend fun updateMcpServer(
        server: String,
        token: String,
        id: String,
        name: String? = null,
        url: String? = null,
        transport: String? = null,
        headers: Map<String, String>? = null,
        approvalPolicy: String? = null,
        enabled: Boolean? = null,
    ): McpServer = request<McpServerResponse>(server, "api/mcp-servers/$id", HttpMethod.Patch) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(
            UpdateMcpServerPayload(
                name = name,
                url = url,
                transport = transport,
                headers = headers,
                approvalPolicy = approvalPolicy,
                enabled = enabled,
            ),
        )
    }.toModel()

    /**
     * Contacts the upstream server, locks the fetched tool definitions, and
     * reports the diff. A failed refresh comes back as [McpRefreshResult.ok]
     * false (HTTP 200), with the failure also stored as the server's last_error.
     */
    suspend fun refreshMcpServer(server: String, token: String, id: String): McpRefreshResult =
        request(server, "api/mcp-servers/$id/refresh", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
        }

    /**
     * Applies the user's review decisions (enabled + approval per tool) — the
     * only way a tool becomes available to the assistant.
     */
    suspend fun updateMcpTools(
        server: String,
        token: String,
        id: String,
        decisions: List<McpToolDecision>,
    ): McpServer = request<McpServerResponse>(server, "api/mcp-servers/$id/tools", HttpMethod.Patch) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(UpdateMcpToolsPayload(tools = decisions))
    }.toModel()

    /** Removes the server and its stored credentials; Chat stops offering its tools immediately. */
    suspend fun deleteMcpServer(server: String, token: String, id: String) =
        requestEmpty(server, "api/collections/mcp_servers/records/$id", HttpMethod.Delete) {
            header(HttpHeaders.Authorization, token)
        }

    // ---- Saved chat memories ----

    /** Lists the signed-in user's saved memories, most recently updated first. */
    suspend fun getSavedMemories(server: String, token: String): List<SavedChatMemory> =
        request<RecordListResponse<SavedChatMemory>>(
            server,
            "api/collections/chat_memories/records",
            HttpMethod.Get,
        ) {
            header(HttpHeaders.Authorization, token)
            parameter("sort", "-updated")
            parameter("perPage", "200")
        }.items

    suspend fun createSavedMemory(
        server: String,
        token: String,
        category: MemoryCategory,
        content: String,
    ): SavedChatMemory =
        request(server, "api/collections/chat_memories/records", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(MemoryContentPayload(category = category.raw, content = content))
        }

    suspend fun updateSavedMemory(
        server: String,
        token: String,
        id: String,
        category: MemoryCategory,
        content: String,
    ): SavedChatMemory =
        request(server, "api/collections/chat_memories/records/$id", HttpMethod.Patch) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(MemoryContentPayload(category = category.raw, content = content))
        }

    suspend fun setSavedMemoryActive(
        server: String,
        token: String,
        id: String,
        active: Boolean,
    ): SavedChatMemory =
        request(server, "api/collections/chat_memories/records/$id", HttpMethod.Patch) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(MemoryActivePayload(active = active))
        }

    suspend fun deleteSavedMemory(server: String, token: String, id: String) =
        requestEmpty(server, "api/collections/chat_memories/records/$id", HttpMethod.Delete) {
            header(HttpHeaders.Authorization, token)
        }

    // ---- LLM model configurations (custom /api/llm endpoints) ----

    /** Lists the user's model configurations (secrets replaced by a hasApiKey flag). */
    suspend fun getLlmModels(server: String, token: String): List<LlmModel> =
        request(server, "api/llm/models", HttpMethod.Get) {
            header(HttpHeaders.Authorization, token)
        }

    suspend fun createLlmModel(server: String, token: String, input: LlmModelInput): LlmModel =
        request(server, "api/llm/models", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(input)
        }

    suspend fun updateLlmModel(server: String, token: String, id: String, input: LlmModelInput): LlmModel =
        request(server, "api/llm/models/$id", HttpMethod.Patch) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(input)
        }

    suspend fun deleteLlmModel(server: String, token: String, id: String) =
        requestEmpty(server, "api/llm/models/$id", HttpMethod.Delete) {
            header(HttpHeaders.Authorization, token)
        }

    suspend fun setDefaultLlmModel(server: String, token: String, id: String): LlmModel =
        request(server, "api/llm/models/$id/default", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
        }

    suspend fun getLlmShareTargets(server: String, token: String): List<LlmShareTarget> =
        request(server, "api/llm/share-targets", HttpMethod.Get) {
            header(HttpHeaders.Authorization, token)
        }

    /** Shares a model with the given users; returns how many invitations were sent. */
    suspend fun shareLlmModel(server: String, token: String, id: String, userIds: List<String>): Int =
        request<ShareModelResponse>(server, "api/llm/models/$id/share", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(ShareModelPayload(userIds = userIds))
        }.shared

    /** PocketBase file URL, e.g. a user avatar. Returns null when fileName is empty. */
    fun fileUrl(server: String, collectionId: String, recordId: String, fileName: String): String? {
        if (fileName.isEmpty()) return null
        val base = normalizeServerAddress(server) ?: return null
        return "$base/api/files/$collectionId/$recordId/$fileName"
    }

    /**
     * Dashboard stats, matching the iOS app: 5 concurrent count queries
     * (perPage=1, reading only totalItems). nextSevenDays = calendar events in
     * the next 7 days + open tasks due within the same window.
     */
    suspend fun getDashboardSummary(
        server: String,
        token: String,
        now: Instant = Instant.now(),
    ): DashboardSummary = coroutineScope {
        val openTaskFilter = "state.category != \"completed\" && archived = false"
        val nowValue = dashboardDate(now)
        val nextWeekValue = dashboardDate(now.plus(7, ChronoUnit.DAYS))

        val projects = async { countRecords(server, token, "board_projects", "archived = false") }
        val tasks = async { countRecords(server, token, "board_tasks", openTaskFilter) }
        val events = async {
            countRecords(
                server, token, "calendar_events",
                "start_at >= \"$nowValue\" && start_at < \"$nextWeekValue\"",
            )
        }
        val upcomingTasks = async {
            countRecords(
                server, token, "board_tasks",
                "$openTaskFilter && due_date >= \"$nowValue\" && due_date < \"$nextWeekValue\"",
            )
        }
        val reading = async { countRecords(server, token, "reading_items", "status = \"unread\"") }

        DashboardSummary(
            activeProjects = projects.await(),
            openTasks = tasks.await(),
            nextSevenDays = events.await() + upcomingTasks.await(),
            unreadReading = reading.await(),
        )
    }

    private suspend fun countRecords(
        server: String,
        token: String,
        collection: String,
        filter: String,
    ): Int = request<ListCountResponse>(
        server,
        "api/collections/$collection/records",
        HttpMethod.Get,
    ) {
        header(HttpHeaders.Authorization, token)
        parameter("page", "1")
        parameter("perPage", "1")
        parameter("fields", "id")
        parameter("filter", filter)
    }.totalItems

    // ---- Notifications (mirrors the notification API of the iOS PocketBaseClient) ----

    @Serializable
    private data class RespondSharePayload(val decision: String)

    @Serializable
    private data class RespondShareResponse(val status: String = "pending")

    @Serializable
    private data class ArchiveNotificationPayload(val status: String, val pinned: Boolean)

    /** Notification list: sorted by -pinned,-created, active only. page is 1-based. */
    suspend fun getNotifications(
        server: String,
        token: String,
        page: Int,
        pageSize: Int,
    ): NotificationPage {
        val response = request<RecordListResponse<AppNotification>>(
            server,
            "api/collections/notifications/records",
            HttpMethod.Get,
        ) {
            header(HttpHeaders.Authorization, token)
            parameter("page", page.toString())
            parameter("perPage", pageSize.toString())
            parameter("sort", "-pinned,-created")
            parameter("filter", "status = \"active\"")
        }
        return NotificationPage(
            items = response.items,
            page = response.page,
            totalPages = response.totalPages,
            totalItems = response.totalItems ?: response.items.size,
        )
    }

    /** Unread count also goes through the list endpoint: perPage=1, reading only totalItems. */
    suspend fun getUnreadNotificationCount(server: String, token: String): Int =
        request<ListCountResponse>(
            server,
            "api/collections/notifications/records",
            HttpMethod.Get,
        ) {
            header(HttpHeaders.Authorization, token)
            parameter("page", "1")
            parameter("perPage", "1")
            parameter("fields", "id")
            parameter("filter", "status = \"active\" && read_at = \"\"")
        }.totalItems

    /** Marks one notification as read (the server writes read_at; no request body). */
    suspend fun markNotificationRead(server: String, token: String, id: String) =
        requestEmpty(server, "api/notifications/$id/read", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
        }

    /** Marks all notifications as read. */
    suspend fun markAllNotificationsRead(server: String, token: String) =
        requestEmpty(server, "api/notifications/read-all", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
        }

    /** Responds to a model share: decision is accept/reject; returns the latest status. */
    suspend fun respondToModelShare(
        server: String,
        token: String,
        notificationId: String,
        decision: String,
    ): String = request<RespondShareResponse>(
        server,
        "api/llm/shares/$notificationId/respond",
        HttpMethod.Post,
    ) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(RespondSharePayload(decision = decision))
    }.status

    /** Archives (and unpins) a notification, removing it from the list. */
    suspend fun archiveNotification(server: String, token: String, id: String) =
        request<AppNotification>(
            server,
            "api/collections/notifications/records/$id",
            HttpMethod.Patch,
        ) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(ArchiveNotificationPayload(status = "archived", pinned = false))
        }

    /** Permanently deletes a notification. */
    suspend fun deleteNotification(server: String, token: String, id: String) =
        requestEmpty(server, "api/collections/notifications/records/$id", HttpMethod.Delete) {
            header(HttpHeaders.Authorization, token)
        }

    // ---- Board (mirrors the board API of the iOS PocketBaseClient) ----

    @Serializable
    private data class BoardUserRecord(
        val id: String,
        val collectionId: String = "users",
        val name: String = "",
        val email: String = "",
        val avatar: String = "",
    ) {
        val displayName: String get() = name.ifEmpty { email }
    }

    @Serializable
    private data class BoardProjectRecord(
        val id: String,
        val name: String = "",
        val description: String = "",
        val owner: String = "",
        val archived: Boolean = false,
        val expand: Expansion? = null,
    ) {
        @Serializable
        data class Expansion(val owner: BoardUserRecord? = null)
    }

    @Serializable
    private data class BoardProjectPreferenceRecord(
        val id: String,
        val project: String = "",
        @SerialName("sort_order") val sortOrder: Double = 0.0,
        val expand: Expansion? = null,
    ) {
        @Serializable
        data class Expansion(val project: BoardProjectRecord? = null)
    }

    @Serializable
    private data class BoardStateRecord(
        val id: String,
        val project: String = "",
        val name: String = "",
        val color: String = "",
        val category: String = "",
        @SerialName("sort_order") val sortOrder: Double = 0.0,
    )

    @Serializable
    private data class BoardLabelRecord(
        val id: String,
        val project: String = "",
        val name: String = "",
        val color: String = "",
    )

    @Serializable
    private data class BoardMemberRecord(
        val id: String,
        val project: String = "",
        val user: String = "",
        val expand: Expansion? = null,
    ) {
        @Serializable
        data class Expansion(val user: BoardUserRecord? = null)
    }

    @Serializable
    private data class BoardTaskRecord(
        val id: String,
        val project: String = "",
        val state: String = "",
        val title: String = "",
        val description: String = "",
        val priority: String = "",
        val labels: List<String> = emptyList(),
        val assignees: List<String> = emptyList(),
        val documents: List<String> = emptyList(),
        @SerialName("due_date") val dueDate: String = "",
        val rank: Double = 0.0,
    )

    @Serializable
    private data class MoveTaskPayload(val state: String, val rank: Double)

    @Serializable
    private data class TaskPriorityPayload(val priority: String)

    @Serializable
    private data class TaskDueDatePayload(@SerialName("due_date") val dueDate: String)

    /**
     * Board page load: 1 paged request (project preferences, expanding the
     * project and its owner) + 4 concurrent full-list fetches (states / tasks /
     * labels / members, auto-paginated with perPage=200), assembled client-side.
     */
    suspend fun getBoardPage(
        server: String,
        token: String,
        userId: String,
        page: Int,
        pageSize: Int,
    ): BoardPage = coroutineScope {
        val preferences = request<RecordListResponse<BoardProjectPreferenceRecord>>(
            server,
            "api/collections/board_project_preferences/records",
            HttpMethod.Get,
        ) {
            header(HttpHeaders.Authorization, token)
            parameter("page", page.toString())
            parameter("perPage", pageSize.toString())
            parameter("sort", "sort_order,id")
            parameter("expand", "project,project.owner")
            parameter("filter", "user = \"$userId\" && project.archived = false")
        }

        val projectRecords = preferences.items.mapNotNull { it.expand?.project }
        if (projectRecords.isEmpty()) {
            return@coroutineScope BoardPage(
                projects = emptyList(),
                page = preferences.page,
                totalPages = maxOf(1, preferences.totalPages),
                totalItems = preferences.totalItems ?: 0,
            )
        }

        val projectFilter = projectRecords.joinToString(" || ") { "project = \"${it.id}\"" }
        val activeTaskFilter = "($projectFilter) && archived = false"
        val states = async {
            listAll<BoardStateRecord>(server, token, "board_project_states", projectFilter, "sort_order")
        }
        val tasks = async {
            listAll<BoardTaskRecord>(server, token, "board_tasks", activeTaskFilter, "rank")
        }
        val labels = async {
            listAll<BoardLabelRecord>(server, token, "board_project_labels", projectFilter, "name")
        }
        val members = async {
            listAll<BoardMemberRecord>(
                server, token, "board_project_members", projectFilter, "created", expand = "user",
            )
        }

        BoardPage(
            projects = assembleBoardProjects(
                server,
                projectRecords,
                states.await(),
                tasks.await(),
                labels.await(),
                members.await(),
            ),
            page = preferences.page,
            totalPages = maxOf(1, preferences.totalPages),
            totalItems = preferences.totalItems ?: projectRecords.size,
        )
    }

    /** Fetches an entire list by auto-paginating (perPage=200). */
    private suspend inline fun <reified T> listAll(
        server: String,
        token: String,
        collection: String,
        filter: String,
        sort: String,
        expand: String? = null,
    ): List<T> {
        val all = mutableListOf<T>()
        var page = 1
        var totalPages = 1
        while (page <= totalPages) {
            val response = request<RecordListResponse<T>>(
                server,
                "api/collections/$collection/records",
                HttpMethod.Get,
            ) {
                header(HttpHeaders.Authorization, token)
                parameter("page", page.toString())
                parameter("perPage", "200")
                parameter("sort", sort)
                parameter("filter", filter)
                if (expand != null) parameter("expand", expand)
            }
            all += response.items
            totalPages = response.totalPages
            page++
        }
        return all
    }

    /**
     * Assembles the 5 record types into domain models: members deduplicated
     * with the owner first, tasks sorted by rank, states by sortOrder.
     */
    private fun assembleBoardProjects(
        server: String,
        projectRecords: List<BoardProjectRecord>,
        stateRecords: List<BoardStateRecord>,
        taskRecords: List<BoardTaskRecord>,
        labelRecords: List<BoardLabelRecord>,
        memberRecords: List<BoardMemberRecord>,
    ): List<BoardProject> {
        val labelsById = labelRecords.associate {
            it.id to BoardLabel(it.id, it.project, it.name, it.color)
        }

        fun toMember(user: BoardUserRecord?, fallbackName: String): BoardMember? = user?.let {
            BoardMember(
                id = it.id,
                name = it.displayName.ifEmpty { fallbackName },
                avatarUrl = fileUrl(server, it.collectionId, it.id, it.avatar),
            )
        }

        return projectRecords.map { project ->
            val projectMembers = buildList {
                toMember(project.expand?.owner, "Project owner")?.let(::add)
                memberRecords.filter { it.project == project.id }.forEach { record ->
                    val member = toMember(record.expand?.user, "Unknown member")
                    if (member != null && none { it.id == member.id }) add(member)
                }
            }
            val membersById = projectMembers.associateBy { it.id }
            val projectLabels = labelRecords
                .filter { it.project == project.id }
                .sortedBy { it.name }
                .map { BoardLabel(it.id, it.project, it.name, it.color) }
            val projectStates = stateRecords
                .filter { it.project == project.id }
                .sortedBy { it.sortOrder }
                .map { state ->
                    val stateTasks = taskRecords
                        .filter { it.state == state.id }
                        .sortedBy { it.rank }
                        .map { task ->
                            BoardTask(
                                id = task.id,
                                projectId = task.project,
                                stateId = task.state,
                                title = task.title,
                                description = task.description,
                                priority = TaskPriority.fromRaw(task.priority),
                                dueDate = parseServerDate(task.dueDate),
                                labels = task.labels.mapNotNull { labelsById[it] },
                                assignees = task.assignees.map {
                                    membersById[it] ?: BoardMember(it, "Unknown member", null)
                                },
                                documentCount = task.documents.size,
                                rank = task.rank,
                            )
                        }
                    BoardState(
                        id = state.id,
                        projectId = state.project,
                        name = state.name,
                        color = state.color,
                        category = BoardStateCategory.fromRaw(state.category),
                        sortOrder = state.sortOrder,
                        tasks = stateTasks,
                    )
                }
            BoardProject(
                id = project.id,
                ownerId = project.owner,
                name = project.name,
                description = project.description,
                members = projectMembers,
                labels = projectLabels,
                states = projectStates,
            )
        }
    }

    /** Moves a task to a target state (caller computes rank as max+1024; append-to-end only). */
    suspend fun moveBoardTask(
        server: String,
        token: String,
        id: String,
        stateId: String,
        rank: Double,
    ) = requestEmpty(server, "api/collections/board_tasks/records/$id", HttpMethod.Patch) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(MoveTaskPayload(state = stateId, rank = rank))
    }

    suspend fun updateBoardTaskPriority(
        server: String,
        token: String,
        id: String,
        priority: TaskPriority,
    ) = requestEmpty(server, "api/collections/board_tasks/records/$id", HttpMethod.Patch) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(TaskPriorityPayload(priority = priority.raw))
    }

    /** Passing null for dueDate sends an empty string to clear it. */
    suspend fun updateBoardTaskDueDate(
        server: String,
        token: String,
        id: String,
        dueDate: Instant?,
    ) = requestEmpty(server, "api/collections/board_tasks/records/$id", HttpMethod.Patch) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(TaskDueDatePayload(dueDate = dueDate?.let(::formatServerInstant) ?: ""))
    }

    // ---- Calendar (events + task deadlines + timezone) ----

    /**
     * Calendar snapshot, matching the iOS getCalendarSnapshot: events and
     * board-task deadlines loaded concurrently, plus the workspace timezone
     * from /api/configs/system (applied to every event).
     */
    suspend fun getCalendarSnapshot(
        server: String,
        token: String,
        userId: String,
    ): CalendarSnapshot = coroutineScope {
        val eventsDeferred = async {
            listAll<CalendarEvent>(server, token, "calendar_events", "", "start_at")
        }
        val tasksDeferred = async {
            listAll<CalendarTaskDeadline>(
                server, token, "board_tasks",
                "due_date != \"\" && archived = false && assignees ?= \"$userId\"",
                "due_date", expand = "project,state",
            )
        }
        val configDeferred = async {
            runCatching {
                request<SystemConfig>(server, "api/configs/system", HttpMethod.Get) {
                    header(HttpHeaders.Authorization, token)
                }
            }.getOrNull()
        }
        val rawZone = configDeferred.await()?.timezone.orEmpty()
        val timezone = if (runCatching { ZoneId.of(rawZone) }.isSuccess) rawZone else ZoneId.systemDefault().id
        val events = eventsDeferred.await().map { it.copy(timezone = timezone) }
        CalendarSnapshot(events = events, tasks = tasksDeferred.await(), timezone = timezone)
    }

    suspend fun createCalendarEvent(
        server: String,
        token: String,
        ownerId: String,
        input: CalendarEventInput,
    ): CalendarEvent =
        request(server, "api/collections/calendar_events/records", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(CalendarEventPayload(owner = ownerId, input = input))
        }

    suspend fun updateCalendarEvent(
        server: String,
        token: String,
        id: String,
        input: CalendarEventInput,
    ): CalendarEvent =
        request(server, "api/collections/calendar_events/records/$id", HttpMethod.Patch) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(CalendarEventPayload(owner = null, input = input))
        }

    suspend fun deleteCalendarEvent(server: String, token: String, id: String) =
        requestEmpty(server, "api/collections/calendar_events/records/$id", HttpMethod.Delete) {
            header(HttpHeaders.Authorization, token)
        }

    /** Calendar realtime signals (payloads ignored; any calendar/task event triggers a refresh). */
    fun calendarRealtimeSignals(server: String, token: String): Flow<Unit> =
        realtimeFrames(server, token, CALENDAR_REALTIME_TOPICS)
            .filter { frame -> CALENDAR_REALTIME_PREFIXES.any { frame.event.startsWith(it) } }
            .map { }

    // ---- Docs (workspace documents, mirrors the iOS docs API) ----

    @Serializable
    private data class DocumentRecord(
        val id: String,
        val title: String = "Untitled",
        val kind: String = "markdown",
        val content: String = "",
        val owner: String = "",
        val project: String = "",
        val folder: String = "",
        val status: String = "draft",
        val revision: Int = 1,
        @SerialName("last_edited_by") val lastEditedBy: String = "",
        val created: String = "",
        val updated: String = "",
    ) {
        fun toSummary(pinned: Boolean) = DocumentSummary(
            id = id, title = title, kind = kind, ownerId = owner,
            projectId = project.ifEmpty { null }, folderId = folder.ifEmpty { null },
            status = status, revision = revision, updated = updated, pinned = pinned,
        )

        fun toWorkspace(pinned: Boolean) = WorkspaceDocument(
            id = id, title = title, kind = kind, content = content, ownerId = owner,
            projectId = project.ifEmpty { null }, folderId = folder.ifEmpty { null },
            status = status, revision = revision, lastEditedBy = lastEditedBy,
            created = created, updated = updated, pinned = pinned,
        )
    }

    @Serializable
    private data class PinnedDocumentRecord(
        val id: String,
        val title: String = "Untitled",
        val kind: String = "markdown",
        val ownerId: String = "",
        val projectId: String? = null,
        val folderId: String? = null,
        val status: String = "draft",
        val revision: Int = 1,
        val updated: String = "",
    ) {
        fun toSummary() = DocumentSummary(
            id = id, title = title, kind = kind, ownerId = ownerId,
            projectId = projectId, folderId = folderId, status = status,
            revision = revision, updated = updated, pinned = true,
        )
    }

    @Serializable
    private data class DocumentFolderRecord(val id: String, val name: String = "")

    @Serializable
    private data class DocumentPinPayload(val pinned: Boolean)

    /**
     * Docs page load, matching the iOS getDocsPage: pinned ids + folders +
     * projects concurrently with the (paged) documents list; the Pinned view
     * is served entirely by /api/docs-pinned, Recent caps at 10 items.
     */
    suspend fun getDocsPage(
        server: String,
        token: String,
        userId: String,
        view: DocsViewFilter,
        page: Int,
        pageSize: Int,
        query: String,
    ): DocsPage = coroutineScope {
        val normalizedQuery = query.trim()
        val escapedQuery = filterLiteral(normalizedQuery)

        val pinnedDeferred = async {
            request<List<PinnedDocumentRecord>>(server, "api/docs-pinned", HttpMethod.Get) {
                header(HttpHeaders.Authorization, token)
                if (normalizedQuery.isNotEmpty()) parameter("query", normalizedQuery)
            }
        }
        val foldersDeferred = async {
            listAll<DocumentFolderRecord>(server, token, "doc_folders", "", "name")
        }
        val projectsDeferred = async {
            listAll<BoardProjectPreferenceRecord>(
                server, token, "board_project_preferences",
                "user = \"${filterLiteral(userId)}\" && project.archived = false",
                "sort_order,id", expand = "project",
            )
        }
        val listDeferred = async {
            if (view is DocsViewFilter.Pinned) {
                null
            } else {
                val filters = mutableListOf(
                    if (view is DocsViewFilter.Archived) "status = \"archived\"" else "status = \"draft\"",
                )
                if (escapedQuery.isNotEmpty()) {
                    filters += "(title ~ \"$escapedQuery\" || content ~ \"$escapedQuery\")"
                }
                when (view) {
                    DocsViewFilter.MyDocuments -> {
                        filters += "project = \"\""
                        filters += "folder = \"\""
                    }
                    is DocsViewFilter.Folder -> {
                        filters += "project = \"\""
                        filters += "folder = \"${filterLiteral(view.id)}\""
                    }
                    is DocsViewFilter.Project ->
                        filters += "project = \"${filterLiteral(view.id)}\""
                    else -> {}
                }
                val isRecent = view is DocsViewFilter.Recent
                request<RecordListResponse<DocumentRecord>>(
                    server, "api/collections/docs/records", HttpMethod.Get,
                ) {
                    header(HttpHeaders.Authorization, token)
                    parameter("page", (if (isRecent) 1 else page).toString())
                    parameter("perPage", (if (isRecent) 10 else pageSize).toString())
                    parameter("sort", "-updated")
                    parameter("filter", filters.joinToString(" && "))
                    parameter(
                        "fields",
                        "id,title,kind,owner,project,folder,status,revision,last_edited_by,created,updated",
                    )
                }
            }
        }

        val pinnedRecords = pinnedDeferred.await()
        val pinnedIds = pinnedRecords.mapTo(HashSet()) { it.id }
        val listResponse = listDeferred.await()

        val documents: List<DocumentSummary>
        val resolvedPage: Int
        val totalPages: Int
        val totalItems: Int
        when {
            view is DocsViewFilter.Pinned -> {
                documents = pinnedRecords.map { it.toSummary() }
                resolvedPage = 1
                totalPages = 1
                totalItems = documents.size
            }
            listResponse != null -> {
                val isRecent = view is DocsViewFilter.Recent
                documents = listResponse.items.map { it.toSummary(it.id in pinnedIds) }
                resolvedPage = if (isRecent) 1 else listResponse.page
                totalPages = if (isRecent) 1 else maxOf(1, listResponse.totalPages)
                totalItems = if (isRecent) documents.size else listResponse.totalItems ?: documents.size
            }
            else -> {
                documents = emptyList()
                resolvedPage = 1
                totalPages = 1
                totalItems = 0
            }
        }

        DocsPage(
            documents = documents,
            folders = foldersDeferred.await().map { DocumentFolder(it.id, it.name) },
            projects = projectsDeferred.await().mapNotNull { preference ->
                preference.expand?.project?.let { DocumentProject(it.id, it.name) }
            },
            page = resolvedPage,
            totalPages = totalPages,
            totalItems = totalItems,
        )
    }

    suspend fun getDocument(server: String, token: String, id: String, pinned: Boolean): WorkspaceDocument =
        request<DocumentRecord>(server, "api/collections/docs/records/$id", HttpMethod.Get) {
            header(HttpHeaders.Authorization, token)
        }.toWorkspace(pinned)

    suspend fun getDocumentVersions(server: String, token: String, id: String): List<DocumentVersion> =
        request(server, "api/docs/$id/versions", HttpMethod.Get) {
            header(HttpHeaders.Authorization, token)
        }

    suspend fun getDocumentVersion(
        server: String,
        token: String,
        id: String,
        revision: Int,
    ): DocumentVersion =
        request(server, "api/docs/$id/versions/$revision", HttpMethod.Get) {
            header(HttpHeaders.Authorization, token)
        }

    suspend fun setDocumentPinned(server: String, token: String, id: String, pinned: Boolean) =
        requestEmpty(server, "api/docs/$id/pin", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(DocumentPinPayload(pinned))
        }

    suspend fun setDocumentArchived(server: String, token: String, id: String, archived: Boolean) =
        requestEmpty(server, "api/docs/$id/${if (archived) "archive" else "unarchive"}", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
        }

    suspend fun deleteDocument(server: String, token: String, id: String) =
        requestEmpty(server, "api/docs/$id", HttpMethod.Delete) {
            header(HttpHeaders.Authorization, token)
        }

    // ---- Reading (reading list items, mirrors the iOS reading API) ----

    @Serializable
    private data class CreateReadingItemPayload(
        val owner: String,
        val url: String,
        val title: String,
        val description: String,
        val tags: List<String>,
        val status: String,
    )

    @Serializable
    private data class UpdateReadingStatusPayload(val status: String)

    @Serializable
    private data class ReadingPinPayload(val pinned: Boolean)

    @Serializable
    private data class MarkAllReadingReadResponse(val updated: Int = 0)

    /**
     * Reading page load, matching the iOS getReadingPage: the paged items list
     * and the global unread count are fetched concurrently; content_text is
     * deliberately excluded from the list fields.
     */
    suspend fun getReadingPage(
        server: String,
        token: String,
        filter: ReadingListFilter,
        page: Int,
        pageSize: Int,
        query: String,
    ): ReadingPage = coroutineScope {
        val filters = when (filter) {
            ReadingListFilter.ACTIVE -> mutableListOf("status != \"archived\"")
            ReadingListFilter.UNREAD -> mutableListOf("status = \"unread\"")
            ReadingListFilter.READ -> mutableListOf("status = \"read\"")
            ReadingListFilter.PINNED -> mutableListOf("status != \"archived\"", "pinned = true")
            ReadingListFilter.ARCHIVED -> mutableListOf("status = \"archived\"")
        }
        val normalizedQuery = query.trim()
        if (normalizedQuery.isNotEmpty()) {
            val escaped = filterLiteral(normalizedQuery)
            filters += "(title ~ \"$escaped\" || url ~ \"$escaped\" || description ~ \"$escaped\" || " +
                "summary ~ \"$escaped\" || tags ~ \"$escaped\" || key_points ~ \"$escaped\")"
        }

        val pageDeferred = async {
            request<RecordListResponse<ReadingItem>>(
                server, "api/collections/reading_items/records", HttpMethod.Get,
            ) {
                header(HttpHeaders.Authorization, token)
                parameter("page", page.toString())
                parameter("perPage", pageSize.toString())
                parameter("sort", "-pinned,-updated")
                parameter("filter", filters.joinToString(" && "))
                parameter(
                    "fields",
                    "id,owner,project,url,title,description,tags,status,pinned,summary,key_points,summary_language,created,updated",
                )
            }
        }
        val unreadDeferred = async {
            request<ListCountResponse>(
                server, "api/collections/reading_items/records", HttpMethod.Get,
            ) {
                header(HttpHeaders.Authorization, token)
                parameter("page", "1")
                parameter("perPage", "1")
                parameter("filter", "status = \"unread\"")
                parameter("fields", "id")
            }
        }

        val result = pageDeferred.await()
        val unread = unreadDeferred.await()
        ReadingPage(
            items = result.items,
            page = result.page,
            totalPages = maxOf(1, result.totalPages),
            totalItems = result.totalItems ?: result.items.size,
            unreadCount = unread.totalItems,
        )
    }

    suspend fun createReadingItem(
        server: String,
        token: String,
        ownerId: String,
        input: ReadingItemInput,
    ): ReadingItem =
        request(server, "api/collections/reading_items/records", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateReadingItemPayload(
                    owner = ownerId,
                    url = input.url,
                    title = input.title,
                    description = input.description,
                    tags = input.tags,
                    status = input.status.raw,
                ),
            )
        }

    suspend fun updateReadingStatus(
        server: String,
        token: String,
        id: String,
        status: ReadingStatus,
    ): ReadingItem =
        request(server, "api/collections/reading_items/records/$id", HttpMethod.Patch) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(UpdateReadingStatusPayload(status.raw))
        }

    /** Pin goes through a custom endpoint (server-side ordering), not a collection PATCH. */
    suspend fun setReadingPinned(server: String, token: String, id: String, pinned: Boolean) =
        requestEmpty(server, "api/reading/items/$id/pin", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(ReadingPinPayload(pinned))
        }

    suspend fun deleteReadingItem(server: String, token: String, id: String) =
        requestEmpty(server, "api/collections/reading_items/records/$id", HttpMethod.Delete) {
            header(HttpHeaders.Authorization, token)
        }

    suspend fun summarizeReadingItem(server: String, token: String, id: String): ReadingSummaryResponse =
        request(server, "api/reading/items/$id/summarize", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
        }

    suspend fun markAllReadingItemsRead(server: String, token: String): Int =
        request<MarkAllReadingReadResponse>(server, "api/reading/items/read-all", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
        }.updated

    // ---- Contacts (users directory + favorites, mirrors the iOS contacts API) ----

    @Serializable
    private data class CreateContactFavoritePayload(val owner: String, val contact: String)

    /**
     * Contacts snapshot, matching the iOS getContacts: the full users list and
     * the owner's favorite records are fetched concurrently (both fully paged).
     * There is no server-side search; filtering happens client-side.
     */
    suspend fun getContactsSnapshot(
        server: String,
        token: String,
        ownerId: String,
    ): ContactsSnapshot = coroutineScope {
        val usersDeferred = async {
            listAll<Contact>(server, token, "users", "", "name,id")
        }
        val favoritesDeferred = async {
            listAll<ContactFavoriteRecord>(
                server, token, "contact_favorites",
                "owner = \"${filterLiteral(ownerId)}\"", "-created",
            )
        }
        val favoriteRecordIds = favoritesDeferred.await().associate { it.contact to it.id }
        ContactsSnapshot(
            contacts = usersDeferred.await().map { user ->
                user.copy(
                    avatarUrl = fileUrl(server, user.collectionId, user.id, user.avatar),
                    isFavorite = user.id in favoriteRecordIds,
                )
            },
            favoriteRecordIds = favoriteRecordIds,
        )
    }

    suspend fun createContactFavorite(
        server: String,
        token: String,
        ownerId: String,
        contactId: String,
    ): ContactFavoriteRecord =
        request(server, "api/collections/contact_favorites/records", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(CreateContactFavoritePayload(owner = ownerId, contact = contactId))
        }

    suspend fun deleteContactFavorite(server: String, token: String, id: String) =
        requestEmpty(server, "api/collections/contact_favorites/records/$id", HttpMethod.Delete) {
            header(HttpHeaders.Authorization, token)
        }

    /** Escapes a value embedded in a double-quoted PocketBase filter literal. */
    private fun filterLiteral(raw: String): String =
        raw.replace("\\", "\\\\").replace("\"", "\\\"")

    // ---- Chat (conversations + SSE streaming, mirrors the iOS PocketBaseClient chat API) ----

    @Serializable
    private data class CreateChatConversationPayload(val title: String)

    @Serializable
    private data class ChatApprovalPayload(val approved: Boolean)

    /** Conversation list: pinned first, then by latest activity. page is 1-based. */
    suspend fun getChatConversations(
        server: String,
        token: String,
        page: Int,
        pageSize: Int,
        query: String = "",
        archived: Boolean = false,
    ): ChatConversationsPage {
        var filter = "status = \"${if (archived) "archived" else "active"}\""
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) filter += " && title ~ \"${filterLiteral(trimmed)}\""
        val response = request<RecordListResponse<ChatConversation>>(
            server,
            "api/collections/chat_conversations/records",
            HttpMethod.Get,
        ) {
            header(HttpHeaders.Authorization, token)
            parameter("page", page.toString())
            parameter("perPage", pageSize.toString())
            parameter("sort", if (archived) "-updated" else "-pinned,-last_message_at,-updated")
            parameter("filter", filter)
        }
        return ChatConversationsPage(
            items = response.items,
            page = response.page,
            totalPages = response.totalPages,
            totalItems = response.totalItems ?: response.items.size,
        )
    }

    suspend fun createChatConversation(
        server: String,
        token: String,
        title: String,
    ): ChatConversation =
        request(server, "api/collections/chat_conversations/records", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(CreateChatConversationPayload(title))
        }

    suspend fun updateChatConversation(
        server: String,
        token: String,
        id: String,
        update: ChatConversationUpdate,
    ): ChatConversation =
        request(server, "api/collections/chat_conversations/records/$id", HttpMethod.Patch) {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(update)
        }

    suspend fun deleteChatConversation(server: String, token: String, id: String) =
        requestEmpty(server, "api/collections/chat_conversations/records/$id", HttpMethod.Delete) {
            header(HttpHeaders.Authorization, token)
        }

    /** Persisted message history of one conversation (server is authoritative). */
    suspend fun getChatMessages(
        server: String,
        token: String,
        conversationId: String,
    ): List<ChatMessage> =
        request(server, "api/chat/conversations/$conversationId/messages", HttpMethod.Get) {
            header(HttpHeaders.Authorization, token)
        }

    /**
     * Starts a chat run: POST /api/chat/stream, answered with a
     * text/event-stream of ChatStreamChunk frames terminated by `data: [DONE]`.
     */
    fun chatStream(
        server: String,
        token: String,
        body: ChatStreamRequest,
    ): Flow<ChatStreamChunk> = flow {
        val base = normalizeServerAddress(server)
            ?: throw ApiException(0, "Enter your server address first.")
        http.preparePost("$base/api/chat/stream") {
            header(HttpHeaders.Authorization, token)
            header(HttpHeaders.Accept, "text/event-stream")
            header(HttpHeaders.CacheControl, "no-cache")
            contentType(ContentType.Application.Json)
            setBody(body)
            timeout {
                requestTimeoutMillis = CHAT_REQUEST_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }.execute { response ->
            if (!response.status.isSuccess()) throw parseError(response)
            readChatChunks(response.bodyAsChannel()) { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    /** Re-attaches to an in-flight run (same chunk format as [chatStream]). */
    fun resumeChatStream(
        server: String,
        token: String,
        runId: String,
    ): Flow<ChatStreamChunk> = flow {
        val base = normalizeServerAddress(server)
            ?: throw ApiException(0, "Enter your server address first.")
        http.prepareGet("$base/api/chat/runs/$runId/stream") {
            header(HttpHeaders.Authorization, token)
            header(HttpHeaders.Accept, "text/event-stream")
            header(HttpHeaders.CacheControl, "no-cache")
            timeout {
                requestTimeoutMillis = CHAT_REQUEST_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }.execute { response ->
            if (!response.status.isSuccess()) throw parseError(response)
            readChatChunks(response.bodyAsChannel()) { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun stopChatRun(server: String, token: String, runId: String) =
        requestEmpty(server, "api/chat/runs/$runId/stop", HttpMethod.Post) {
            header(HttpHeaders.Authorization, token)
        }

    suspend fun respondChatApproval(
        server: String,
        token: String,
        runId: String,
        approvalId: String,
        approved: Boolean,
    ) = requestEmpty(
        server,
        "api/chat/runs/$runId/approvals/$approvalId",
        HttpMethod.Post,
    ) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(ChatApprovalPayload(approved))
    }

    /**
     * Reads SSE frames from a chat stream: blank line ends a frame, multiple
     * data: lines are joined with \n, [DONE] ends the stream. Unlike realtime
     * frames, event: lines are ignored (same as the iOS chatStream).
     */
    private suspend fun readChatChunks(
        channel: ByteReadChannel,
        emit: suspend (ChatStreamChunk) -> Unit,
    ) {
        val data = StringBuilder()
        while (true) {
            val line = channel.readUTF8Line(CHAT_SSE_LINE_LIMIT) ?: break
            when {
                line.isEmpty() -> {
                    val payload = data.toString()
                    data.setLength(0)
                    if (payload.isEmpty()) continue
                    if (payload == CHAT_STREAM_DONE) return
                    emit(json.decodeFromString<ChatStreamChunk>(payload))
                }
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substringAfter(':').trim())
                }
                // event:/comment/heartbeat lines are ignored.
            }
        }
    }

    // ---- Realtime subscriptions (PocketBase SSE, same mechanism as iOS realtimeEvents) ----

    @Serializable
    private data class RealtimeSubscriptions(val clientId: String, val subscriptions: List<String>)

    /** One SSE event frame: event is the topic (e.g. notifications/abc123), data is JSON. */
    private data class RealtimeFrame(val event: String, val data: String)

    /** Stream of realtime notification events. */
    fun realtimeNotificationEvents(
        server: String,
        token: String,
    ): Flow<NotificationRealtimeEvent> =
        realtimeFrames(server, token, listOf("notifications/*"))
            .filter { it.event.startsWith("notifications") && it.data.isNotEmpty() }
            .map { json.decodeFromString<NotificationRealtimeEvent>(it.data) }

    /**
     * Stream of board realtime signals: payloads are ignored — any board_*
     * event is just a "go refresh" trigger (same as iOS boardRealtimeSignals).
     */
    fun boardRealtimeSignals(server: String, token: String): Flow<Unit> =
        realtimeFrames(server, token, BOARD_REALTIME_TOPICS)
            .filter { frame -> BOARD_REALTIME_PREFIXES.any { frame.event.startsWith(it) } }
            .map { }

    /**
     * Long-lived SSE connection: GET /api/realtime, then POST the subscription
     * topics once PB_CONNECT arrives, emitting one item per frame afterwards.
     * The flow ends / throws when the server times out (35 minutes) or the
     * connection drops; reconnecting is the caller's responsibility.
     */
    private fun realtimeFrames(
        server: String,
        token: String,
        subscriptions: List<String>,
    ): Flow<RealtimeFrame> = flow {
        val base = normalizeServerAddress(server)
            ?: throw ApiException(0, "Enter your server address first.")

        http.prepareGet("$base/api/realtime") {
            header(HttpHeaders.Accept, "text/event-stream")
            header(HttpHeaders.CacheControl, "no-cache")
            timeout {
                requestTimeoutMillis = REALTIME_REQUEST_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }.execute { response ->
            if (!response.status.isSuccess()) throw parseError(response)
            val channel = response.bodyAsChannel()
            var eventName = ""
            val data = StringBuilder()
            while (true) {
                val line = channel.readUTF8Line(SSE_LINE_LIMIT) ?: break
                when {
                    // Blank line = end of frame; dispatch it.
                    line.isEmpty() -> {
                        val payload = data.toString()
                        if (eventName == REALTIME_CONNECT_EVENT && payload.isNotEmpty()) {
                            val clientId = json.parseToJsonElement(payload)
                                .jsonObject["clientId"]?.jsonPrimitive?.contentOrNull
                            if (clientId != null) {
                                subscribeRealtime(base, token, clientId, subscriptions)
                            }
                        } else if (eventName.isNotEmpty()) {
                            emit(RealtimeFrame(eventName, payload))
                        }
                        eventName = ""
                        data.setLength(0)
                    }
                    line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                    line.startsWith("data:") -> data.append(line.substringAfter(':').trim())
                    // Everything else is a comment / heartbeat line; ignore.
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Submits subscription topics after PB_CONNECT; only this request carries Authorization. */
    private suspend fun subscribeRealtime(
        base: String,
        token: String,
        clientId: String,
        subscriptions: List<String>,
    ) = requestEmpty(base, "api/realtime", HttpMethod.Post) {
        header(HttpHeaders.Authorization, token)
        contentType(ContentType.Application.Json)
        setBody(RealtimeSubscriptions(clientId, subscriptions))
    }

    private suspend inline fun <reified T> request(
        server: String,
        path: String,
        method: HttpMethod,
        crossinline block: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response = send(server, path, method) { block() }
        return try {
            response.body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(
                statusCode = response.status.value,
                message = "The server returned data this version of Workavera could not read.",
                cause = e,
            )
        }
    }

    /** Request whose response body is not parsed (e.g. mark-as-read, delete). */
    private suspend fun requestEmpty(
        server: String,
        path: String,
        method: HttpMethod,
        block: HttpRequestBuilder.() -> Unit = {},
    ) {
        send(server, path, method, block)
    }

    private suspend fun send(
        server: String,
        path: String,
        method: HttpMethod,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val base = normalizeServerAddress(server)
            ?: throw ApiException(0, "Enter your server address first.")

        val response = try {
            http.request("$base/$path") {
                this.method = method
                accept(ContentType.Application.Json)
                block()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(
                statusCode = 0,
                message = "Could not reach the server. Check the address and your connection.",
                cause = e,
            )
        }

        if (!response.status.isSuccess()) throw parseError(response)
        return response
    }

    /** PocketBase error bodies look like {"status":400,"message":"...","data":{field:{"message":"..."}}}. */
    private suspend fun parseError(response: HttpResponse): ApiException {
        val statusCode = response.status.value
        val fallback = "Request failed ($statusCode)."
        val body = try {
            response.bodyAsText()
        } catch (_: Exception) {
            ""
        }
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            null
        } ?: return ApiException(statusCode, fallback)

        val message = root["message"]?.jsonPrimitive?.contentOrNull ?: fallback
        val fieldMessages = buildMap {
            (root["data"] as? JsonObject)?.forEach { (field, value) ->
                val fieldMessage = (value as? JsonObject)
                    ?.get("message")?.jsonPrimitive?.contentOrNull
                if (fieldMessage != null) put(field, fieldMessage)
            }
        }
        return ApiException(statusCode, message, fieldMessages)
    }

    companion object {
        /** Matches iOS: SSE requests time out after 35 minutes; the caller reconnects. */
        private const val REALTIME_REQUEST_TIMEOUT_MS = 35L * 60 * 1000
        private const val REALTIME_CONNECT_EVENT = "PB_CONNECT"
        private const val SSE_LINE_LIMIT = 64 * 1024

        /** Matches iOS: chat stream requests time out after 11 minutes. */
        private const val CHAT_REQUEST_TIMEOUT_MS = 11L * 60 * 1000
        private const val CHAT_STREAM_DONE = "[DONE]"

        /** Chat SSE frames can carry large tool outputs; iOS caps frames at 5 MB. */
        private const val CHAT_SSE_LINE_LIMIT = 6 * 1024 * 1024

        private val BOARD_REALTIME_TOPICS = listOf(
            "board_projects/*",
            "board_project_preferences/*",
            "board_project_states/*",
            "board_tasks/*",
            "board_project_labels/*",
            "board_project_members/*",
        )
        private val BOARD_REALTIME_PREFIXES =
            BOARD_REALTIME_TOPICS.map { it.removeSuffix("/*") }

        private val CALENDAR_REALTIME_TOPICS = listOf("calendar_events/*", "board_tasks/*")
        private val CALENDAR_REALTIME_PREFIXES =
            CALENDAR_REALTIME_TOPICS.map { it.removeSuffix("/*") }

        /** ISO-8601 UTC with milliseconds, matching the iOS dashboardDate format. */
        private val serverInstantFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(ZoneOffset.UTC)

        /** Server date write format (ISO-8601 UTC with milliseconds), shared by write endpoints. */
        fun formatServerInstant(instant: Instant): String =
            serverInstantFormatter.format(instant)

        private fun dashboardDate(instant: Instant): String =
            formatServerInstant(instant)

        /**
         * Adds a scheme if missing (https by default) and strips trailing
         * slashes; returns null for a blank address.
         */
        fun normalizeServerAddress(raw: String): String? {
            var address = raw.trim().trimEnd('/')
            if (address.isEmpty()) return null
            if (!address.startsWith("http://") && !address.startsWith("https://")) {
                address = "https://$address"
            }
            return address
        }
    }
}
