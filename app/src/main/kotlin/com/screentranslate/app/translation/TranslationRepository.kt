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
        val src = sourceLang.takeIf { it != "und" }

        // 1. ML Kit — offline, fast, requires downloaded model
        if (src != null) {  // ML Kit needs a known source language
            try {
                return mlKit.translate(text, src, targetLang)
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit failed, falling back to free API: ${e.message}")
            }
        }

        // 2. Free unofficial Google endpoint — online, no key, auto-detects language
        try {
            return googleApi.translateFree(text, targetLang, src)
        } catch (e: Exception) {
            Log.w(TAG, "Free translate API failed: ${e.message}")
        }

        // 3. Official Google Cloud API — online, requires paid API key
        if (googleApi.isAvailable) {
            try {
                return googleApi.translate(text, targetLang, src)
            } catch (e: Exception) {
                Log.e(TAG, "All translation methods failed", e)
            }
        }

        return null
    }

    companion object {
        private const val TAG = "TranslationRepository"
    }
}
