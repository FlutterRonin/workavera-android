package com.workavera.android.widget

import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.TimeZone
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.workavera.android.MainActivity
import com.workavera.android.R
import com.workavera.android.data.model.EventColor
import com.workavera.android.data.model.TaskPriority
import com.workavera.android.ui.AppSection
import com.workavera.android.ui.theme.color
import com.workavera.android.ui.theme.parseHexColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** Monochrome brand surfaces with iOS system accents, following the device's dark mode. */
private object WidgetColors {
    val background = DayNightColor(day = Color.White, night = Color(0xFF1C1C1E))
    val primary = DayNightColor(day = Color(0xFF1A1A1A), night = Color(0xFFEBEBEB))
    val secondary = DayNightColor(day = Color(0xFF6E6E73), night = Color(0xFF98989F))
    val tertiary = DayNightColor(day = Color(0xFFAEAEB2), night = Color(0xFF636366))
    val divider = DayNightColor(day = Color(0xFFE5E5EA), night = Color(0xFF38383A))
    val today = DayNightColor(day = Color(0xFFFF3B30), night = Color(0xFFFF453A))
    val chat = DayNightColor(day = Color(0xFFAF52DE), night = Color(0xFFBF5AF2))
}

/** Everything the widget shows for one render, derived from the snapshot. */
private class AgendaPresentation(
    state: AgendaWidgetState,
    options: AgendaWidgetOptions,
    now: Instant,
    private val locale: Locale,
    is24Hour: Boolean,
) {
    class Row(
        val item: AgendaWidgetItem,
        /** The day the row is listed under (today for events already in progress). */
        val dayKey: String,
        val date: LocalDate,
    )

    val zone: ZoneId = state.snapshot?.zone ?: ZoneId.systemDefault()
    val today: LocalDate = now.atZone(zone).toLocalDate()
    val updatedAt: Long? = state.snapshot?.updatedAt
    /** Eight rows need a tighter layout than six to fit. */
    val isDense = options.itemCount == AgendaWidgetItemCount.EIGHT
    private val timeSkeleton = if (is24Hour) "Hmm" else "hmm"

    val rows: List<Row> = buildList {
        val todayKey = today.toString()
        // Snapshot items are already in day order; take the first upcoming ones.
        for (item in state.snapshot?.items.orEmpty()) {
            if (!options.content.includes(item)) continue
            val dayKey = item.dayKeys.firstOrNull { it >= todayKey } ?: continue
            val date = runCatching { LocalDate.parse(dayKey) }.getOrNull() ?: continue
            add(Row(item, dayKey, date))
            if (size == options.itemCount.count) break
        }
    }

    fun isToday(row: Row): Boolean = row.date == today

    fun format(epochMillis: Long, skeleton: String): String {
        val formatter = DateFormat.getInstanceForSkeleton(skeleton, locale)
        formatter.timeZone = TimeZone.getTimeZone(zone.id)
        return formatter.format(Date(epochMillis))
    }

    fun format(date: LocalDate, skeleton: String): String =
        format(date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), skeleton)

    fun time(epochMillis: Long): String = format(epochMillis, timeSkeleton)

    /** "Today" / "Tomorrow" in the user's language, otherwise e.g. "Wed, 10/21". */
    fun dateLabel(row: Row): String {
        val direction = when (ChronoUnit.DAYS.between(today, row.date)) {
            0L -> RelativeDateTimeFormatter.Direction.THIS
            1L -> RelativeDateTimeFormatter.Direction.NEXT
            else -> return format(row.date, "MdEEE")
        }
        return RelativeDateTimeFormatter.getInstance(
            ULocale.forLocale(locale),
            null,
            RelativeDateTimeFormatter.Style.LONG,
            DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE,
        ).format(direction, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
    }

    /** Start time for a timed event on its first day; null otherwise. */
    fun startTime(row: Row): String? {
        val item = row.item
        if (item.kind != AgendaWidgetItem.Kind.EVENT || item.allDay || row.dayKey != item.firstDayKey) return null
        return item.startAt?.let(::time)
    }

    fun tint(item: AgendaWidgetItem): Color = when (item.kind) {
        AgendaWidgetItem.Kind.EVENT -> parseHexColor(EventColor.fromRaw(item.tint).hex, Color(0xFF3B82F6))
        AgendaWidgetItem.Kind.TASK -> TaskPriority.fromRaw(item.tint).color
    }
}

