package com.workavera.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workavera.android.data.model.AppAppearance
import com.workavera.android.ui.AppSection
import com.workavera.android.ui.WorkaveraApp
import com.workavera.android.ui.theme.WorkaveraTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** A section asked for from outside the app, e.g. by tapping the Agenda widget. */
    private val requestedSection = MutableStateFlow<AppSection?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity has already handled its launch intent.
        if (savedInstanceState == null) handleDeepLink(intent)
        val settings = (application as WorkaveraApplication).container.settings
        setContent {
            // The appearance preference drives the theme app-wide; SYSTEM follows
            // the device dark-mode setting.
            val appearance by settings.appearance
                .collectAsStateWithLifecycle(initialValue = AppAppearance.SYSTEM)
            WorkaveraTheme(darkTheme = appearance.isDark(isSystemInDarkTheme())) {
                WorkaveraApp(requestedSection = requestedSection)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onStop() {
        super.onStop()
        // Let the Agenda widget pick up anything changed while the app was open.
        (application as WorkaveraApplication).container.agendaWidget.refreshInBackground()
    }

    private fun handleDeepLink(intent: Intent?) {
        sectionFrom(intent?.data)?.let { requestedSection.value = it }
    }

    companion object {
        private const val SCHEME = "workavera"

        /** Opens the app on [section] (`workavera://<section>`), reusing the running activity. */
        fun sectionIntent(context: Context, section: AppSection): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("$SCHEME://${section.route}"))
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )

        private fun sectionFrom(uri: Uri?): AppSection? =
            uri?.takeIf { it.scheme == SCHEME }?.let { AppSection.fromRoute(it.host) }
    }
}
