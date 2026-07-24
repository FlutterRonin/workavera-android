package com.workavera.android.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.workavera.android.data.model.CalendarEvent
import com.workavera.android.data.model.CalendarEventInput
import com.workavera.android.data.model.CalendarTaskDeadline
import com.workavera.android.data.model.EventColor
import com.workavera.android.data.model.EventOccurrence
import com.workavera.android.data.model.EventRecurrenceFrequency
import com.workavera.android.data.model.EventReminder
import com.workavera.android.ui.settings.FormCard
import com.workavera.android.ui.settings.FormSectionTitle
import com.workavera.android.ui.settings.FormTextField
import com.workavera.android.ui.theme.parseHexColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
private val detailTimeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val mediumDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/** Event detail, mirroring the iOS EventDetailSheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventDetailSheet(
    occurrence: EventOccurrence,
    zone: ZoneId,
    isMutating: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val event = occurrence.event
    val color = parseHexColor(event.eventColor.hex, MaterialTheme.colorScheme.primary)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Toolbar row (iOS topBarTrailing): spinner + edit/delete grouped in a
            // capsule, so the title below gets the full width.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMutating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Row(
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onEdit, enabled = !isMutating) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit event",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onDelete, enabled = !isMutating) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete event",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DateRange,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = event.title.ifEmpty { "Untitled event" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(20.dp))
            DetailCard {
                DetailRow(Icons.Outlined.Schedule, "Time", timeText(occurrence, zone, event.allDay))
                DetailDivider()
                DetailRow(
                    Icons.Outlined.DateRange,
                    "Date",
                    occurrence.startAt.atZone(zone).format(mediumDateFormat),
                )
                if (event.location.isNotEmpty()) {
                    DetailDivider()
                    DetailRow(Icons.Outlined.Place, "Location", event.location)
                }
                if (event.frequency != EventRecurrenceFrequency.NONE) {
                    DetailDivider()
                    DetailRow(Icons.Outlined.Repeat, "Repeats", recurrenceText(event))
                }
                if (event.reminder != EventReminder.NONE) {
                    DetailDivider()
                    DetailRow(Icons.Outlined.Notifications, "Reminder", event.reminder.label)
                }
            }
            if (event.description.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Task deadline detail, mirroring the iOS TaskDeadlineSheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailSheet(task: CalendarTaskDeadline, onDismiss: () -> Unit) {
    val color = task.taskPriority.calendarColor
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (task.completed) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))
            DetailCard {
                DetailRow(Icons.Outlined.Layers, "Project", task.projectName)
                DetailDivider()
                DetailRow(Icons.Outlined.Flag, "Priority", task.taskPriority.label)
                DetailDivider()
                DetailRow(Icons.Outlined.Schedule, "Due Date", dueDateText(task))
                if (task.completed) {
                    DetailDivider()
                    DetailRow(Icons.Outlined.CheckCircle, "Status", "Completed")
                }
            }
            if (task.description.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Description", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Grouped card holding detail rows (iOS secondarySystemGroupedBackground card). */
@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

/** One icon + label + value line inside a DetailCard (iOS detailRow). */
@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 44.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private fun timeText(occurrence: EventOccurrence, zone: ZoneId, allDay: Boolean): String {
    if (allDay) return "All day"
    val start = occurrence.startAt.atZone(zone).format(detailTimeFormat)
    val end = occurrence.endAt.atZone(zone).format(detailTimeFormat)
    return "$start – $end"
}

private fun dueDateText(task: CalendarTaskDeadline): String =
    runCatching { LocalDate.parse(task.dueDay).format(mediumDateFormat) }.getOrDefault(task.dueDay)

