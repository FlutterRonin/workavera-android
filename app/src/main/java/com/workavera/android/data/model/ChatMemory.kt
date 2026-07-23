package com.workavera.android.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Category of a saved chat memory (icons are mapped in the UI layer). */
enum class MemoryCategory(val raw: String, val label: String) {
    PREFERENCE("preference", "Preference"),
    PERSONAL("personal", "Personal"),
    WORK("work", "Work"),
    GOAL("goal", "Goal"),
    CONSTRAINT("constraint", "Constraint");

    companion object {
        fun fromRaw(raw: String?): MemoryCategory =
            entries.firstOrNull { it.raw == raw } ?: PREFERENCE
    }
}

/** How a memory came to be, shown as a subtitle. */
enum class MemoryOrigin(val raw: String, val label: String) {
    MANUAL("manual", "Added manually"),
    EXPLICIT("explicit", "Saved by request"),
    AUTOMATIC("automatic", "Captured automatically");

    companion object {
        fun fromRaw(raw: String?): MemoryOrigin =
            entries.firstOrNull { it.raw == raw } ?: MANUAL
    }
}

/** A record of the PocketBase chat_memories collection. */
@Serializable
data class SavedChatMemory(
    val id: String,
    val owner: String = "",
    val category: String = "preference",
    val content: String = "",
    val active: Boolean = true,
    val origin: String = "manual",
    @SerialName("source_conversation") val sourceConversation: String = "",
    @SerialName("source_message") val sourceMessage: String = "",
    val created: String = "",
    val updated: String = "",
) {
    val memoryCategory: MemoryCategory get() = MemoryCategory.fromRaw(category)
    val memoryOrigin: MemoryOrigin get() = MemoryOrigin.fromRaw(origin)
    val fromChat: Boolean get() = sourceConversation.isNotEmpty()
    val updatedAt: Instant? get() = parseServerDate(updated)
}
