package com.workavera.android.widget

import com.workavera.android.data.model.CalendarEvent
import com.workavera.android.data.model.CalendarSnapshot
import com.workavera.android.data.model.CalendarTaskDeadline
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/** Mirrors the iOS AgendaWidgetSnapshotTests. */
class AgendaWidgetSnapshotTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val timezone = "Asia/Shanghai"

    /** 2026-10-19 10:00 in Shanghai. */
    private val now = Instant.parse("2026-10-19T02:00:00Z")

    private fun snapshot(events: String = "[]", tasks: String = "[]") = AgendaWidgetSnapshot.from(
        CalendarSnapshot(
            events = json.decodeFromString<List<CalendarEvent>>(events).map { it.copy(timezone = timezone) },
            tasks = json.decodeFromString<List<CalendarTaskDeadline>>(tasks),
            timezone = timezone,
        ),
        now = now,
    )

    @Test
    fun expandsRepeatingAndOneOffEventsIntoLocalDays() {
        val snapshot = snapshot(
            events = """
            [
              {
                "id": "monthly", "title": "Subscription", "start_at": "2026-10-06 16:00:00.000Z",
                "end_at": "2026-10-07 16:00:00.000Z", "all_day": true, "color": "green",
                "recurrence_frequency": "monthly", "recurrence_interval": 1
              },
              {
                "id": "once", "title": "Glucose test", "description": "\n  Fast beforehand\nBring ID",
                "location": "Clinic", "start_at": "2026-10-20 16:00:00.000Z",
                "end_at": "2026-10-21 16:00:00.000Z", "all_day": true, "color": "amber",
                "recurrence_frequency": "none"
              }
            ]
            """,
        )

        assertEquals(listOf("2026-10-21", "2026-11-07", "2026-12-07"), snapshot.items.map { it.firstDayKey })
        val once = snapshot.items.first { it.title == "Glucose test" }
        assertEquals(listOf("2026-10-21"), once.dayKeys)
        assertEquals("Fast beforehand", once.detail)
        assertEquals(AgendaWidgetItem.Kind.EVENT, once.kind)
        assertEquals("amber", once.tint)
        assertTrue(once.allDay)
    }

    @Test
    fun eventDetailFallsBackToLocationWithoutNotes() {
        val snapshot = snapshot(
            events = """
            [{
              "id": "meet", "title": "Review", "description": "", "location": "Room 3",
              "start_at": "2026-10-22 02:00:00.000Z", "end_at": "2026-10-22 03:00:00.000Z"
            }]
            """,
        )

        assertEquals("Room 3", snapshot.items.first().detail)
    }

    @Test
    fun keepsOnlyTasksDueFromTodayWithinTheLookahead() {
        val snapshot = snapshot(
            tasks = """
            [
              {"id": "lastMonth", "project": "p1", "title": "Old", "due_date": "2026-08-15 00:00:00.000Z",
               "expand": {"state": {"category": "active"}}},
              {"id": "earlier", "project": "p1", "title": "Late", "due_date": "2026-10-10 00:00:00.000Z",
               "priority": "high", "expand": {"project": {"name": "Ops"}, "state": {"category": "active"}}},
              {"id": "soon", "project": "p1", "title": "Soon", "due_date": "2026-10-22 00:00:00.000Z",
               "expand": {"project": {"name": "Ops"}, "state": {"category": "pending"}}},
              {"id": "far", "project": "p1", "title": "Far", "due_date": "2027-03-01 00:00:00.000Z",
               "expand": {"state": {"category": "pending"}}}
            ]
            """,
        )

        assertEquals(listOf("soon"), snapshot.items.map { it.id })
        assertEquals("Ops", snapshot.items.first().detail)
        assertEquals("none", snapshot.items.first().tint)
    }

    @Test
    fun ordersAllDayItemsBeforeTimedOnesOnTheSameDay() {
        val snapshot = snapshot(
            events = """
            [{
              "id": "meet", "title": "Review", "start_at": "2026-10-22 02:00:00.000Z",
              "end_at": "2026-10-22 03:00:00.000Z"
            }]
            """,
            tasks = """
            [{"id": "task", "project": "p1", "title": "Ship", "due_date": "2026-10-22 00:00:00.000Z",
              "expand": {"state": {"category": "active"}}}]
            """,
        )

        assertEquals(listOf("task", "meet:${Instant.parse("2026-10-22T02:00:00Z").epochSecond}"), snapshot.items.map { it.id })
    }

    @Test
    fun sameItemsIgnoresFetchTime() {
        val first = AgendaWidgetSnapshot(timezone, emptyList(), updatedAt = now.toEpochMilli())
        val second = AgendaWidgetSnapshot(timezone, emptyList(), updatedAt = now.toEpochMilli() + 60_000)
        assertTrue(first.hasSameItems(second))
        assertFalse(first.hasSameItems(null))
    }
}