/** Full-screen event create/edit editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventEditorDialog(
    event: CalendarEvent?,
    initialDate: LocalDate,
    timezone: String,
    onSave: (String?, CalendarEventInput, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember(timezone) { runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault()) }
    val defaultStart = remember { initialDate.atTime(9, 0).atZone(zone).toEpochSecond() }
    val defaultEnd = remember { initialDate.atTime(10, 0).atZone(zone).toEpochSecond() }

    var title by remember { mutableStateOf(event?.title ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var location by remember { mutableStateOf(event?.location ?: "") }
    var allDay by remember { mutableStateOf(event?.allDay ?: false) }
    var startEpoch by remember { mutableLongStateOf(event?.startAt?.epochSecond ?: defaultStart) }
    var endEpoch by remember { mutableLongStateOf(event?.endAt?.epochSecond ?: defaultEnd) }
    var color by remember { mutableStateOf(event?.eventColor ?: EventColor.BLUE) }
    var frequency by remember { mutableStateOf(event?.frequency ?: EventRecurrenceFrequency.NONE) }
    var interval by remember { mutableIntStateOf(event?.interval ?: 1) }
    var reminder by remember { mutableStateOf(event?.reminder ?: EventReminder.NONE) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun submit() {
        if (title.isBlank()) { error = "A title is required."; return }
        // iOS: all-day events span the whole selected day (midnight → midnight).
        val start: Long
        val end: Long
        if (allDay) {
            val day = Instant.ofEpochSecond(startEpoch).atZone(zone).toLocalDate()
            start = day.atStartOfDay(zone).toEpochSecond()
            end = day.plusDays(1).atStartOfDay(zone).toEpochSecond()
        } else {
            if (endEpoch <= startEpoch) { error = "The end time must be after the start time."; return }
            start = startEpoch
            end = endEpoch
        }
        val input = CalendarEventInput(
            title = title.trim(),
            description = description.trim(),
            startAt = Instant.ofEpochSecond(start),
            endAt = Instant.ofEpochSecond(end),
            allDay = allDay,
            timezone = timezone,
            location = location.trim(),
            color = color,
            recurrenceFrequency = frequency,
            recurrenceInterval = interval.coerceAtLeast(1),
            reminder = reminder,
        )
        isSaving = true
        error = null
        onSave(event?.id, input) { err ->
            isSaving = false
            if (err == null) onDismiss() else error = err
        }
    }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (event == null) "New Event" else "Edit Event") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, enabled = !isSaving) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            TextButton(onClick = { submit() }, enabled = title.isNotBlank() && !isSaving) {
                                Text(if (isSaving) "Saving…" else "Save")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (event != null && event.frequency != EventRecurrenceFrequency.NONE) {
                        FormCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Repeat,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = WarningOrange,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Changes apply to the entire repeating series.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarningOrange,
                                )
                            }
                        }
                    }

                    EditorGroup("Event") {
                        FormTextField(
                            value = title,
                            onValueChange = { if (it.length <= 240) title = it },
                            placeholder = "Title",
                            enabled = !isSaving,
                        )
                        EditorDivider()
                        EditorValueRow("Date", editorDateText(startEpoch, zone), enabled = !isSaving) {
                            pickDate(context, startEpoch, zone) { picked ->
                                val delta = picked - startEpoch
                                startEpoch = picked
                                endEpoch += delta
                            }
                        }
                        EditorDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("All day", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = allDay, onCheckedChange = { allDay = it }, enabled = !isSaving)
                        }
                        if (!allDay) {
                            EditorDivider()
                            EditorValueRow("Starts", editorTimeText(startEpoch, zone), enabled = !isSaving) {
                                pickTime(context, startEpoch, zone) { picked ->
                                    startEpoch = picked
                                    if (endEpoch <= startEpoch) endEpoch = startEpoch + 3600
                                }
                            }
                            EditorDivider()
                            EditorValueRow("Ends", editorTimeText(endEpoch, zone), enabled = !isSaving) {
                                pickTime(context, endEpoch, zone) { endEpoch = it }
                            }
                        }
                    }

                    EditorGroup("Schedule") {
                        EditorDropdownRow(
                            label = "Repeat",
                            value = frequency.label,
                            enabled = !isSaving,
                            options = EventRecurrenceFrequency.entries.map { it to it.label },
                            onSelect = { frequency = it },
                        )
                        if (frequency != EventRecurrenceFrequency.NONE) {
                            EditorDivider()
                            IntervalStepperRow(
                                interval = interval,
                                frequency = frequency,
                                enabled = !isSaving,
                                onChange = { interval = it },
                            )
                        }
                        EditorDivider()
                        EditorDropdownRow(
                            label = "Reminder",
                            value = reminder.label,
                            enabled = !isSaving,
                            options = EventReminder.entries.map { it to it.label },
                            onSelect = { reminder = it },
                        )
                    }

                    EditorGroup("Color") {
                        ColorPickerRow(selected = color, enabled = !isSaving, onSelect = { color = it })
                    }

                    EditorGroup("Details") {
                        FormTextField(
                            value = location,
                            onValueChange = { if (it.length <= 500) location = it },
                            placeholder = "Location",
                            enabled = !isSaving,
                        )
                        EditorDivider()
                        TextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = { Text("Description") },
                            minLines = 4,
                            enabled = !isSaving,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (error != null) {
                        Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** iOS .orange, used for the repeating-series warning. */
private val WarningOrange = Color(0xFFFF9500)

/** One section of the event editor: a title above a grouped card (iOS Form Section). */
@Composable
private fun EditorGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        FormSectionTitle(title)
        FormCard(content = content)
    }
}

