package com.workavera.android.ui.calendar

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.model.CalendarEvent
import com.workavera.android.data.model.CalendarTaskDeadline
import com.workavera.android.data.model.EventOccurrence
import com.workavera.android.data.model.EventRecurrenceFrequency
import com.workavera.android.data.model.TaskPriority
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.ExpressivePullToRefreshBox
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.components.MonthCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val agendaDayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/**
 * Priority tint used across the calendar (day dots, row bars, badges), matching
 * the iOS TaskPriority.calendarColor: none/low → blue, medium/high → amber,
 * urgent → red.
 */
internal val TaskPriority.calendarColor: Color
    get() = when (this) {
        TaskPriority.NONE, TaskPriority.LOW -> Color(0xFF3B82F6)
        TaskPriority.MEDIUM, TaskPriority.HIGH -> Color(0xFFF59E0B)
        TaskPriority.URGENT -> Color(0xFFEF4444)
    }

/** One agenda entry for a day: an event occurrence or a task deadline (iOS CalendarItem). */
private sealed interface AgendaItem {
    val title: String
    val itemDescription: String
    val tint: Color
    val isAllDay: Boolean
    val sortInstant: Instant
    val isCompletedTask: Boolean

    data class Event(val occurrence: EventOccurrence) : AgendaItem {
        override val title get() = occurrence.event.title
        override val itemDescription get() = occurrence.event.description
        override val tint get() = parseHexColorOrDefault(occurrence.event.eventColor.hex)
        override val isAllDay get() = occurrence.event.allDay
        override val sortInstant get() = occurrence.startAt
        override val isCompletedTask get() = false
    }

    data class Task(val task: CalendarTaskDeadline) : AgendaItem {
        override val title get() = task.title
        override val itemDescription get() = task.description
        override val tint get() = task.taskPriority.calendarColor
        override val isAllDay get() = true
        override val sortInstant get() = Instant.MIN
        override val isCompletedTask get() = task.completed
    }
}

/**
 * Triggers for the calendar actions that live in the home top bar (the iOS
 * toolbar's list.bullet / plus buttons). The buttons are rendered by
 * HomeScaffold; the sheets they open stay here.
 */
class CalendarTopBarState {
    var showAllEvents by mutableStateOf(false)
    var newEventRequests by mutableStateOf(0)
        private set

    fun requestNewEvent() {
        newEventRequests++
    }
}

/**
 * Calendar screen, mirroring the iOS CalendarView: a month grid with per-day
 * event dots over a selected-day agenda that merges events (recurrence
 * expanded) with board-task deadlines. New/edit events via a full-screen editor.
 */
