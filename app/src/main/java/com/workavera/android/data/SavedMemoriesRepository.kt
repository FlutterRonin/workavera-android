package com.workavera.android.data

import com.workavera.android.data.model.MemoryCategory
import com.workavera.android.data.model.SavedChatMemory
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi

/** Saved memories data entry point: pulls server/token from the current session and forwards to [PocketBaseApi]. */
class SavedMemoriesRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    suspend fun load(): List<SavedChatMemory> =
        api.getSavedMemories(session.server, session.token)

    suspend fun create(category: MemoryCategory, content: String): SavedChatMemory =
        api.createSavedMemory(session.server, session.token, category, content)

    suspend fun update(id: String, category: MemoryCategory, content: String): SavedChatMemory =
        api.updateSavedMemory(session.server, session.token, id, category, content)

    suspend fun setActive(id: String, active: Boolean): SavedChatMemory =
        api.setSavedMemoryActive(session.server, session.token, id, active)

    suspend fun delete(id: String) =
        api.deleteSavedMemory(session.server, session.token, id)
}