@Composable
fun AgendaWidgetContent(state: AgendaWidgetState, options: AgendaWidgetOptions) {
    val context = LocalContext.current
    val size = LocalSize.current
    val presentation = AgendaPresentation(
        state = state,
        options = options,
        now = Instant.now(),
        locale = Locale.getDefault(),
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    val isLarge = size.height >= LARGE_MIN_HEIGHT
    val dense = presentation.isDense
    val horizontalPadding = 16.dp
    val verticalPadding = if (isLarge || !dense) 14.dp else 10.dp
    val headerGap = if (dense) (if (isLarge) 10.dp else 5.dp) else (if (isLarge) 12.dp else 7.dp)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.agenda_widget_background))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .clickable(openSection(context, AppSection.CALENDAR)),
    ) {
        AgendaHeader(presentation, state, isLarge)
        Spacer(GlanceModifier.height(headerGap))
        when {
            state.status == AgendaWidgetState.Status.SIGNED_OUT ->
                Message(context.getString(R.string.agenda_widget_signed_out))
            state.status == AgendaWidgetState.Status.UNAVAILABLE ->
                Message(context.getString(R.string.agenda_widget_unavailable))
            state.snapshot == null ->
                Message(context.getString(R.string.agenda_widget_loading))
            presentation.rows.isEmpty() ->
                Message(context.getString(R.string.agenda_widget_empty))
            else -> {
                // Show only the rows that fit the widget's current size.
                val available = size.height - verticalPadding * 2 - headerHeight(isLarge) - headerGap
                if (isLarge) {
                    val rowHeight = if (dense) 37.dp else 46.dp
                    val fit = ((available + 1.dp) / (rowHeight + 1.dp)).toInt().coerceAtLeast(1)
                    LargeAgendaList(presentation, presentation.rows.take(fit), rowHeight)
                } else {
                    val rowHeight = if (dense) 16.dp else 18.dp
                    val spacing = if (dense) 2.dp else 3.dp
                    val fit = ((available + spacing) / (rowHeight + spacing)).toInt().coerceAtLeast(1)
                    CompactAgendaList(presentation, presentation.rows.take(fit), rowHeight, spacing)
                }
            }
        }
    }
}

