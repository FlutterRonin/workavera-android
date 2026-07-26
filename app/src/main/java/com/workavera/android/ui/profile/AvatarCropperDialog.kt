package com.workavera.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mr0xf00.easycrop.AspectRatio
import com.mr0xf00.easycrop.CropState
import com.mr0xf00.easycrop.CropperStyle
import com.mr0xf00.easycrop.ui.ImageCropperDialog
import com.workavera.android.ui.theme.Ink

/**
 * Shared easycrop dialog for avatar cropping: square-locked crop region and
 * a monochrome top bar (the library default is a Material 2 purple
 * TopAppBar). The bottom transform controls are the library default.
 */
@Composable
fun AvatarCropperDialog(state: CropState) {
    ImageCropperDialog(
        state = state,
        style = CropperStyle(aspects = listOf(AspectRatio(1, 1))),
        topBar = { cropState ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ink)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { cropState.done(false) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Cancel",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { cropState.reset() }) {
                    Icon(
                        Icons.Outlined.Restore,
                        contentDescription = "Reset",
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = { cropState.done(true) },
                    enabled = !cropState.accepted,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Use photo",
                        tint = Color.White,
                    )
                }
            }
        },
    )
}
