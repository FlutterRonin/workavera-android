package com.workavera.android.widget

import com.workavera.android.data.model.CalendarSnapshot
import com.workavera.android.data.model.EventOccurrence
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable

/**
 * A flattened, widget-ready view of Calendar events and Board task deadlines
 * (iOS AgendaWidgetSnapshot).
 *
 * Repeating events are expanded into occurrences ahead of time so the widget
 * only has to filter by day when it renders. The snapshot covers today plus
 * [LOOKAHEAD_DAYS], which is plenty for the handful of upcoming items shown.
 */
@Serializable
data class AgendaWidgetSnapshot(
    val timezone: String,
    val items: List<AgendaWidgetItem>,
    /** Epoch milliseconds of the fetch. */
    val updatedAt: Long,
) {
    val zone: ZoneId get() = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())

    /** Same agenda content, ignoring when it was fetched. */
    fun hasSameItems(other: AgendaWidgetSnapshot?): Boolean =
        other != null && timezone == other.timezone && items == other.items

    companion object {
        const val LOOKAHEAD_DAYS = 60L

        fun from(calendar: CalendarSnapshot, now: Instant = Instant.now()): AgendaWidgetSnapshot {
            val zone = runCatching { ZoneId.of(calendar.timezone) }.getOrDefault(ZoneId.systemDefault())
            val today = now.atZone(zone).toLocalDate()
            val windowEnd = today.plusDays(LOOKAHEAD_DAYS)
            val todayKey = today.toString()
            val windowEndKey = windowEnd.toString()

            val occurrences = LinkedHashMap<String, EventOccurrence>()
            val occurrenceDays = HashMap<String, MutableList<String>>()
            var day = today
            while (day.isBefore(windowEnd)) {
                val key = day.toString()
                for (event in calendar.events) {
                    for (occurrence in event.occurrencesOverlapping(day, zone)) {
                        occurrences[occurrence.id] = occurrence
                        occurrenceDays.getOrPut(occurrence.id) { mutableListOf() }.add(key)
                    }
                }
                day = day.plusDays(1)
            }

            val eventItems = occurrences.values.map { occurrence ->
                AgendaWidgetItem(
                    id = occurrence.id,
                    kind = AgendaWidgetItem.Kind.EVENT,
                    title = occurrence.event.title,
                    detail = eventDetail(occurrence.event.description, occurrence.event.location),
                    dayKeys = occurrenceDays[occurrence.id].orEmpty(),
                    startAt = occurrence.startAt.toEpochMilli(),
                    endAt = occurrence.endAt.toEpochMilli(),
                    allDay = occurrence.event.allDay,
                    tint = occurrence.event.color,
                )
            }
            val taskItems = calendar.tasks
                .filter { !it.completed && it.dueDay >= todayKey && it.dueDay < windowEndKey }
                .map { task ->
                    AgendaWidgetItem(
                        id = task.id,
                        kind = AgendaWidgetItem.Kind.TASK,
                        title = task.title,
                        detail = task.projectName,
                        dayKeys = listOf(task.dueDay),
                        startAt = null,
                        endAt = null,
                        allDay = true,
                        tint = task.priority,
                    )
                }

            return AgendaWidgetSnapshot(
                timezone = zone.id,
                items = (eventItems + taskItems).sortedWith(itemOrder),
                updatedAt = now.toEpochMilli(),
            )
        }

        /** The first line of the notes, or the location when there are no notes. */
        private fun eventDetail(description: String, location: String): String =
            description.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?: location.trim()

        /** Orders items by day, then all-day entries, then start time. */
        private val itemOrder = compareBy<AgendaWidgetItem> { it.firstDayKey }
            .thenBy { !it.allDay }
            .thenBy { it.startAt ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    }
}

@Serializable
data class AgendaWidgetItem(
    val id: String,
    val kind: Kind,
    val title: String,
    /**
     * Project name for tasks; for events, the first line of the notes, or the
     * location when there are no notes.
     */
    val detail: String,
    /** Every `yyyy-MM-dd` day (in the snapshot time zone) the item covers. */
    val dayKeys: List<String>,
    /** Epoch milliseconds; null for tasks. */
    val startAt: Long?,
    val endAt: Long?,
    val allDay: Boolean,
    /** [com.workavera.android.data.model.EventColor] raw value for events, [com.workavera.android.data.model.TaskPriority] raw value for tasks. */
    val tint: String,
) {
    @Serializable
    enum class Kind { EVENT, TASK }

    val firstDayKey: String get() = dayKeys.firstOrNull().orEmpty()
}
