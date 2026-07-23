package com.workavera.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Notification type, matching the iOS AppNotificationType; unrecognized raw values fall back to UNKNOWN. */
enum class AppNotificationType(val raw: String) {
    MODEL_SHARE("model_share"),
    TASK_DUE("task_due"),
    CALENDAR_EVENT("calendar_event"),
    UNKNOWN("");

    companion object {
        fun fromRaw(raw: String?): AppNotificationType =
            entries.firstOrNull { it.raw == raw } ?: UNKNOWN
    }
}

/** Handling status of a model-share notification. */
enum class ModelShareStatus(val raw: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    REJECTED("rejected");

    companion object {
        fun fromRaw(raw: String?): ModelShareStatus? =
            entries.firstOrNull { it.raw == raw }
    }
}

/** Extra notification data (currently only model shares carry it). */
@Serializable
data class AppNotificationData(
    val senderId: String? = null,
    val sourceModelId: String? = null,
    val senderName: String? = null,
    val modelName: String? = null,
    val shareStatus: String? = null,
) {
    val modelShareStatus: ModelShareStatus
        get() = ModelShareStatus.fromRaw(shareStatus) ?: ModelShareStatus.PENDING
}

/**
 * A record of the PocketBase notifications collection.
 * An empty read_at means unread; created is a PocketBase date string.
 */
@Serializable
data class AppNotification(
    val id: String,
    val recipient: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val data: AppNotificationData = AppNotificationData(),
    @SerialName("read_at") val readAt: String = "",
    val status: String = "active",
    val pinned: Boolean = false,
    val created: String = "",
) {
    val notificationType: AppNotificationType get() = AppNotificationType.fromRaw(type)
    val isRead: Boolean get() = readAt.isNotEmpty()
}

/** Page result, matching the iOS NotificationPage (page is PocketBase's 1-based page number). */
@Serializable
data class NotificationPage(
    val items: List<AppNotification>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Int,
)

/** A PocketBase SSE realtime event; action is create/update/delete. */
@Serializable
data class NotificationRealtimeEvent(
    val action: String = "",
    val record: AppNotification,
)

/** PocketBase list response envelope. */
@Serializable
data class RecordListResponse<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int? = null,
)
