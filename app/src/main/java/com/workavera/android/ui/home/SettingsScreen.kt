package com.workavera.android.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.workavera.android.data.AuthState
import com.workavera.android.ui.components.KeyValueRow

/** Settings screen: shows real account / server info for now; the remaining settings come later. */
@Composable
fun SettingsScreen(state: AuthState.LoggedIn) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        ) {
            SettingsCard(title = "Account") {
                KeyValueRow("Email", state.user.email)
                KeyValueRow("Name", state.user.name)
            }
            Spacer(Modifier.height(12.dp))
            SettingsCard(title = "Server") {
                KeyValueRow("Address", state.server)
            }
            Spacer(Modifier.height(12.dp))
            SettingsCard(title = "About") {
                KeyValueRow("App version", "0.1.1 (preview)")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Appearance, API keys, model configurations, and saved memories " +
                    "are coming in a later release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
