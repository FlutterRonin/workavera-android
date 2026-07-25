package com.workavera.android.data

import com.workavera.android.data.model.ContactFavoriteRecord
import com.workavera.android.data.model.ContactsSnapshot
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi

/** Contacts data entry point: session passthrough + disk cache for the directory snapshot. */
class ContactsRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val cache: ResponseCache,
) {

    private val session: AuthState.LoggedIn
        get() = authRepository.state.value as? AuthState.LoggedIn
            ?: throw ApiException(401, "Your session is no longer available. Sign in again.")

    /** Signed-in user id without throwing; guards the self-favorite rule. */
    val currentUserId: String?
        get() = (authRepository.state.value as? AuthState.LoggedIn)?.user?.id

    private fun snapshotCacheKey() = "contacts|${session.server}|${session.user.id}"

    /** Cached snapshot rendered first when entering the screen; null when absent. */
    fun cachedSnapshot(): ContactsSnapshot? =
        cache.read(snapshotCacheKey(), ContactsSnapshot.serializer())

    /** Network load, writing through to the disk cache on success. */
    suspend fun loadSnapshot(): ContactsSnapshot {
        val snapshot = api.getContactsSnapshot(session.server, session.token, session.user.id)
        cache.write(snapshotCacheKey(), ContactsSnapshot.serializer(), snapshot)
        return snapshot
    }

    suspend fun createFavorite(contactId: String): ContactFavoriteRecord =
        api.createContactFavorite(session.server, session.token, session.user.id, contactId)

    suspend fun deleteFavorite(id: String) =
        api.deleteContactFavorite(session.server, session.token, id)
}
