package com.workavera.android.data

import com.workavera.android.data.model.LlmModel
import com.workavera.android.data.model.LlmModelInput
import com.workavera.android.data.model.LlmShareTarget
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import kotlinx.serialization.builtins.ListSerializer

/** LLM model configurations data entry point: session passthrough + disk cache for the list. */
class LlmModelsRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    private val cacheKey: String
        get() = "llm_models|${session.server}|${session.user.id}"

    /** Cached models rendered first when entering the screen; null when absent. */
    fun cachedModels(): List<LlmModel>? =
        runCatching { cache.read(cacheKey, ListSerializer(LlmModel.serializer())) }.getOrNull()

    /** Network load, writing through to the disk cache on success. */
    suspend fun loadModels(): List<LlmModel> {
        val models = api.getLlmModels(session.server, session.token)
        cache.write(cacheKey, ListSerializer(LlmModel.serializer()), models)
        return models
    }

    suspend fun create(input: LlmModelInput): LlmModel =
        api.createLlmModel(session.server, session.token, input)

    suspend fun update(id: String, input: LlmModelInput): LlmModel =
        api.updateLlmModel(session.server, session.token, id, input)

    suspend fun delete(id: String) =
        api.deleteLlmModel(session.server, session.token, id)

    suspend fun setDefault(id: String): LlmModel =
        api.setDefaultLlmModel(session.server, session.token, id)

    suspend fun loadShareTargets(): List<LlmShareTarget> =
        api.getLlmShareTargets(session.server, session.token)

    suspend fun share(id: String, userIds: List<String>): Int =
        api.shareLlmModel(session.server, session.token, id, userIds)
}
