package com.workavera.android.data.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/** Task priority, matching the iOS TaskPriority (colors are mapped in the UI layer). */
enum class TaskPriority(val raw: String, val label: String) {
    NONE("none", "No priority"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    URGENT("urgent", "Urgent");

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

/** Category of a board state (column). */
enum class BoardStateCategory(val raw: String) {
    PENDING("pending"),
    ACTIVE("active"),
    COMPLETED("completed"),
    UNKNOWN("");

    companion object {
        fun fromRaw(raw: String?): BoardStateCategory =
            entries.firstOrNull { it.raw == raw } ?: UNKNOWN
    }
}

@Serializable
data class BoardLabel(
    val id: String,
    val projectId: String,
    val name: String,
    /** Server-side hex color, e.g. #3b82f6. */
    val color: String,
)

@Serializable
data class BoardMember(
    val id: String,
    val name: String,
    val avatarUrl: String?,
)

@Serializable
data class BoardTask(
    val id: String,
    val projectId: String,
    val stateId: String,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    @Serializable(with = InstantSerializer::class)
    val dueDate: Instant?,
    val labels: List<BoardLabel>,
    val assignees: List<BoardMember>,
    val documentCount: Int,
    val rank: Double,
) {
    val isOverdue: Boolean
        get() = dueDate?.isBefore(Instant.now()) == true
}

@Serializable
data class BoardState(
    val id: String,
    val projectId: String,
    val name: String,
    val color: String,
    val category: BoardStateCategory,
    val sortOrder: Double,
    val tasks: List<BoardTask>,
)

@Serializable
data class BoardProject(
    val id: String,
    val ownerId: String,
    val name: String,
    val description: String,
    val members: List<BoardMember>,
    val labels: List<BoardLabel>,
    val states: List<BoardState>,
) {
    val taskCount: Int get() = states.sumOf { it.tasks.size }
    val labelCount: Int get() = labels.size
}

/** Board page result (page is PocketBase's 1-based page number). */
@Serializable
data class BoardPage(
    val projects: List<BoardProject>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Int,
)

private val pocketBaseDateFormat =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** Parses a server date (ISO-8601 or PocketBase's space-separated format); returns null for an empty string. */
fun parseServerDate(raw: String): Instant? {
    if (raw.isEmpty()) return null
    return try {
        Instant.parse(raw)
    } catch (_: Exception) {
        try {
            Instant.from(pocketBaseDateFormat.parse(raw))
        } catch (_: Exception) {
            null
        }
    }
}
