package com.workavera.android.data.model

import kotlinx.serialization.Serializable

/**
 * User presence status, matching the status field of the Workavera users
 * collection. PocketBase returns "" for an empty select field; anything
 * unrecognized falls back to [OFFLINE].
 */
enum class UserStatus(val raw: String, val label: String) {
    ONLINE("online", "Online"),
    AWAY("away", "Away"),
    BUSY("busy", "Busy"),
    OFFLINE("offline", "Offline");

    companion object {
        fun fromRaw(raw: String?): UserStatus =
            entries.firstOrNull { it.raw == raw } ?: OFFLINE
    }
}

/** A record of the PocketBase users collection. */
@Serializable
data class User(
    val id: String,
    val collectionId: String = "users",
    val name: String = "",
    val email: String = "",
    val avatar: String = "",
    val phone: String = "",
    val title: String = "",
    val bio: String = "",
    val status: String = "",
    val created: String = "",
    val updated: String = "",
) {
    val displayName: String get() = name.ifEmpty { email }
    val userStatus: UserStatus get() = UserStatus.fromRaw(status)
}

/** Fields submitted when editing the profile, matching the iOS ProfileUpdate. */
data class ProfileUpdate(
    val name: String,
    val phone: String,
    val title: String,
    val bio: String,
    val status: UserStatus,
)

/** An avatar file pending upload. */
class AvatarUpload(
    val data: ByteArray,
    val fileName: String,
    val mimeType: String,
)

/** Response of POST /api/collections/users/auth-with-password. */
@Serializable
data class AuthResponse(
    val token: String,
    val record: User,
)