@Composable
private fun AgendaHeader(presentation: AgendaPresentation, state: AgendaWidgetState, isLarge: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(headerHeight(isLarge)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Workavera",
            style = TextStyle(
                color = WidgetColors.primary,
                fontSize = if (isLarge) 16.sp else 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = presentation.format(presentation.today, "MMMdEEE"),
            style = TextStyle(color = WidgetColors.today, fontSize = if (isLarge) 14.sp else 12.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        presentation.updatedAt?.let { updatedAt ->
            Text(
                text = presentation.time(updatedAt),
                style = TextStyle(color = WidgetColors.tertiary, fontSize = 9.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        HeaderButton(
            icon = R.drawable.ic_widget_sparkles,
            background = R.drawable.agenda_widget_button_chat,
            tint = WidgetColors.chat,
            contentDescription = context.getString(R.string.agenda_widget_open_chat),
            onClick = openSection(context, AppSection.CHAT),
            isLarge = isLarge,
        )
        Spacer(GlanceModifier.width(6.dp))
        HeaderButton(
            icon = R.drawable.ic_widget_refresh,
            background = R.drawable.agenda_widget_button_neutral,
            // Dimmed while a refresh is running.
            tint = if (state.isRefreshing) WidgetColors.tertiary else WidgetColors.secondary,
            contentDescription = context.getString(R.string.agenda_widget_refresh),
            onClick = actionRunCallback<RefreshAgendaAction>(),
            isLarge = isLarge,
        )
    }
}

/** A round, comfortably sized tap target for the header actions. */
@Composable
private fun HeaderButton(
    icon: Int,
    background: Int,
    tint: ColorProvider,
    contentDescription: String,
    onClick: Action,
    isLarge: Boolean,
) {
    val size = if (isLarge) 34.dp else 30.dp
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(ImageProvider(background))
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(if (isLarge) 17.dp else 15.dp),
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(text = text, style = TextStyle(color = WidgetColors.secondary, fontSize = 12.sp))
}

/** Medium: one line per item — date, color bar, title, start time. */
@Composable
private fun CompactAgendaList(
    presentation: AgendaPresentation,
    rows: List<AgendaPresentation.Row>,
    rowHeight: Dp,
    spacing: Dp,
) {
    val dense = presentation.isDense
    val metaSize = if (dense) 10.5.sp else 11.sp
    // Glance caps a Column at ten children, so rows carry their own spacing
    // instead of separate spacers.
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = if (index > 0) spacing else 0.dp)
                    .height(rowHeight + if (index > 0) spacing else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = presentation.dateLabel(row),
                    style = TextStyle(
                        color = if (presentation.isToday(row)) WidgetColors.today else WidgetColors.secondary,
                        fontSize = metaSize,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.width(74.dp),
                )
                ColorBar(presentation.tint(row.item), height = if (dense) 11.dp else 12.dp)
                Spacer(GlanceModifier.width(6.dp))
                TitleText(row.item, fontSize = if (dense) 12.sp else 13.sp, modifier = GlanceModifier.defaultWeight())
                presentation.startTime(row)?.let { time ->
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = time,
                        style = TextStyle(color = WidgetColors.secondary, fontSize = metaSize),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Large: a date badge and two lines per item. */
@Composable
private fun LargeAgendaList(
    presentation: AgendaPresentation,
    rows: List<AgendaPresentation.Row>,
    rowHeight: Dp,
) {
    val context = LocalContext.current
    val dense = presentation.isDense
    // Glance caps a Column at ten children, so each item wraps its divider and row.
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                if (index > 0) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Spacer(GlanceModifier.width(46.dp))
                        Box(GlanceModifier.defaultWeight().height(1.dp).background(WidgetColors.divider)) {}
                    }
                }
                val badgeColor = if (presentation.isToday(row)) WidgetColors.today else WidgetColors.primary
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(rowHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = GlanceModifier.width(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = row.date.dayOfMonth.toString(),
                            style = TextStyle(
                                color = badgeColor,
                                fontSize = if (dense) 18.sp else 20.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = presentation.format(row.date, "EEE"),
                            style = TextStyle(
                                color = if (presentation.isToday(row)) WidgetColors.today else WidgetColors.secondary,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    ColorBar(presentation.tint(row.item), height = if (dense) 26.dp else 30.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        TitleText(row.item, fontSize = if (dense) 13.sp else 15.sp, medium = true)
                        Spacer(GlanceModifier.height(if (dense) 1.dp else 2.dp))
                        Text(
                            text = subtitle(presentation, row, context),
                            style = TextStyle(color = WidgetColors.secondary, fontSize = if (dense) 11.sp else 12.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun subtitle(
    presentation: AgendaPresentation,
    row: AgendaPresentation.Row,
    context: android.content.Context,
): String {
    val item = row.item
    val parts = mutableListOf<String>()
    when (item.kind) {
        AgendaWidgetItem.Kind.TASK -> parts += context.getString(R.string.agenda_widget_due)
        AgendaWidgetItem.Kind.EVENT ->
            parts += presentation.startTime(row) ?: context.getString(R.string.agenda_widget_all_day)
    }
    if (item.detail.isNotEmpty()) parts += item.detail
    return parts.joinToString(" · ")
}

@Composable
private fun ColorBar(color: Color, height: Dp) {
    Box(
        modifier = GlanceModifier
            .width(3.dp)
            .height(height)
            .cornerRadius(1.5.dp)
            .background(color),
    ) {}
}

/** The item title, with a to-do circle in front of tasks. */
@Composable
private fun TitleText(
    item: AgendaWidgetItem,
    fontSize: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    medium: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (item.kind == AgendaWidgetItem.Kind.TASK) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_task),
                contentDescription = null,
                colorFilter = ColorFilter.tint(WidgetColors.secondary),
                modifier = GlanceModifier.size(9.dp),
            )
            Spacer(GlanceModifier.width(3.dp))
        }
        Text(
            text = item.title,
            style = TextStyle(
                color = WidgetColors.primary,
                fontSize = fontSize,
                fontWeight = if (medium) FontWeight.Medium else FontWeight.Normal,
            ),
            maxLines = 1,
        )
    }
}

private fun openSection(context: android.content.Context, section: AppSection): Action =
    actionStartActivity(MainActivity.sectionIntent(context, section))

/** Fits the round header buttons. */
private fun headerHeight(isLarge: Boolean): Dp = if (isLarge) 34.dp else 30.dp

/** Heights from about a 4×3 widget up get the two-line layout with date badges. */
private val LARGE_MIN_HEIGHT = 250.dp
