package com.workavera.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.workavera.android.data.model.UserStatus
import com.workavera.android.ui.theme.color

/**
 * Circular avatar: loads the URL via Coil, falling back to the name's initial
 * while loading / on failure / when no URL is set, so it never shows a blank or
 * black circle. When [status] is non-null a presence dot is overlaid at the
 * bottom-right.
 *
 * Workavera's preset avatars are DiceBear-generated SVGs, which require Coil's
 * SvgDecoder (see the ImageLoader configuration in WorkaveraApplication).
 */
@Composable
fun AvatarView(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    status: UserStatus? = null,
) {
    Box(modifier = modifier.size(size)) {
        val initials = @Composable {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (url.isNullOrEmpty()) {
            initials()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = "$name avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = { initials() },
                error = { initials() },
            )
        }

        if (status != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.32f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(size * 0.22f)
                        .clip(CircleShape)
                        .background(status.color),
                )
            }
        }
    }
}
