package com.screentranslate.app.ocr

import android.graphics.Rect

data class OcrResult(
    val blocks: List<TextBlockData>,
    val fullText: String,
    val processingTimeMs: Long
)

data class TextBlockData(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float = 1.0f
)
