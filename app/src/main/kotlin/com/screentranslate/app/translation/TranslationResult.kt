package com.screentranslate.app.translation

import android.graphics.Rect

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val boundingBox: Rect
)
