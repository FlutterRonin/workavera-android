package com.workavera.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthHeaderFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

/**
 * The app's own month grid (iOS CalendarView parity): month header with
 * prev / next and a Today shortcut, Sunday-first weekday labels, and day cells
 * where the selection is a filled circle and today a ring — no Material
 * DatePicker chrome. Shared by the Calendar screen and the board due-date
 * picker so both months look identical.
 *
 * [dayDots] supplies up to three dots under a day (calendar item colors); it
 * defaults to none for plain date picking.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    dayDots: (LocalDate) -> List<Color> = { emptyList() },
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = month.atDay(1).format(monthHeaderFormat),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (month != YearMonth.from(today)) {
                TextButton(
                    onClick = {
                        onMonthChange(YearMonth.from(today))
                        onSelectDate(today)
                    },
                ) {
                    Text("Today")
                }
            }
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month")
            }
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
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
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val cells = monthCells(month)
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(((cells.size / 7) * 48).dp),
            userScrollEnabled = false,
        ) {
            items(cells) { day ->
                if (day == null) {
                    Box(modifier = Modifier.height(48.dp))
                } else {
                    DayCell(
                        day = day,
                        isSelected = day == selectedDate,
                        isToday = day == today,
                        dotColors = dayDots(day),
                        onClick = { onSelectDate(day) },
                    )
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
