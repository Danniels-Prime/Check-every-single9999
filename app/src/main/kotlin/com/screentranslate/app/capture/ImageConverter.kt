package com.screentranslate.app.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image

object ImageConverter {

    fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop to actual width if there's row padding
        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
                bitmap.recycle()
            }
        } else {
            bitmap
        }
    }

    fun scaleBitmapIfNeeded(bitmap: Bitmap, maxWidth: Int = 1080, maxHeight: Int = 1920): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap

        val ratioX = maxWidth.toFloat() / bitmap.width
        val ratioY = maxHeight.toFloat() / bitmap.height
        val ratio = minOf(ratioX, ratioY)

        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }
}
