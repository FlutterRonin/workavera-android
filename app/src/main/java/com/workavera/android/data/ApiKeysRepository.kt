package com.workavera.android.data

import com.workavera.android.data.model.ApiKey
import com.workavera.android.data.model.ApiKeyExpiration
import com.workavera.android.data.model.CreatedApiKey
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import kotlinx.serialization.builtins.ListSerializer

/** API keys data entry point: session passthrough + disk cache for the list. */
class ApiKeysRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    private val cacheKey: String
        get() = "apikeys|${session.server}|${session.user.id}"

    /** Current server address, for building the MCP client configuration snippet. */
    val serverAddress: String
        get() = (authRepository.state.value as? AuthState.LoggedIn)?.server ?: ""

    /** Cached keys rendered first when entering the screen; null when absent. */
    fun cachedKeys(): List<ApiKey>? =
        runCatching { cache.read(cacheKey, ListSerializer(ApiKey.serializer())) }.getOrNull()

    /** Network load, writing through to the disk cache on success. */
    suspend fun load(): List<ApiKey> {
        val keys = api.getApiKeys(session.server, session.token)
        cache.write(cacheKey, ListSerializer(ApiKey.serializer()), keys)
        return keys
    }

    suspend fun create(
        name: String,
        expiration: ApiKeyExpiration,
        allowDestructive: Boolean,
    ): CreatedApiKey = api.createApiKey(
        session.server,
        session.token,
        name = name,
        expires = expiration.requestValue(),
        allowDestructive = allowDestructive,
    )

    suspend fun revoke(id: String) =
        api.revokeApiKey(session.server, session.token, id)
}
