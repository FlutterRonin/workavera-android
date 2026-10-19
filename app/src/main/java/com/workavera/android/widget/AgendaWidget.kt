package com.workavera.android.widget

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.workavera.android.R
import com.workavera.android.WorkaveraApplication

/** What the list shows, chosen per widget (iOS AgendaWidgetContent). */
enum class AgendaWidgetContentFilter(val raw: String, @param:StringRes val label: Int) {
    ALL("all", R.string.agenda_widget_content_all),
    TASKS("tasks", R.string.agenda_widget_content_tasks),
    EVENTS("events", R.string.agenda_widget_content_events);

    fun includes(item: AgendaWidgetItem): Boolean = when (this) {
        ALL -> true
        TASKS -> item.kind == AgendaWidgetItem.Kind.TASK
        EVENTS -> item.kind == AgendaWidgetItem.Kind.EVENT
    }

    companion object {
        fun fromRaw(raw: String?): AgendaWidgetContentFilter = entries.firstOrNull { it.raw == raw } ?: ALL
    }
}

/** How many upcoming items the widget lists. Eight uses a denser layout. */
enum class AgendaWidgetItemCount(val raw: String, val count: Int, @param:StringRes val label: Int) {
    SIX("six", 6, R.string.agenda_widget_items_six),
    EIGHT("eight", 8, R.string.agenda_widget_items_eight);

    companion object {
        fun fromRaw(raw: String?): AgendaWidgetItemCount = entries.firstOrNull { it.raw == raw } ?: SIX
    }
}

/** Per-widget options, stored in each widget's Glance state. */
data class AgendaWidgetOptions(
    val content: AgendaWidgetContentFilter = AgendaWidgetContentFilter.ALL,
    val itemCount: AgendaWidgetItemCount = AgendaWidgetItemCount.SIX,
) {
    companion object {
        val CONTENT_KEY = stringPreferencesKey("content")
        val ITEM_COUNT_KEY = stringPreferencesKey("item_count")

        fun from(preferences: Preferences) = AgendaWidgetOptions(
            content = AgendaWidgetContentFilter.fromRaw(preferences[CONTENT_KEY]),
            itemCount = AgendaWidgetItemCount.fromRaw(preferences[ITEM_COUNT_KEY]),
        )
    }
}

/**
 * The Agenda home screen widget: the next six or eight assigned task deadlines
 * and Calendar events, each marked with its date (iOS AgendaWidget).
 */
class AgendaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as WorkaveraApplication).container.agendaWidget
        // Periodic updates land here; the repository skips the network when it
        // fetched within the last minute.
        repository.refreshInBackground()
        provideContent {
            val state by repository.state.collectAsState()
            AgendaWidgetContent(state, AgendaWidgetOptions.from(currentState()))
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

/** Backs the widget's refresh button: fetches the latest agenda without opening the app. */
class RefreshAgendaAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        (context.applicationContext as WorkaveraApplication).container.agendaWidget.refresh(force = true)
    }
}
