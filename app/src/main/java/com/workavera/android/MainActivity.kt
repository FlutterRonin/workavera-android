package com.workavera.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workavera.android.data.model.AppAppearance
import com.workavera.android.ui.WorkaveraApp
import com.workavera.android.ui.theme.WorkaveraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = (application as WorkaveraApplication).container.settings
        setContent {
            // The appearance preference drives the theme app-wide; SYSTEM follows
            // the device dark-mode setting.
            val appearance by settings.appearance
                .collectAsStateWithLifecycle(initialValue = AppAppearance.SYSTEM)
            WorkaveraTheme(darkTheme = appearance.isDark(isSystemInDarkTheme())) {
                WorkaveraApp()
            }
        }
    }
}
