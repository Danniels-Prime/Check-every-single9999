package com.screentranslate.app.ocr

import android.graphics.Rect

object TextBlockMapper {

    /**
     * Scales bounding boxes from bitmap coordinates to screen coordinates.
     * Also adjusts for status bar height since overlay uses FLAG_LAYOUT_IN_SCREEN.
     */
    fun scaleToScreen(
        blocks: List<TextBlockData>,
        bitmapWidth: Int,
        bitmapHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        statusBarHeight: Int
    ): List<TextBlockData> {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return blocks

        val ratioX = screenWidth.toFloat() / bitmapWidth
        val ratioY = screenHeight.toFloat() / bitmapHeight

        return blocks.map { block ->
            val orig = block.boundingBox
            val scaled = Rect(
                (orig.left * ratioX).toInt(),
                (orig.top * ratioY).toInt() + statusBarHeight,
                (orig.right * ratioX).toInt(),
                (orig.bottom * ratioY).toInt() + statusBarHeight
            )
            block.copy(boundingBox = scaled)
        }
    }
}
