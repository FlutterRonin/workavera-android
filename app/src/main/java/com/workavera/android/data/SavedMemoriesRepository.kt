package com.workavera.android.data

import com.workavera.android.data.model.MemoryCategory
import com.workavera.android.data.model.SavedChatMemory
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import kotlinx.serialization.builtins.ListSerializer

/** Saved memories data entry point: session passthrough + disk cache for the list. */
class SavedMemoriesRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    private val cacheKey: String
        get() = "chat_memories|${session.server}|${session.user.id}"

    /** Cached memories rendered first when entering the screen; null when absent. */
    fun cachedMemories(): List<SavedChatMemory>? =
        runCatching { cache.read(cacheKey, ListSerializer(SavedChatMemory.serializer())) }.getOrNull()

    /** Network load, writing through to the disk cache on success. */
    suspend fun load(): List<SavedChatMemory> {
        val memories = api.getSavedMemories(session.server, session.token)
        cache.write(cacheKey, ListSerializer(SavedChatMemory.serializer()), memories)
        return memories
    }

    suspend fun create(category: MemoryCategory, content: String): SavedChatMemory =
        api.createSavedMemory(session.server, session.token, category, content)

    suspend fun update(id: String, category: MemoryCategory, content: String): SavedChatMemory =
        api.updateSavedMemory(session.server, session.token, id, category, content)

    suspend fun setActive(id: String, active: Boolean): SavedChatMemory =
        api.setSavedMemoryActive(session.server, session.token, id, active)

    suspend fun delete(id: String) =
        api.deleteSavedMemory(session.server, session.token, id)
}
