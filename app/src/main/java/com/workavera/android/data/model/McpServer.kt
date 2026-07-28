package com.workavera.android.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One locked tool definition of an MCP server. The definition is a snapshot the
 * user reviewed and accepted; the assistant never sees a live upstream view.
 * `parameters` / `required` are part of the stored definition but are not
 * needed by the management UI, so they are not decoded.
 */
@Serializable
data class McpTool(
    val name: String,
    val description: String = "",
    val enabled: Boolean = false,
    val approval: String = McpToolApproval.ALWAYS.raw,
    val hash: String = "",
    val stale: Boolean = false,
) {
    /** Whether a call must pass the Chat approval gate before contacting upstream. */
    val requiresApproval: Boolean get() = approval != McpToolApproval.NEVER.raw
}

/** Approval mode stored on a locked tool definition. */
enum class McpToolApproval(val raw: String) {
    ALWAYS("always"),
    NEVER("never"),
}

/** Upstream transport, mirroring the backend `transport` select field. */
enum class McpTransport(val raw: String, val label: String) {
    HTTP("http", "Streamable HTTP"),
    SSE("sse", "SSE (legacy)");

    companion object {
        fun fromRaw(raw: String): McpTransport = entries.firstOrNull { it.raw == raw } ?: HTTP
    }
}

/**
 * Server-level policy that pre-selects the approval mode of newly discovered
 * tools after a refresh. Labels and hints mirror the Web settings card.
 */
enum class McpApprovalPolicy(val raw: String, val label: String, val hint: String) {
    WRITES(
        "writes",
        "Ask unless the server says read-only",
        "Trusts the server's own read-only hint. Many servers publish no hint, so their tools will still ask.",
    ),
    ALL(
        "all",
        "Ask for everything",
        "Every tool from this server asks before running, whatever the server claims about itself.",
    ),
    NONE(
        "none",
        "Never ask",
        "Tools run without confirmation. Only for servers you fully trust.",
    );

    companion object {
        fun fromRaw(raw: String): McpApprovalPolicy = entries.firstOrNull { it.raw == raw } ?: WRITES
    }
}

/**
 * A record of the PocketBase mcp_servers collection (snake_case list/view
 * shape). The `headers` field is hidden server-side — stored credentials are
 * write-only and never reach a read path.
 */
@Serializable
data class McpServer(
    val id: String,
    val name: String = "",
    val slug: String = "",
    val transport: String = "http",
    val url: String = "",
    @SerialName("approval_policy") val approvalPolicy: String = "writes",
    val enabled: Boolean = false,
    val tools: List<McpTool> = emptyList(),
    @SerialName("last_error") val lastError: String = "",
    @SerialName("last_refreshed") val lastRefreshed: String = "",
    val created: String = "",
) {
    /** What decides whether the server does anything at all: a disabled tool is never offered. */
    val enabledToolCount: Int get() = tools.count { it.enabled }
    val hasStaleTools: Boolean get() = tools.any { it.stale }
    val lastRefreshedAt: Instant? get() = parseServerDate(lastRefreshed)
}

/**
 * The feature-route response shape (camelCase, from the backend toResponse)
 * returned by create / update / refresh / tool-selection endpoints.
 */
@Serializable
data class McpServerResponse(
    val id: String,
    val name: String = "",
    val slug: String = "",
    val transport: String = "http",
    val url: String = "",
    val approvalPolicy: String = "writes",
    val enabled: Boolean = false,
    val tools: List<McpTool> = emptyList(),
    val headerNames: List<String> = emptyList(),
    val lastError: String = "",
    val lastRefreshed: String = "",
    val created: String = "",
) {
    fun toModel() = McpServer(
        id = id,
        name = name,
        slug = slug,
        transport = transport,
        url = url,
        approvalPolicy = approvalPolicy,
        enabled = enabled,
        tools = tools,
        lastError = lastError,
        lastRefreshed = lastRefreshed,
        created = created,
    )
}

/** Diff produced by a refresh, naming the tools in each bucket. */
@Serializable
data class McpRefreshReport(
    val added: List<String> = emptyList(),
    val changed: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val unchanged: List<String> = emptyList(),
    val unsupported: List<String> = emptyList(),
)

/**
 * Result of POST /api/mcp-servers/{id}/refresh. A failed refresh is still a
 * 200 with [ok] false and [error] set, because the server records the failure
 * as the server's last_error.
 */
@Serializable
data class McpRefreshResult(
    val ok: Boolean,
    val error: String? = null,
    val report: McpRefreshReport? = null,
    val server: McpServerResponse,
) {
    /** Names of the tools the report flags as new or changed (pending review). */
    fun statusOf(tool: McpTool): String? = when {
        report?.added?.contains(tool.name) == true -> "New"
        report?.changed?.contains(tool.name) == true -> "Changed"
        tool.stale -> "May be out of date"
        else -> null
    }
}

/** The user's review decision for one tool; the only tool fields a client may write. */
@Serializable
data class McpToolDecision(
    val name: String,
    val enabled: Boolean,
    val approval: String,
)

/**
 * Parses the request-headers text field: one "Name: value" per line. Returns
 * null when any non-empty line is malformed, so the form can flag it.
 */
fun parseMcpHeaders(raw: String): Map<String, String>? {
    val headers = linkedMapOf<String, String>()
    for (line in raw.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        val separator = trimmed.indexOf(':')
        if (separator <= 0) return null
        headers[trimmed.substring(0, separator).trim()] = trimmed.substring(separator + 1).trim()
    }
    return headers
}
