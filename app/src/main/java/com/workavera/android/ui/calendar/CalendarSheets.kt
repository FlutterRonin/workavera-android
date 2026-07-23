package com.workavera.android.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.workavera.android.ui.theme.parseHexColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
private val detailTimeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = event.title.ifEmpty { "Untitled event" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (isMutating) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(16.dp))
            DetailLine("When", whenText(occurrence, zone, event.allDay))
            if (event.location.isNotEmpty()) DetailLine("Where", event.location)
            if (event.frequency != EventRecurrenceFrequency.NONE) {
                DetailLine("Repeats", recurrenceText(event))
            }
            if (event.reminder != EventReminder.NONE) DetailLine("Reminder", event.reminder.label)
            if (event.description.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Description", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(event.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onEdit, enabled = !isMutating) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }
                OutlinedButton(onClick = onDelete, enabled = !isMutating) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailSheet(task: CalendarTaskDeadline, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            DetailLine("Project", task.projectName)
            DetailLine("Due", task.dueDay)
            DetailLine("Priority", task.taskPriority.label)
            DetailLine("Status", if (task.completed) "Completed" else "Open")
            if (task.description.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(task.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** Full-screen event create/edit editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventEditorDialog(
    event: CalendarEvent?,
    initialDate: java.time.LocalDate,
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
    var interval by remember { mutableStateOf((event?.interval ?: 1).toString()) }
    var reminder by remember { mutableStateOf(event?.reminder ?: EventReminder.NONE) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun submit() {
        if (title.isBlank()) { error = "A title is required."; return }
        if (endEpoch <= startEpoch) { error = "The end time must be after the start time."; return }
        val input = CalendarEventInput(
            title = title.trim(),
            description = description.trim(),
            startAt = Instant.ofEpochSecond(startEpoch),
            endAt = Instant.ofEpochSecond(endEpoch),
            allDay = allDay,
            timezone = timezone,
            location = location.trim(),
            color = color,
            recurrenceFrequency = frequency,
            recurrenceInterval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 240) title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("All day", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = allDay, onCheckedChange = { allDay = it }, enabled = !isSaving)
                    }
                    DateTimeRow("Starts", startEpoch, zone, allDay, enabled = !isSaving) {
                        pickDateTime(context, startEpoch, zone) { picked ->
                            startEpoch = picked
                            if (endEpoch <= startEpoch) endEpoch = startEpoch + 3600
                        }
                    }
                    DateTimeRow("Ends", endEpoch, zone, allDay, enabled = !isSaving) {
                        pickDateTime(context, endEpoch, zone) { endEpoch = it }
                    }
                    ColorPickerRow(selected = color, enabled = !isSaving, onSelect = { color = it })
                    EnumDropdownRow(
                        label = "Repeats",
                        value = frequency.label,
                        enabled = !isSaving,
                        options = EventRecurrenceFrequency.entries.map { it to it.label },
                        onSelect = { frequency = it },
                    )
                    if (frequency != EventRecurrenceFrequency.NONE) {
                        OutlinedTextField(
                            value = interval,
                            onValueChange = { interval = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("Every N ${frequency.name.lowercase()} interval") },
                            singleLine = true,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    EnumDropdownRow(
                        label = "Reminder",
                        value = reminder.label,
                        enabled = !isSaving,
                        options = EventReminder.entries.map { it to it.label },
                        onSelect = { reminder = it },
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { if (it.length <= 500) location = it },
                        label = { Text("Location") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        minLines = 3,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error != null) {
                        Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    epochSecond: Long,
    zone: ZoneId,
    allDay: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val zdt = Instant.ofEpochSecond(epochSecond).atZone(zone)
    val text = if (allDay) zdt.format(detailDateFormat) else "${zdt.format(detailDateFormat)}  ${zdt.format(detailTimeFormat)}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ColorPickerRow(selected: EventColor, enabled: Boolean, onSelect: (EventColor) -> Unit) {
    Column {
        Text("Color", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EventColor.entries.forEach { option ->
                val c = parseHexColor(option.hex, MaterialTheme.colorScheme.primary)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(c, CircleShape)
                        .clickable(enabled = enabled) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (option == selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> EnumDropdownRow(
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
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.UnfoldMore, contentDescription = null, modifier = Modifier.size(18.dp))
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

private fun whenText(occurrence: EventOccurrence, zone: ZoneId, allDay: Boolean): String {
    val start = occurrence.startAt.atZone(zone)
    if (allDay) return start.format(detailDateFormat) + " · All day"
    val end = occurrence.endAt.atZone(zone)
    return "${start.format(detailDateFormat)}  ${start.format(detailTimeFormat)} – ${end.format(detailTimeFormat)}"
}

private fun recurrenceText(event: CalendarEvent): String {
    val n = event.interval
    val unit = when (event.frequency) {
        EventRecurrenceFrequency.DAILY -> "day"
        EventRecurrenceFrequency.WEEKLY -> "week"
        EventRecurrenceFrequency.MONTHLY -> "month"
        EventRecurrenceFrequency.YEARLY -> "year"
        EventRecurrenceFrequency.NONE -> "time"
    }
    return if (n == 1) "Every $unit" else "Every $n ${unit}s"
}
