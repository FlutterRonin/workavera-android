package com.workavera.android.data.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Event accent color (hex mapped in the UI layer), matching the server select values. */
enum class EventColor(val raw: String, val hex: String) {
    BLUE("blue", "#3b82f6"),
    GREEN("green", "#22c55e"),
    AMBER("amber", "#f59e0b"),
    RED("red", "#ef4444"),
    PURPLE("purple", "#8b5cf6");

    companion object {
        fun fromRaw(raw: String?): EventColor = entries.firstOrNull { it.raw == raw } ?: BLUE
    }
}

enum class EventRecurrenceFrequency(val raw: String, val label: String) {
    NONE("none", "Does not repeat"),
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly"),
    YEARLY("yearly", "Yearly");

    companion object {
        fun fromRaw(raw: String?): EventRecurrenceFrequency =
            entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

enum class EventReminder(val minutes: Int, val label: String) {
    NONE(-1, "No reminder"),
    AT_START(0, "At start time"),
    FIVE_MINUTES(5, "5 minutes before"),
    TEN_MINUTES(10, "10 minutes before"),
    THIRTY_MINUTES(30, "30 minutes before"),
    ONE_HOUR(60, "1 hour before"),
    ONE_DAY(1440, "1 day before");

    companion object {
        fun fromMinutes(minutes: Int?): EventReminder =
            entries.firstOrNull { it.minutes == minutes } ?: NONE
    }
}

/** A single occurrence of an event on a specific day (after expanding recurrence). */
data class EventOccurrence(
    val event: CalendarEvent,
    val startAt: Instant,
    val endAt: Instant,
) {
    val id: String get() = "${event.id}:${startAt.epochSecond}"
}

/** A record of the PocketBase calendar_events collection. */
@Serializable
data class CalendarEvent(
    val id: String,
    val owner: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("start_at") val startAtRaw: String = "",
    @SerialName("end_at") val endAtRaw: String = "",
    @SerialName("all_day") val allDay: Boolean = false,
    val timezone: String = "",
    val location: String = "",
    val color: String = "blue",
    @SerialName("recurrence_frequency") val recurrenceFrequency: String = "none",
    @SerialName("recurrence_interval") val recurrenceInterval: Int = 1,
    @SerialName("reminder_minutes_before") val reminderMinutesBefore: Int = -1,
) {
    val startAt: Instant get() = parseServerDate(startAtRaw) ?: Instant.EPOCH
    val endAt: Instant get() = parseServerDate(endAtRaw) ?: startAt
    val eventColor: EventColor get() = EventColor.fromRaw(color)
    val frequency: EventRecurrenceFrequency get() = EventRecurrenceFrequency.fromRaw(recurrenceFrequency)
    val reminder: EventReminder get() = EventReminder.fromMinutes(reminderMinutesBefore)
    val interval: Int get() = maxOf(1, recurrenceInterval)

    private fun eventZone(displayZone: ZoneId): ZoneId =
        runCatching { ZoneId.of(timezone) }.getOrDefault(displayZone)

    /** Occurrences of this event overlapping [day] (in [displayZone]), expanding recurrence. */
    fun occurrencesOverlapping(day: LocalDate, displayZone: ZoneId): List<EventOccurrence> {
        val dayStart = day.atStartOfDay(displayZone).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(displayZone).toInstant()
        val duration = Duration.between(startAt, endAt)
        if (duration.isNegative || duration.isZero) return emptyList()
        val zone = eventZone(displayZone)
        val result = mutableListOf<EventOccurrence>()
        var index = 0
        while (index < 100_000) {
            val (date, marker) = recurrenceDate(index, zone)
            if (!marker.isBefore(dayEnd)) break
            if (date == null) {
                if (frequency == EventRecurrenceFrequency.NONE) break else { index++; continue }
            }
            if (!date.isBefore(dayEnd)) break
            val occurrenceEnd = date.plus(duration)
            if (occurrenceEnd.isAfter(dayStart)) {
                result.add(EventOccurrence(this, date, occurrenceEnd))
            }
            if (frequency == EventRecurrenceFrequency.NONE) break
            index++
        }
        return result
    }

    private fun recurrenceDate(index: Int, zone: ZoneId): Pair<Instant?, Instant> {
        val step = interval
        val base = startAt.atZone(zone)
        return when (frequency) {
            EventRecurrenceFrequency.NONE ->
                (if (index == 0) startAt else null) to (if (index == 0) startAt else Instant.MAX)
            EventRecurrenceFrequency.DAILY -> {
                val date = base.plusDays((step.toLong() * index)).toInstant()
                date to date
            }
            EventRecurrenceFrequency.WEEKLY -> {
                val date = base.plusWeeks(step.toLong() * index).toInstant()
                date to date
            }
            EventRecurrenceFrequency.MONTHLY -> monthOccurrence(step * index, zone)
            EventRecurrenceFrequency.YEARLY -> monthOccurrence(step * index * 12, zone)
        }
    }

    private fun monthOccurrence(offset: Int, zone: ZoneId): Pair<Instant?, Instant> {
        val base = startAt.atZone(zone)
        val monthIndex = base.year * 12 + (base.monthValue - 1) + offset
        val year = Math.floorDiv(monthIndex, 12)
        val month = Math.floorMod(monthIndex, 12) + 1
        val marker = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, zone).toInstant()
        if (base.dayOfMonth > YearMonth.of(year, month).lengthOfMonth()) return null to marker
        val occurrence = ZonedDateTime.of(
            year, month, base.dayOfMonth, base.hour, base.minute, base.second, 0, zone,
        ).toInstant()
        return occurrence to marker
    }
}

/** A board task with a due date, surfaced as a calendar deadline (via expand=project,state). */
@Serializable
data class CalendarTaskDeadline(
    val id: String,
    val project: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("due_date") val dueDateRaw: String = "",
    val priority: String = "none",
    val expand: Expansion? = null,
) {
    @Serializable
    data class Expansion(
        val project: ProjectRef? = null,
        val state: StateRef? = null,
    ) {
        @Serializable data class ProjectRef(val name: String = "")
        @Serializable data class StateRef(val category: String = "")
    }

    /** The date portion (yyyy-MM-dd) used to bucket the deadline onto a day. */
    val dueDay: String get() = dueDateRaw.take(10)
    val projectName: String get() = expand?.project?.name ?: "Board"
    val taskPriority: TaskPriority get() = TaskPriority.fromRaw(priority)
    val completed: Boolean get() = expand?.state?.category == "completed"
}

/** Events + task deadlines + resolved timezone, the cached unit for the calendar. */
@Serializable
data class CalendarSnapshot(
    val events: List<CalendarEvent>,
    val tasks: List<CalendarTaskDeadline>,
    val timezone: String,
)

/** Fields submitted when creating or updating an event. */
data class CalendarEventInput(
    val title: String,
    val description: String,
    val startAt: Instant,
    val endAt: Instant,
    val allDay: Boolean,
    val timezone: String,
    val location: String,
    val color: EventColor,
    val recurrenceFrequency: EventRecurrenceFrequency,
    val recurrenceInterval: Int,
    val reminder: EventReminder,
)
