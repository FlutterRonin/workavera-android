package com.workavera.android.data.model

import java.time.Instant
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
