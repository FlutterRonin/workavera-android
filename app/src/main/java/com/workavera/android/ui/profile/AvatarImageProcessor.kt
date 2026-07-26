package com.workavera.android.ui.profile

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Avatar compression, matching the iOS preparedJPEG: longest edge scaled to
 * 1024 or less, JPEG quality stepping down from 90 to 30 until the size fits
 * the server's 500 KB limit. Cropping itself is handled by the easycrop
 * ImageCropper dialog.
 */
object AvatarImageProcessor {

    const val MAX_DIMENSION = 1024
    const val MAX_BYTES = 500 * 1024

    /** Scales the longest edge to ≤1024, then compresses down the quality ladder to ≤500KB. */
    fun compressAvatar(bitmap: Bitmap): ByteArray {
        val resized = scaleDown(bitmap)
        for (quality in 90 downTo 30 step 10) {
            val data = resized.toJpeg(quality)
            if (data.size <= MAX_BYTES) return data
        }
        return resized.toJpeg(20)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val scale = min(1f, MAX_DIMENSION.toFloat() / longest)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
