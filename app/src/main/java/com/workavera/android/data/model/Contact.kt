package com.workavera.android.data.model

import kotlinx.serialization.Serializable

/**
 * A users-collection record as shown in the contacts directory, matching the
 * iOS Contact. avatarUrl / isFavorite are not record fields; they are filled
 * in when the snapshot is assembled (the record only carries the avatar file
 * name, and PocketBase returns "" for an empty select/status field).
 */
@Serializable
data class Contact(
    val id: String,
    val collectionId: String = "users",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val title: String = "",
    val bio: String = "",
    val status: String = "",
    val avatar: String = "",
    val avatarUrl: String? = null,
    val isFavorite: Boolean = false,
) {
    val displayName: String get() = name.ifEmpty { email }
    val userStatus: UserStatus get() = UserStatus.fromRaw(status)
    val phoneOrNull: String? get() = phone.ifEmpty { null }
    val titleOrNull: String? get() = title.ifEmpty { null }
    val bioOrNull: String? get() = bio.ifEmpty { null }
}

/** A record of the PocketBase contact_favorites collection (owner favorites contact). */
@Serializable
data class ContactFavoriteRecord(
    val id: String,
    val owner: String = "",
    val contact: String = "",
)

/**
 * Directory plus the favorite record ids keyed by contact user id, matching
 * the iOS ContactsSnapshot; the record id is needed to DELETE a favorite.
 */
@Serializable
data class ContactsSnapshot(
    val contacts: List<Contact> = emptyList(),
    val favoriteRecordIds: Map<String, String> = emptyMap(),
)
