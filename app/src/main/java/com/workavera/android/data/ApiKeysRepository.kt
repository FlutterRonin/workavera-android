package com.workavera.android.data

import com.workavera.android.data.model.ApiKey
import com.workavera.android.data.model.ApiKeyExpiration
import com.workavera.android.data.model.CreatedApiKey
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi

/** API keys data entry point: pulls server/token from the current session and forwards to [PocketBaseApi]. */
class ApiKeysRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    /** Current server address, for building the MCP client configuration snippet. */
    val serverAddress: String
        get() = (authRepository.state.value as? AuthState.LoggedIn)?.server ?: ""

    suspend fun load(): List<ApiKey> =
        api.getApiKeys(session.server, session.token)

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
