package com.workavera.android.ui.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Avatar crop dialog: pinch to zoom and drag to reframe inside a square
 * viewport; saving crops a square from the current framing and compresses it.
 * Mirrors the iOS "Move and Scale" step before changing the avatar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarCropDialog(
    uri: Uri,
    onConfirm: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            AvatarImageProcessor.decode(context, uri)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Move and Scale") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = Color.White,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                    ),
                )

                val source = bitmap
                if (source == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    CropViewport(
                        source = source,
                        onCancel = onDismiss,
                        onConfirm = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CropViewport(
    source: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewportPx = with(density) {
            minOf(maxWidth, maxHeight).toPx()
        }
        val viewportDp = with(density) { viewportPx.toDp() }

        // Initial scale: fill the square viewport.
        val baseScale = max(
            viewportPx / source.width,
            viewportPx / source.height,
        )
        var userScale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        fun clampOffset(scale: Float, candidate: Offset): Offset {
            val displayW = source.width * baseScale * scale
            val displayH = source.height * baseScale * scale
            val maxX = max(0f, (displayW - viewportPx) / 2f)
            val maxY = max(0f, (displayH - viewportPx) / 2f)
            return Offset(
                candidate.x.coerceIn(-maxX, maxX),
                candidate.y.coerceIn(-maxY, maxY),
            )
        }

        val displayWidthDp = with(density) {
            (source.width * baseScale * userScale).toDp()
        }
        val displayHeightDp = with(density) {
            (source.height * baseScale * userScale).toDp()
        }

        Box(
            modifier = Modifier
                .size(viewportDp)
                .pointerInput(source) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (userScale * zoom).coerceIn(1f, 5f)
                        userScale = newScale
                        offset = clampOffset(newScale, offset + pan)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val imageBitmap = remember(source) { source.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = "Crop preview",
                modifier = Modifier
                    .size(displayWidthDp, displayHeightDp)
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) },
                contentScale = ContentScale.FillBounds,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color.White)
            }
            TextButton(
                onClick = {
                    val scale = baseScale * userScale
                    val cropSize = viewportPx / scale
                    val centerX = source.width / 2f - offset.x / scale
                    val centerY = source.height / 2f - offset.y / scale
                    val left = (centerX - cropSize / 2f)
                        .roundToInt()
                        .coerceIn(0, (source.width - cropSize).roundToInt().coerceAtLeast(0))
                    val top = (centerY - cropSize / 2f)
                        .roundToInt()
                        .coerceIn(0, (source.height - cropSize).roundToInt().coerceAtLeast(0))
                    val size = cropSize.roundToInt()
                        .coerceAtMost(source.width - left)
                        .coerceAtMost(source.height - top)
                    if (size > 0) {
                        onConfirm(
                            AvatarImageProcessor.cropAndCompress(source, left, top, size),
                        )
                    }
                },
            ) {
                Text("Use Photo", color = Color.White)
            }
        }
    }
}
