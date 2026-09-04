package com.workavera.android.data

import com.workavera.android.data.model.CandidateSummary
import com.workavera.android.data.model.DiscoverResponse
import com.workavera.android.data.model.ReadingItem
import com.workavera.android.data.model.ReadingItemInput
import com.workavera.android.data.model.ReadingListFilter
import com.workavera.android.data.model.ReadingPage
import com.workavera.android.data.model.ReadingSource
import com.workavera.android.data.model.ReadingSourceInput
import com.workavera.android.data.model.ReadingStatus
import com.workavera.android.data.model.ReadingSummaryResponse
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi

/** Reading data entry point: session passthrough + disk cache for pages. */
class ReadingRepository(
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

    private fun pageCacheKey(filter: ReadingListFilter, query: String, page: Int) =
        "reading.page|${session.server}|${session.user.id}|${filter.raw}|${query.trim()}|$page"

    /** Cached page rendered first when entering the screen; null when absent. */
    fun cachedPage(filter: ReadingListFilter, query: String, page: Int): ReadingPage? =
        cache.read(pageCacheKey(filter, query, page), ReadingPage.serializer())

    /** Network load, writing through to the disk cache on success. [page] is 1-based. */
    suspend fun loadPage(filter: ReadingListFilter, page: Int, pageSize: Int, query: String): ReadingPage {
        val snapshot = api.getReadingPage(session.server, session.token, filter, page, pageSize, query)
        cache.write(pageCacheKey(filter, query, page), ReadingPage.serializer(), snapshot)
        return snapshot
    }

    suspend fun create(input: ReadingItemInput): ReadingItem =
        api.createReadingItem(session.server, session.token, session.user.id, input)

    suspend fun updateStatus(id: String, status: ReadingStatus): ReadingItem =
        api.updateReadingStatus(session.server, session.token, id, status)

    suspend fun setPinned(id: String, pinned: Boolean) =
        api.setReadingPinned(session.server, session.token, id, pinned)

    suspend fun delete(id: String) =
        api.deleteReadingItem(session.server, session.token, id)

    suspend fun summarize(id: String): ReadingSummaryResponse =
        api.summarizeReadingItem(session.server, session.token, id)

    suspend fun markAllRead(): Int =
        api.markAllReadingItemsRead(session.server, session.token)

    // ---- Subscriptions (discover) ----

    suspend fun loadSources(): List<ReadingSource> =
        api.getReadingSources(session.server, session.token)

    suspend fun createSource(input: ReadingSourceInput) =
        api.createReadingSource(session.server, session.token, session.user.id, input)

    suspend fun updateSource(id: String, input: ReadingSourceInput) =
        api.updateReadingSource(session.server, session.token, id, input)

    suspend fun setSourceEnabled(id: String, enabled: Boolean) =
        api.setReadingSourceEnabled(session.server, session.token, id, enabled)

    suspend fun deleteSource(id: String) =
        api.deleteReadingSource(session.server, session.token, id)

    suspend fun discover(sourceIds: List<String>): DiscoverResponse =
        api.discoverReadingCandidates(session.server, session.token, sourceIds)

    suspend fun summarizeCandidate(url: String, title: String, language: String): CandidateSummary =
        api.summarizeReadingCandidate(session.server, session.token, url, title, language)
}
