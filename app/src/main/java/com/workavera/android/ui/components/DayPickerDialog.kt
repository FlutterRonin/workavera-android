package com.workavera.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.YearMonth

/**
 * Date picker built on the app's own [MonthCalendar] instead of the platform
 * dialog (which ignores the theme) or the Material date picker (whose big
 * headline and input-mode toggle sit oddly next to our cards). Shared by the
 * board due date and the calendar event editor.
 *
 * [extraAction] hangs an extra button under the grid — the board uses it for
 * "Clear Due Date", mirroring the iOS due-date editor.
 */
@Composable
fun DayPickerDialog(
    title: String,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
    today: LocalDate = LocalDate.now(),
    extraAction: (@Composable () -> Unit)? = null,
) {
    var selected by remember(initialDate) { mutableStateOf(initialDate) }
    var month by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                MonthCalendar(
                    month = month,
                    selectedDate = selected,
                    today = today,
                    onMonthChange = { month = it },
                    onSelectDate = { selected = it },
                )
                extraAction?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onPicked(selected)
                    onDismiss()
                },
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