@Composable
fun CalendarScreen(topBarState: CalendarTopBarState = remember { CalendarTopBarState() }) {
    val viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory)
    val ui = viewModel.uiState
    val zone = ui.zone
    val today = remember(zone) { LocalDate.now(zone) }

    var displayedMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }
    var detailOccurrence by remember { mutableStateOf<EventOccurrence?>(null) }
    var detailTask by remember { mutableStateOf<CalendarTaskDeadline?>(null) }
    var editorFor by remember { mutableStateOf<EditorState?>(null) }
    var deletingEvent by remember { mutableStateOf<CalendarEvent?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
        viewModel.listenForRealtimeSignals()
    }

    // New-event button in the home top bar: each request opens the editor.
    LaunchedEffect(topBarState.newEventRequests) {
        if (topBarState.newEventRequests > 0) {
            editorFor = EditorState(event = null, date = selectedDate)
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = ui.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!ui.hasLoaded && ui.isLoading) {
            LoadingIndicator("Loading calendar…")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    if (ui.error != null) {
                        ErrorBanner(message = ui.error, onRetry = viewModel::refresh)
                        Spacer(Modifier.height(16.dp))
                    }
                    MonthCard(
                        month = displayedMonth,
                        selectedDate = selectedDate,
                        today = today,
                        zone = zone,
                        events = ui.events,
                        tasks = ui.tasks,
                        onMonthChange = { displayedMonth = it },
                        onSelect = { selectedDate = it },
                    )
                    Spacer(Modifier.height(20.dp))
                    AgendaSection(
                        date = selectedDate,
                        zone = zone,
                        events = ui.events,
                        tasks = ui.tasks,
                        onEventClick = { detailOccurrence = it },
                        onTaskClick = { detailTask = it },
                    )
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    detailOccurrence?.let { occurrence ->
        EventDetailSheet(
            occurrence = occurrence,
            zone = zone,
            isMutating = occurrence.event.id in ui.mutatingIds,
            onEdit = {
                editorFor = EditorState(event = occurrence.event, date = selectedDate)
                detailOccurrence = null
            },
            onDelete = {
                detailOccurrence = null
                deletingEvent = occurrence.event
            },
            onDismiss = { detailOccurrence = null },
        )
    }

    detailTask?.let { task ->
        TaskDetailSheet(task = task, onDismiss = { detailTask = null })
    }

    editorFor?.let { state ->
        EventEditorDialog(
            event = state.event,
            initialDate = state.date,
            timezone = ui.timezone,
            onSave = viewModel::save,
            onDismiss = { editorFor = null },
        )
    }

    if (topBarState.showAllEvents) {
        AllEventsSheet(
            events = ui.events,
            zone = zone,
            onEventClick = {
                topBarState.showAllEvents = false
                detailOccurrence = it
            },
            onNewEvent = {
                topBarState.showAllEvents = false
                editorFor = EditorState(event = null, date = selectedDate)
            },
            onDismiss = { topBarState.showAllEvents = false },
        )
    }

    // Delete confirmation, matching the iOS alert (recurring events warn about the series).
    deletingEvent?.let { event ->
        val repeating = event.frequency != EventRecurrenceFrequency.NONE
        AlertDialog(
            onDismissRequest = { deletingEvent = null },
            title = { Text(if (repeating) "Delete repeating event?" else "Delete event?") },
            text = {
                Text(
                    if (repeating) "This permanently removes the entire repeating series."
                    else "This event will be permanently removed.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(event.id)
                    deletingEvent = null
                }) {
                    Text("Delete Event", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEvent = null }) { Text("Cancel") }
            },
        )
    }
}

private data class EditorState(val event: CalendarEvent?, val date: LocalDate)

@Composable
private fun MonthCard(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    events: List<CalendarEvent>,
    tasks: List<CalendarTaskDeadline>,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        MonthCalendar(
            month = month,
            selectedDate = selectedDate,
            today = today,
            onMonthChange = onMonthChange,
            onSelectDate = onSelect,
            modifier = Modifier.padding(16.dp),
            dayDots = { day -> dayItemColors(day, zone, events, tasks) },
        )
    }
}

@Composable
private fun AgendaSection(
    date: LocalDate,
    zone: ZoneId,
    events: List<CalendarEvent>,
    tasks: List<CalendarTaskDeadline>,
    onEventClick: (EventOccurrence) -> Unit,
    onTaskClick: (CalendarTaskDeadline) -> Unit,
) {
    // iOS items(on:): events (recurrence expanded) + the day's task deadlines,
    // all-day first, then by start time (tasks sort before timed events).
    val items = remember(date, zone, events, tasks) {
        val eventItems = events
            .flatMap { it.occurrencesOverlapping(date, zone) }
            .map(AgendaItem::Event)
        val dayKey = date.toString()
        val taskItems = tasks.filter { it.dueDay == dayKey }.map(AgendaItem::Task)
        (eventItems + taskItems).sortedWith(compareBy({ !it.isAllDay }, { it.sortInstant }))
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date.format(agendaDayFormat),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (items.isNotEmpty()) {
            Text(
                text = "${items.size} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No events or due tasks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                AgendaItemRow(
                    item = item,
                    zone = zone,
                    onClick = {
                        when (item) {
                            is AgendaItem.Event -> onEventClick(item.occurrence)
                            is AgendaItem.Task -> onTaskClick(item.task)
                        }
                    },
                )
            }
        }
    }
}

