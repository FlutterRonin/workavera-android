package com.workavera.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/*
 * The app's two menu flavors, both wearing the group-card chrome — plain
 * surface, hairline border, 14dp corners, no tonal tint — instead of the
 * default Material tonal popup, which reads as a foreign gray slab against the
 * monochrome brand:
 *
 *  - [ActionMenu]: the "⋮" overflow on a row, holding icon + label commands.
 *  - [SelectMenu]: a picker anchored to a form row, holding options with a
 *    check mark on the current one.
 */

private val MenuItemPadding = PaddingValues(horizontal = 14.dp)

/** Shared popup chrome for [ActionMenu] and [SelectMenu]. */
@Composable
private fun MenuContainer(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    offset: DpOffset,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 180.dp),
        offset = offset,
        shape = RoundedCornerShape(14.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** Row-action menu, holding [ActionMenuItem]s split by [ActionMenuDivider]. */
@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    MenuContainer(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        content = content,
    )
}

/**
 * One [ActionMenu] command: 18dp leading icon plus label, with the icon in the
 * secondary ink so the label leads; [destructive] paints both in the error
 * color for delete-style actions.
 */
@Composable
fun ActionMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val error = MaterialTheme.colorScheme.error
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = if (destructive) error else MaterialTheme.colorScheme.onSurface,
            leadingIconColor = if (destructive) error else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = MenuItemPadding,
    )
}

/** Hairline separator setting a destructive action apart from the safe ones. */
@Composable
fun ActionMenuDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * Option picker anchored to a select row ("label · value · ⌄"), holding
 * [SelectMenuItem]s. Pass a width in [modifier] to make it match its trigger.
 */
@Composable
fun SelectMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    MenuContainer(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        content = content,
    )
}

/**
 * One [SelectMenu] option. The current one is tinted and check-marked so every
 * picker shows its selection the same way; [icon] or [dotColor] carry the
 * option's own glyph (category icon, status / priority dot), and
 * [supportingText] the second line a few pickers need.
 */
@Composable
fun SelectMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dotColor: Color? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        onClick = onClick,
        modifier = modifier.background(
            if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        ),
        leadingIcon = when {
            icon != null -> {
                { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }
            dotColor != null -> {
                { SelectDot(dotColor) }
            }
            else -> null
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = MenuItemPadding,
    )
}

/** The colored dot pickers use in place of an icon (status, priority, board state). */
@Composable
private fun SelectDot(color: Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .background(color, CircleShape),
    )
}
