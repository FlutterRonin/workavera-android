package com.workavera.android.data.model

import java.net.URI
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reading item lifecycle state, matching the iOS ReadingStatus. */
enum class ReadingStatus(val raw: String, val label: String) {
    UNREAD("unread", "Unread"),
    READ("read", "Read"),
    ARCHIVED("archived", "Archived");

    companion object {
        fun fromRaw(raw: String?): ReadingStatus = entries.firstOrNull { it.raw == raw } ?: UNREAD
    }
}

/** Which slice of the reading list is shown, matching the iOS ReadingListFilter. */
enum class ReadingListFilter(val raw: String, val label: String) {
    ACTIVE("active", "All"),
    UNREAD("unread", "Unread"),
    READ("read", "Read"),
    PINNED("pinned", "Pinned"),
    ARCHIVED("archived", "Archived"),
}

/**
 * A record of the PocketBase reading_items collection, matching the iOS
 * ReadingItem. The list endpoint deliberately omits content_text; it only
 * arrives via status updates or the summarize endpoint.
 */
@Serializable
data class ReadingItem(
    val id: String,
    val owner: String = "",
    val project: String = "",
    val url: String = "",
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val status: String = "unread",
    val pinned: Boolean = false,
    @SerialName("content_text") val contentText: String = "",
    val summary: String = "",
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
    @SerialName("summary_language") val summaryLanguage: String? = null,
    val created: String = "",
    val updated: String = "",
) {
    val readingStatus: ReadingStatus get() = ReadingStatus.fromRaw(status)
    val projectId: String? get() = project.ifEmpty { null }

    /** URL host without any "www."; falls back to the raw URL when it does not parse. */
    val host: String
        get() {
            val parsed = try {
                URI(url).host
            } catch (_: Exception) {
                null
            }
            return if (parsed.isNullOrEmpty()) url else parsed.replace("www.", "")
        }

    /** Card preview text: summary first, then description, then the URL itself. */
    val preview: String
        get() = when {
            summary.isNotEmpty() -> summary
            description.isNotEmpty() -> description
            else -> url
        }

    val updatedAt: Instant? get() = parseServerDate(updated)
}

/** One page of the reading list plus the global unread count, matching the iOS ReadingPage. */
@Serializable
data class ReadingPage(
    val items: List<ReadingItem> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val unreadCount: Int = 0,
)

/** Fields the add sheet collects, matching the iOS ReadingItemInput. */
data class ReadingItemInput(
    val url: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val status: ReadingStatus = ReadingStatus.UNREAD,
)

/** Answer of POST /api/reading/items/{id}/summarize, matching the iOS ReadingSummaryResponse. */
@Serializable
data class ReadingSummaryResponse(
    @SerialName("content_text") val contentText: String = "",
    val summary: String = "",
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
)
