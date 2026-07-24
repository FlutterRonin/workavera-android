package com.workavera.android.data

import com.workavera.android.data.model.DocsPage
import com.workavera.android.data.model.DocsViewFilter
import com.workavera.android.data.model.DocumentVersion
import com.workavera.android.data.model.WorkspaceDocument
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import kotlinx.serialization.builtins.ListSerializer

/** Docs data entry point: session passthrough + disk cache for pages and documents. */
class DocsRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    private fun pageCacheKey(view: DocsViewFilter, query: String, page: Int) =
        "docs.page|${session.server}|${session.user.id}|${view.cacheKey}|${query.trim()}|$page"

    private fun documentCacheKey(id: String) = "docs.doc|${session.server}|${session.user.id}|$id"

    private fun versionsCacheKey(id: String) = "docs.versions|${session.server}|${session.user.id}|$id"

    private fun versionCacheKey(id: String, revision: Int) =
        "docs.version|${session.server}|${session.user.id}|$id|$revision"

    /** Cached page rendered first when entering the screen; null when absent. */
    fun cachedPage(view: DocsViewFilter, query: String, page: Int): DocsPage? =
        cache.read(pageCacheKey(view, query, page), DocsPage.serializer())

    /** Network load, writing through to the disk cache on success. [page] is 1-based. */
    suspend fun loadPage(view: DocsViewFilter, page: Int, pageSize: Int, query: String): DocsPage {
        val snapshot = api.getDocsPage(
            session.server, session.token, session.user.id, view, page, pageSize, query,
        )
        cache.write(pageCacheKey(view, query, page), DocsPage.serializer(), snapshot)
        return snapshot
    }

    fun cachedDocument(id: String): WorkspaceDocument? =
        cache.read(documentCacheKey(id), WorkspaceDocument.serializer())

    suspend fun loadDocument(id: String, pinned: Boolean): WorkspaceDocument {
        val document = api.getDocument(session.server, session.token, id, pinned)
        cache.write(documentCacheKey(id), WorkspaceDocument.serializer(), document)
        return document
    }

    fun cachedVersions(id: String): List<DocumentVersion>? =
        cache.read(versionsCacheKey(id), ListSerializer(DocumentVersion.serializer()))

    suspend fun loadVersions(id: String): List<DocumentVersion> {
        val versions = api.getDocumentVersions(session.server, session.token, id)
        cache.write(versionsCacheKey(id), ListSerializer(DocumentVersion.serializer()), versions)
        return versions
    }

    fun cachedVersion(id: String, revision: Int): DocumentVersion? =
        cache.read(versionCacheKey(id, revision), DocumentVersion.serializer())

    suspend fun loadVersion(id: String, revision: Int): DocumentVersion {
        val version = api.getDocumentVersion(session.server, session.token, id, revision)
        cache.write(versionCacheKey(id, revision), DocumentVersion.serializer(), version)
        return version
    }

    suspend fun setPinned(id: String, pinned: Boolean) =
        api.setDocumentPinned(session.server, session.token, id, pinned)

    suspend fun setArchived(id: String, archived: Boolean) =
        api.setDocumentArchived(session.server, session.token, id, archived)

    suspend fun delete(id: String) =
        api.deleteDocument(session.server, session.token, id)
}
