package com.workavera.android.data.model

import com.workavera.android.data.network.PocketBaseApi
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/** Document body format, matching the iOS DocumentKind. */
enum class DocumentKind(val raw: String) {
    MARKDOWN("markdown"),
    HTML("html");

    companion object {
        fun fromRaw(raw: String?): DocumentKind = entries.firstOrNull { it.raw == raw } ?: MARKDOWN
    }
}

/** Document lifecycle state, matching the iOS DocumentStatus. */
enum class DocumentStatus(val raw: String) {
    DRAFT("draft"),
    ARCHIVED("archived");

    companion object {
        fun fromRaw(raw: String?): DocumentStatus = entries.firstOrNull { it.raw == raw } ?: DRAFT
    }
}

/** Which slice of the workspace docs is shown, matching the iOS DocsViewFilter. */
sealed interface DocsViewFilter {
    data object Recent : DocsViewFilter
    data object Pinned : DocsViewFilter
    data object MyDocuments : DocsViewFilter
    data class Folder(val id: String) : DocsViewFilter
    data class Project(val id: String) : DocsViewFilter
    data object Archived : DocsViewFilter

    /** Recent and Pinned are fixed-size lists; everything else pages. */
    val usesPagination: Boolean
        get() = this !is Recent && this !is Pinned

    /** Stable key for cache namespacing. */
    val cacheKey: String
        get() = when (this) {
            Recent -> "recent"
            Pinned -> "pinned"
            MyDocuments -> "my"
            is Folder -> "folder:$id"
            is Project -> "project:$id"
            Archived -> "archived"
        }
}

@Serializable
data class DocumentFolder(val id: String, val name: String = "")

@Serializable
data class DocumentProject(val id: String, val name: String = "")

/** One row in the docs list (no content), matching the iOS DocumentSummary. */
@Serializable
data class DocumentSummary(
    val id: String,
    val title: String = "",
    val kind: String = "markdown",
    val ownerId: String = "",
    val projectId: String? = null,
    val folderId: String? = null,
    val status: String = "draft",
    val revision: Int = 1,
    val updated: String = "",
    val pinned: Boolean = false,
) {
    val documentKind: DocumentKind get() = DocumentKind.fromRaw(kind)
    val documentStatus: DocumentStatus get() = DocumentStatus.fromRaw(status)
    val updatedAt: Instant? get() = parseServerDate(updated)
}

/** A full document with content, matching the iOS WorkspaceDocument. */
@Serializable
data class WorkspaceDocument(
    val id: String,
    val title: String = "",
    val kind: String = "markdown",
    val content: String = "",
    val ownerId: String = "",
    val projectId: String? = null,
    val folderId: String? = null,
    val status: String = "draft",
    val revision: Int = 1,
    val lastEditedBy: String = "",
    val created: String = "",
    val updated: String = "",
    val pinned: Boolean = false,
) {
    val documentKind: DocumentKind get() = DocumentKind.fromRaw(kind)
    val documentStatus: DocumentStatus get() = DocumentStatus.fromRaw(status)
    val createdAt: Instant? get() = parseServerDate(created)
    val updatedAt: Instant? get() = parseServerDate(updated)
}

/**
 * A document's public link, matching the iOS DocumentShare. [revision] is the
 * snapshot the link serves, which stays where it is while the document is
 * edited; an empty [expires] means the link never expires.
 */
@Serializable
data class DocumentShare(
    val docId: String = "",
    val slug: String = "",
    val revision: Int = 0,
    val expires: String = "",
    val created: String = "",
    val updated: String = "",
) {
    val expiresAt: Instant? get() = if (expires.isEmpty()) null else parseServerDate(expires)
}

/** Expiry choices offered when publishing, matching the iOS DocumentShareExpiry. */
enum class DocumentShareExpiry(val label: String, val days: Int?) {
    /** Leaves an existing expiry untouched while other fields change. */
    KEEP("Keep current expiry", null),
    NEVER("No expiry", null),
    SEVEN_DAYS("7 days from now", 7),
    THIRTY_DAYS("30 days from now", 30);

    /** Expiry for the request body; an empty string means "never expires". */
    fun requestValue(share: DocumentShare?, now: Instant = Instant.now()): String =
        requestValue(share?.expires, now)

    /**
     * Same as [requestValue] but from a share record's raw `expires` value, so
     * other share types (e.g. board project links) can reuse the options.
     */
    fun requestValue(currentExpires: String?, now: Instant = Instant.now()): String {
        if (this == KEEP) return currentExpires.orEmpty()
        val dayCount = days ?: return ""
        return PocketBaseApi.formatServerInstant(now.plus(dayCount.toLong(), ChronoUnit.DAYS))
    }
}

/** One saved revision of a document, matching the iOS DocumentVersion. */
@Serializable
data class DocumentVersion(
    val id: String,
    val revision: Int = 0,
    val title: String = "",
    val content: String? = null,
    val createdBy: String = "",
    val source: String = "",
    val created: String = "",
) {
    val createdAt: Instant? get() = parseServerDate(created)
}

/** One page of the docs list plus the browse targets, matching the iOS DocsPage. */
@Serializable
data class DocsPage(
    val documents: List<DocumentSummary> = emptyList(),
    val folders: List<DocumentFolder> = emptyList(),
    val projects: List<DocumentProject> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
)