@Composable
private fun EditorDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** Tappable label + value row inside an editor card (opens a picker). */
@Composable
private fun EditorValueRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Label + value row opening a dropdown, inside an editor card (iOS Picker .menu). */
@Composable
private fun <T> EditorDropdownRow(
    label: String,
    value: String,
    enabled: Boolean,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (item, itemLabel) ->
                DropdownMenuItem(
                    text = { Text(itemLabel) },
                    onClick = { expanded = false; onSelect(item) },
                )
            }
        }
    }
}

/** iOS Stepper row: "Every N units" with - / + buttons. */
@Composable
private fun IntervalStepperRow(
    interval: Int,
    frequency: EventRecurrenceFrequency,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val unit = when (frequency) {
        EventRecurrenceFrequency.DAILY -> "day"
        EventRecurrenceFrequency.WEEKLY -> "week"
        EventRecurrenceFrequency.MONTHLY -> "month"
        EventRecurrenceFrequency.YEARLY -> "year"
        EventRecurrenceFrequency.NONE -> "time"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Every $interval $unit${if (interval == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(interval - 1) }, enabled = enabled && interval > 1) {
            Icon(Icons.Outlined.Remove, contentDescription = "Decrease interval")
        }
        IconButton(onClick = { onChange(interval + 1) }, enabled = enabled && interval < 999) {
            Icon(Icons.Filled.Add, contentDescription = "Increase interval")
        }
    }
}

/** Color circles inside the Color card (iOS ring around the selected circle). */
@Composable
private fun ColorPickerRow(selected: EventColor, enabled: Boolean, onSelect: (EventColor) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        EventColor.entries.forEach { option ->
            val c = parseHexColor(option.hex, MaterialTheme.colorScheme.primary)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .then(
                        if (option == selected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(enabled = enabled) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(30.dp).background(c, CircleShape))
            }
        }
    }
}

private fun editorDateText(epochSecond: Long, zone: ZoneId): String =
    Instant.ofEpochSecond(epochSecond).atZone(zone).format(detailDateFormat)

private fun editorTimeText(epochSecond: Long, zone: ZoneId): String =
    Instant.ofEpochSecond(epochSecond).atZone(zone).format(detailTimeFormat)

/** Full-screen list of every custom event (iOS AllEventsListSheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllEventsSheet(
    events: List<CalendarEvent>,
    zone: ZoneId,
    onEventClick: (EventOccurrence) -> Unit,
    onNewEvent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(events) { events.sortedBy { it.startAt } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("All Events") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Done") }
                        },
                        actions = {
                            IconButton(onClick = onNewEvent) { Icon(Icons.Filled.Add, contentDescription = "New event") }
                        },
                    )
                },
            ) { padding ->
                if (sorted.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("No Custom Events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Events you create appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onNewEvent) { Text("New Event") }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Text(
                                text = "${sorted.size} ${if (sorted.size == 1) "event" else "events"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(sorted, key = { it.id }) { event ->
                            AllEventRow(event = event, zone = zone) {
                                onEventClick(EventOccurrence(event, event.startAt, event.endAt))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One row in the All Events list, mirroring the iOS EventRow. */
@Composable
private fun AllEventRow(event: CalendarEvent, zone: ZoneId, onClick: () -> Unit) {
    val color = parseHexColor(event.eventColor.hex, MaterialTheme.colorScheme.primary)
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
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = scheduleText(event, zone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.location.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    MetaLabel(Icons.Outlined.Place, event.location)
                }
            }
            if (event.frequency != EventRecurrenceFrequency.NONE) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Autorenew,
                    contentDescription = "Repeats",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** iOS EventRow.scheduleText: "EEE, MMM d, yyyy · All day" or "… · start – end". */
private fun scheduleText(event: CalendarEvent, zone: ZoneId): String {
    val start = event.startAt.atZone(zone)
    val date = start.format(detailDateFormat)
    return if (event.allDay) {
        "$date · All day"
    } else {
        val end = event.endAt.atZone(zone)
        "$date · ${start.format(detailTimeFormat)} – ${end.format(detailTimeFormat)}"
    }
}

/** iOS recurrenceText: the frequency label, or "Every N units" for intervals > 1. */
private fun recurrenceText(event: CalendarEvent): String {
    val n = event.interval
    if (n == 1) return event.frequency.label
    val unit = when (event.frequency) {
        EventRecurrenceFrequency.DAILY -> "day"
        EventRecurrenceFrequency.WEEKLY -> "week"
        EventRecurrenceFrequency.MONTHLY -> "month"
        EventRecurrenceFrequency.YEARLY -> "year"
        EventRecurrenceFrequency.NONE -> "time"
    }
    return "Every $n ${unit}s"
}
