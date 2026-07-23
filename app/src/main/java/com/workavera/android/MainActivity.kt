package com.workavera.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.workavera.android.ui.WorkaveraApp
import com.workavera.android.ui.theme.WorkaveraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WorkaveraTheme {
                WorkaveraApp()
            }
        }
    }
}
