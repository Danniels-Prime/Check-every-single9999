package com.screentranslate.app.translation

import android.util.Log
import com.screentranslate.app.ocr.TextBlockData

class TranslationRepository(
    private val mlKit: MlKitTranslator,
    private val googleApi: GoogleTranslateApi,
    private val detector: LanguageDetector
) {

    suspend fun translateBlocks(
        blocks: List<TextBlockData>,
        targetLanguage: String
    ): List<TranslationResult> {
        if (blocks.isEmpty()) return emptyList()

        // Detect source language from combined text for efficiency
        val combinedText = blocks.joinToString("\n") { it.text }
        val sourceLang = detector.detect(combinedText)

        if (sourceLang == "und") {
            Log.w(TAG, "Could not detect source language")
        }

        return blocks.mapNotNull { block ->
            translateBlock(block, sourceLang, targetLanguage)
        }
    }

    private suspend fun translateBlock(
        block: TextBlockData,
        sourceLang: String,
        targetLang: String
    ): TranslationResult? {
        val text = block.text
        if (text.isBlank()) return null

        // Skip if source matches target
        if (sourceLang != "und" && sourceLang.startsWith(targetLang.take(2), ignoreCase = true)) {
            return TranslationResult(
                originalText = text,
                translatedText = text,
                sourceLang = sourceLang,
                targetLang = targetLang,
                boundingBox = block.boundingBox
            )
        }

        val translated = tryTranslate(text, sourceLang, targetLang)
            ?: return TranslationResult(
                originalText = text,
                translatedText = "[Translation failed]",
                sourceLang = sourceLang,
                targetLang = targetLang,
                boundingBox = block.boundingBox
            )

        return TranslationResult(
            originalText = text,
            translatedText = translated,
            sourceLang = sourceLang,
            targetLang = targetLang,
            boundingBox = block.boundingBox
        )
    }

    private suspend fun tryTranslate(text: String, sourceLang: String, targetLang: String): String? {
        // Try ML Kit first
        return try {
            val src = if (sourceLang == "und") "auto" else sourceLang
            mlKit.translate(text, src, targetLang)
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit translation failed, trying Google API", e)
            // Fallback to Google Translate API
            if (googleApi.isAvailable) {
                try {
                    googleApi.translate(text, targetLang, sourceLang.takeIf { it != "und" })
                } catch (e2: Exception) {
                    Log.e(TAG, "Google API translation also failed", e2)
                    null
                }
            } else {
                null
            }
        }
    }

    companion object {
        private const val TAG = "TranslationRepository"
    }
}
