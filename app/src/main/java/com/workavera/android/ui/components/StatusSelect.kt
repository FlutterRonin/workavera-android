package com.workavera.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.workavera.android.data.model.UserStatus
import com.workavera.android.ui.theme.color

/**
 * Status picker: a single row showing the current status (dot + label + arrow)
 * that opens a dropdown as wide as the trigger row, with the selection
 * highlighted and checked — matching the `.menu` Picker of the iOS ProfileView.
 * Shared by the profile Status card and the edit-profile dialog.
 *
 * When [updating] is true, clicks are disabled and a spinner shows at the end
 * of the row (for immediate status submissions); when [enabled] is false the
 * whole row is disabled and dimmed (for form staging saved with the whole form).
 */
@Composable
fun StatusSelect(
    current: UserStatus,
    updating: Boolean,
    onSelect: (UserStatus) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    // Track the trigger row's width so the dropdown matches it, reading as a
    // real select box rather than a small popup.
    var fieldWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { fieldWidth = with(density) { it.width.toDp() } }
                .alpha(if (enabled) 1f else 0.38f)
                .clickable(enabled = enabled && !updating) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(current)
            Spacer(Modifier.width(10.dp))
            Text(
                text = current.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (updating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Change status",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 4.dp),
            modifier = if (fieldWidth > 0.dp) Modifier.width(fieldWidth) else Modifier,
        ) {
            UserStatus.entries.forEach { status ->
                val selected = status == current
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(status)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = status.label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(status)
                    },
                    modifier = Modifier.background(
                        if (selected) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color.Transparent
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatusDot(status: UserStatus) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .background(status.color, CircleShape),
    )
}
