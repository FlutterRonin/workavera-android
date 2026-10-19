package com.workavera.android.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.workavera.android.R
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.model.AppAppearance
import com.workavera.android.ui.theme.WorkaveraTheme
import kotlinx.coroutines.launch

/**
 * Chooses what an Agenda widget lists (iOS AgendaWidgetConfigurationIntent):
 * tasks and events, tasks only, or events only, and six or eight items.
 * Opens when the widget is placed (before Android 12) and when it is
 * reconfigured from the home screen.
 */
class AgendaWidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Backing out cancels placing the widget.
        setResult(RESULT_CANCELED, resultValue)

        // Only configure this app's own widgets.
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        if (provider?.packageName != packageName) {
            finish()
            return
        }

        val settings = (application as WorkaveraApplication).container.settings
        val glanceManager = GlanceAppWidgetManager(this)
        lifecycleScope.launch {
            val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
            val initial = AgendaWidgetOptions.from(
                getAppWidgetState(this@AgendaWidgetConfigureActivity, PreferencesGlanceStateDefinition, glanceId),
            )
            setContent {
                val appearance by settings.appearance
                    .collectAsStateWithLifecycle(initialValue = AppAppearance.SYSTEM)
                WorkaveraTheme(darkTheme = appearance.isDark(isSystemInDarkTheme())) {
                    ConfigureScreen(initial) { options ->
                        lifecycleScope.launch {
                            updateAppWidgetState(this@AgendaWidgetConfigureActivity, glanceId) {
                                it[AgendaWidgetOptions.CONTENT_KEY] = options.content.raw
                                it[AgendaWidgetOptions.ITEM_COUNT_KEY] = options.itemCount.raw
                            }
                            AgendaWidget().update(this@AgendaWidgetConfigureActivity, glanceId)
                            setResult(RESULT_OK, resultValue)
                            finish()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureScreen(initial: AgendaWidgetOptions, onDone: (AgendaWidgetOptions) -> Unit) {
    var content by remember { mutableStateOf(initial.content) }
    var itemCount by remember { mutableStateOf(initial.itemCount) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.agenda_widget_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.agenda_widget_configure_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OptionGroup(
                title = stringResource(R.string.agenda_widget_show),
                options = AgendaWidgetContentFilter.entries,
                selected = content,
                label = { stringResource(it.label) },
                onSelect = { content = it },
            )
            OptionGroup(
                title = stringResource(R.string.agenda_widget_items),
                options = AgendaWidgetItemCount.entries,
                selected = itemCount,
                label = { stringResource(it.label) },
                onSelect = { itemCount = it },
            )
            Button(
                onClick = { onDone(AgendaWidgetOptions(content, itemCount)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.agenda_widget_done))
            }
        }
    }
}

@Composable
private fun <T> OptionGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 16.dp).selectableGroup()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(label(option), modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
