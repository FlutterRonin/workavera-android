package com.workavera.android.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Avatar image processing: decoding (with sampling to avoid OOM on large
 * images) is separate from compression. Compression matches the iOS
 * preparedJPEG — longest edge scaled to 1024 or less, JPEG quality stepping
 * down from 90 to 30 until the size fits the server's 500 KB limit.
 */
object AvatarImageProcessor {

    const val MAX_DIMENSION = 1024
    const val MAX_BYTES = 500 * 1024

    /** Decodes and downsamples to a longest edge of roughly [targetMax]; returns null on read failure. */
    fun decode(context: Context, uri: Uri, targetMax: Int = 2048): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val half = max(bounds.outWidth, bounds.outHeight) / 2
        while (half / sampleSize >= targetMax) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    /** Scales the longest edge to ≤1024, then compresses down the quality ladder to ≤500KB. */
    fun compressAvatar(bitmap: Bitmap): ByteArray {
        val resized = scaleDown(bitmap)
        for (quality in 90 downTo 30 step 10) {
            val data = resized.toJpeg(quality)
            if (data.size <= MAX_BYTES) return data
        }
        return resized.toJpeg(20)
    }

    /** Crops a size×size square starting at (left, top) and compresses it. */
    fun cropAndCompress(
        source: Bitmap,
        left: Int,
        top: Int,
        size: Int,
    ): ByteArray {
        val cropped = Bitmap.createBitmap(source, left, top, size, size)
        return compressAvatar(cropped)
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
