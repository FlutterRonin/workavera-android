package com.workavera.android.data

import com.workavera.android.data.model.McpRefreshResult
import com.workavera.android.data.model.McpServer
import com.workavera.android.data.model.McpTool
import com.workavera.android.data.model.McpToolDecision
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import kotlinx.serialization.builtins.ListSerializer

/** MCP servers data entry point: session passthrough + disk cache for the list. */
class McpServersRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    private val cacheKey: String
        get() = "mcpservers|${session.server}|${session.user.id}"

    /** Cached servers rendered first when entering the screen; null when absent. */
    fun cachedServers(): List<McpServer>? =
        runCatching { cache.read(cacheKey, ListSerializer(McpServer.serializer())) }.getOrNull()

    /** Network load, writing through to the disk cache on success. */
    suspend fun load(): List<McpServer> {
        val servers = api.getMcpServers(session.server, session.token)
        cache.write(cacheKey, ListSerializer(McpServer.serializer()), servers)
        return servers
    }

    suspend fun create(
        name: String,
        slug: String,
        transport: String,
        url: String,
        headers: Map<String, String>,
        approvalPolicy: String,
    ): McpServer = api.createMcpServer(
        session.server,
        session.token,
        name = name,
        slug = slug,
        transport = transport,
        url = url,
        headers = headers,
        approvalPolicy = approvalPolicy,
    )

    /** [headers] of null leaves the stored credentials untouched (they are write-only). */
    suspend fun update(
        id: String,
        name: String,
        url: String,
        transport: String,
        headers: Map<String, String>?,
        approvalPolicy: String,
    ): McpServer = api.updateMcpServer(
        session.server,
        session.token,
        id,
        name = name,
        url = url,
        transport = transport,
        headers = headers,
        approvalPolicy = approvalPolicy,
    )

    suspend fun setEnabled(id: String, enabled: Boolean): McpServer =
        api.updateMcpServer(session.server, session.token, id, enabled = enabled)

    suspend fun refresh(id: String): McpRefreshResult =
        api.refreshMcpServer(session.server, session.token, id)

    /** Applies the reviewed enabled/approval selection for the server's tools. */
    suspend fun saveToolSelection(id: String, tools: List<McpTool>): McpServer =
        api.updateMcpTools(
            session.server,
            session.token,
            id,
            tools.map { McpToolDecision(name = it.name, enabled = it.enabled, approval = it.approval) },
        )

    suspend fun delete(id: String) =
        api.deleteMcpServer(session.server, session.token, id)
}
