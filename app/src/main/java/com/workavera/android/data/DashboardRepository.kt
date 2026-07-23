package com.workavera.android.data

import com.workavera.android.data.model.DashboardSummary
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi

/** Dashboard data entry point: pulls server/token from the current session and forwards to [PocketBaseApi]. */
class DashboardRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val cacheKey: String
        get() {
            val session = authRepository.state.value as? AuthState.LoggedIn
                ?: throw ApiException(401, "Your session is no longer available. Sign in again.")
            return "dashboard|${session.server}|${session.user.id}"
        }

    /** Writes through to the disk cache after a successful network load. */
    suspend fun loadSummary(): DashboardSummary {
        val session = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")
        val summary = api.getDashboardSummary(session.server, session.token)
        cache.write(cacheKey, DashboardSummary.serializer(), summary)
        return summary
    }

    /** Cached snapshot rendered first when entering the screen; null when absent. */
    fun cachedSummary(): DashboardSummary? =
        cache.read(cacheKey, DashboardSummary.serializer())
}
