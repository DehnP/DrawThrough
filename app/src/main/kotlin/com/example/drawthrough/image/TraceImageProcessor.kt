package com.example.drawthrough.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TraceImageProcessor {

    private const val MAX_DIMENSION = 1600
    private const val OUTLINE_MAX_DIMENSION = 1024

    suspend fun decodeBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_DIMENSION,
    ): Bitmap = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val largest = max(width, height)
                if (largest > maxDimension) {
                    val ratio = maxDimension.toFloat() / largest.toFloat()
                    decoder.setTargetSize(
                        max(1, (width * ratio).toInt()),
                        max(1, (height * ratio).toInt()),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            var sampleSize = 1
            val largest = max(bounds.outWidth, bounds.outHeight)
            while (largest / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: error("Unable to decode selected image")
        }
    }

    /**
     * Converts [source] into crisp dark-ink-on-warm-paper tracing lines using a Sobel edge detector.
     * [strength] ranges from 0f (fewer/bolder edges) to 1f (more fine detail).
     */
    suspend fun createOutlineBitmap(
        source: Bitmap,
        strength: Float,
    ): Bitmap = withContext(Dispatchers.Default) {
        val working = downscaleIfNeeded(source, OUTLINE_MAX_DIMENSION)
        val width = working.width
        val height = working.height
        val pixels = IntArray(width * height)
        working.getPixels(pixels, 0, width, 0, 0, width, height)

        // Precompute grayscale luminance
        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 77 + g * 150 + b * 29) shr 8
        }

        // Map strength (0..1) into contrast gain for crisp, traceable lines
        val clamped = strength.coerceIn(0.1f, 1f)
        val gain = 0.6f + clamped * 2.4f
        val noiseFloor = ((1f - clamped) * 42f + 12f).toInt()

        val out = IntArray(width * height) // 0 = fully transparent so live camera shows through

        for (y in 1 until height - 1) {
            val rowAbove = (y - 1) * width
            val row = y * width
            val rowBelow = (y + 1) * width
            for (x in 1 until width - 1) {
                val tl = gray[rowAbove + x - 1]
                val tc = gray[rowAbove + x]
                val tr = gray[rowAbove + x + 1]
                val ml = gray[row + x - 1]
                val mr = gray[row + x + 1]
                val bl = gray[rowBelow + x - 1]
                val bc = gray[rowBelow + x]
                val br = gray[rowBelow + x + 1]

                val gx = -tl + tr - 2 * ml + 2 * mr - bl + br
                val gy = -tl - 2 * tc - tr + bl + 2 * bc + br
                val mag = (abs(gx) + abs(gy)) shr 2

                if (mag > noiseFloor) {
                    val alpha = min(255, ((mag - noiseFloor) * gain).toInt())
                    // Deep charcoal ink (#181715) with edge-proportional alpha
                    out[row + x] = (alpha shl 24) or 0x00181715
                }
            }
        }

        if (working !== source) {
            working.recycle()
        }

        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(out, 0, width, 0, 0, width, height)
        }
    }

    private fun downscaleIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            max(1, (source.width * ratio).toInt()),
            max(1, (source.height * ratio).toInt()),
            true,
        )
    }
}
