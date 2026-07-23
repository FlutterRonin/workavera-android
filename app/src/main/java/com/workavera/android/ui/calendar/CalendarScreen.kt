package com.workavera.android.ui.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.model.CalendarEvent
import com.workavera.android.data.model.CalendarTaskDeadline
import com.workavera.android.data.model.EventOccurrence
import com.workavera.android.data.model.EventRecurrenceFrequency
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.ExpressivePullToRefreshBox
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.theme.color
import com.workavera.android.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthHeaderFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val agendaDayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/**
 * Calendar screen, mirroring the iOS CalendarView: a month grid with per-day
 * event dots over a selected-day agenda that merges events (recurrence
 * expanded) with board-task deadlines. New/edit events via a full-screen editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    val viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory)
    val ui = viewModel.uiState
    val zone = ui.zone
    val today = remember(zone) { LocalDate.now(zone) }

    var displayedMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }
    var detailOccurrence by remember { mutableStateOf<EventOccurrence?>(null) }
    var detailTask by remember { mutableStateOf<CalendarTaskDeadline?>(null) }
    var editorFor by remember { mutableStateOf<EditorState?>(null) }
    var showAllEvents by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
        viewModel.listenForRealtimeSignals()
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showAllEvents = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("All events")
                            }
                        }
                        MonthCard(
                            month = displayedMonth,
                            selectedDate = selectedDate,
                            today = today,
                            zone = zone,
                            events = ui.events,
                            tasks = ui.tasks,
                            onPrev = { displayedMonth = displayedMonth.minusMonths(1) },
                            onNext = { displayedMonth = displayedMonth.plusMonths(1) },
                            onToday = { displayedMonth = YearMonth.from(today); selectedDate = today },
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

        FloatingActionButton(
            onClick = { editorFor = EditorState(event = null, date = selectedDate) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New event")
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
                viewModel.delete(occurrence.event.id)
                detailOccurrence = null
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

    if (showAllEvents) {
        AllEventsSheet(
            events = ui.events,
            zone = zone,
            onEventClick = {
                showAllEvents = false
                detailOccurrence = it
            },
            onNewEvent = {
                showAllEvents = false
                editorFor = EditorState(event = null, date = selectedDate)
            },
            onDismiss = { showAllEvents = false },
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
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = month.atDay(1).format(monthHeaderFormat),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (month != YearMonth.from(today)) {
                    TextButton(onClick = onToday) { Text("Today") }
                }
                IconButton(onClick = onPrev) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Next month")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels().forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(((monthCells(month).size / 7) * 48).dp),
                userScrollEnabled = false,
            ) {
                items(monthCells(month)) { day ->
                    if (day == null) {
                        Box(modifier = Modifier.height(48.dp))
                    } else {
                        DayCell(
                            day = day,
                            isSelected = day == selectedDate,
                            isToday = day == today,
                            dotColors = dayItemColors(day, zone, events, tasks),
                            onClick = { onSelect(day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    dotColors: List<Color>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            // No ripple: selection is shown by the circle, matching the iOS plain
            // button (avoids a square highlight filling the cell bounds).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The selected/today highlight wraps only the day number; the dots below
        // keep their own colors (matching the iOS day cell).
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    when {
                        isSelected -> Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.height(5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            dotColors.take(3).forEach { dot ->
                Box(modifier = Modifier.size(5.dp).background(dot, CircleShape))
            }
        }
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
    val occurrences = remember(date, zone, events) {
        events.flatMap { it.occurrencesOverlapping(date, zone) }.sortedBy { it.startAt }
    }
    val dayTasks = remember(date, tasks) {
        tasks.filter { it.dueDay == date.toString() }
    }
    Text(
        text = date.format(agendaDayFormat),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )
    if (occurrences.isEmpty() && dayTasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Nothing scheduled",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            occurrences.forEach { occurrence ->
                EventRow(occurrence = occurrence, zone = zone, onClick = { onEventClick(occurrence) })
            }
            dayTasks.forEach { task ->
                TaskRow(task = task, onClick = { onTaskClick(task) })
            }
        }
    }
}

@Composable
private fun EventRow(occurrence: EventOccurrence, zone: ZoneId, onClick: () -> Unit) {
    val event = occurrence.event
    val color = parseHexColor(event.eventColor.hex, MaterialTheme.colorScheme.primary)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(38.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title.ifEmpty { "Untitled event" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = timeRangeText(occurrence, zone, event.allDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (event.location.isNotEmpty()) {
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TaskRow(task: CalendarTaskDeadline, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Priority-colored bar, matching the day-cell task dots.
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(38.dp)
                .background(task.taskPriority.color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.description.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${task.projectName} · Task deadline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (task.completed) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- helpers ----

private fun weekdayLabels(): List<String> =
    listOf("S", "M", "T", "W", "T", "F", "S")

/** Grid cells for a month: leading blanks (Sunday-first) then each day. */
private fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value % 7 // Monday=1..Sunday=7 → Sunday-first offset
    val cells = ArrayList<LocalDate?>(leading + month.lengthOfMonth())
    repeat(leading) { cells.add(null) }
    for (day in 1..month.lengthOfMonth()) cells.add(month.atDay(day))
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

/**
 * One dot color per calendar item on [day], matching iOS: events use their own
 * color, task deadlines use their priority color (so high/urgent show orange/red,
 * medium shows blue, etc. — not a flat gray).
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
        .map { it.taskPriority.color }
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

// System date + time pickers merged into an epoch-second selection.
internal fun pickDateTime(
    context: Context,
    initialEpochSecond: Long,
    zone: ZoneId,
    onPicked: (Long) -> Unit,
) {
    val zdt = java.time.Instant.ofEpochSecond(initialEpochSecond).atZone(zone)
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val picked = java.time.ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, zone)
                    onPicked(picked.toEpochSecond())
                },
                zdt.hour, zdt.minute, true,
            ).show()
        },
        zdt.year, zdt.monthValue - 1, zdt.dayOfMonth,
    ).show()
}