/** A single agenda card, mirroring the iOS itemRow layout. */
@Composable
private fun AgendaItemRow(item: AgendaItem, zone: ZoneId, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tint bar spanning the full content height (iOS RoundedRectangle width 4).
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(item.tint, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (item.isCompletedTask) 0.65f else 1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = when (item) {
                            is AgendaItem.Event -> Icons.Outlined.DateRange
                            is AgendaItem.Task -> Icons.Outlined.CheckBox
                        },
                        contentDescription = null,
                        tint = item.tint,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = item.title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item is AgendaItem.Task && item.task.taskPriority != TaskPriority.NONE) {
                        PriorityBadge(item.task.taskPriority)
                    }
                }
                Spacer(Modifier.height(4.dp))
                MetaLine(item, zone)
                if (item.itemDescription.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.itemDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Priority capsule shown next to a task title (iOS priorityBadge). */
@Composable
private fun PriorityBadge(priority: TaskPriority) {
    val color = priority.calendarColor
    Text(
        text = priority.label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Second line of an agenda card (iOS metaLine). */
@Composable
private fun MetaLine(item: AgendaItem, zone: ZoneId) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item) {
            is AgendaItem.Event -> {
                MetaLabel(
                    Icons.Outlined.Schedule,
                    timeRangeText(item.occurrence, zone, item.occurrence.event.allDay),
                )
                if (item.occurrence.event.location.isNotEmpty()) {
                    MetaLabel(Icons.Outlined.Place, item.occurrence.event.location)
                }
            }
            is AgendaItem.Task -> {
                MetaLabel(Icons.Outlined.Layers, item.task.projectName)
                if (item.task.completed) {
                    MetaLabel(Icons.Outlined.CheckCircle, "Completed")
                } else {
                    MetaLabel(Icons.Outlined.Schedule, "Due")
                }
            }
        }
    }
}

/** Small icon + caption pair used in agenda and list rows. */
@Composable
internal fun MetaLabel(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- helpers ----

/**
 * One dot color per calendar item on [day], matching iOS: events use their own
 * color, task deadlines use their calendar priority color.
 */
private fun dayItemColors(
    day: LocalDate,
    zone: ZoneId,
    events: List<CalendarEvent>,
    tasks: List<CalendarTaskDeadline>,
): List<Color> {
    val dayKey = day.toString()
    val eventColors = events
        .flatMap { it.occurrencesOverlapping(day, zone) }
        .map { parseHexColorOrDefault(it.event.eventColor.hex) }
    val taskColors = tasks
        .filter { it.dueDay == dayKey }
        .map { it.taskPriority.calendarColor }
    return eventColors + taskColors
}

private fun parseHexColorOrDefault(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

private fun timeRangeText(occurrence: EventOccurrence, zone: ZoneId, allDay: Boolean): String {
    if (allDay) return "All day"
    val start = occurrence.startAt.atZone(zone).format(timeFormat)
    val end = occurrence.endAt.atZone(zone).format(timeFormat)
    return "$start – $end"
}

// System time picker merged into an epoch-second selection, preserving the date.
internal fun pickTime(
    context: Context,
    initialEpochSecond: Long,
    zone: ZoneId,
    onPicked: (Long) -> Unit,
) {
    val zdt = Instant.ofEpochSecond(initialEpochSecond).atZone(zone)
    TimePickerDialog(
        context,
        { _, hour, minute ->
            val picked = java.time.ZonedDateTime.of(
                zdt.year, zdt.monthValue, zdt.dayOfMonth, hour, minute, 0, 0, zone,
            )
            onPicked(picked.toEpochSecond())
        },
        zdt.hour, zdt.minute, true,
    ).show()
}
