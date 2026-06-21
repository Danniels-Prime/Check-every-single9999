package com.screentranslate.app.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTranslator {

    private val cache = LinkedHashMap<Pair<String, String>, Translator>(4, 0.75f, true)
    private val maxCached = 3

    private fun getOrCreateTranslator(sourceLang: String, targetLang: String): Translator {
        val key = sourceLang to targetLang
        cache[key]?.let { return it }

        if (cache.size >= maxCached) {
            val oldest = cache.keys.first()
            cache.remove(oldest)?.close()
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)
        cache[key] = translator
        return translator
    }

    suspend fun ensureModelDownloaded(sourceLang: String, targetLang: String): Boolean {
        return try {
            val translator = getOrCreateTranslator(sourceLang, targetLang)
            val conditions = DownloadConditions.Builder().build()
            suspendCancellableCoroutine { cont ->
                translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Model download failed for $sourceLang→$targetLang", e)
            false
        }
    }

    suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        val mlSource = toMlKitCode(sourceLang)
        val mlTarget = toMlKitCode(targetLang)

        if (mlSource == null || mlTarget == null) {
            throw UnsupportedOperationException("Unsupported language pair: $sourceLang → $targetLang")
        }
        if (mlSource == mlTarget) return text

        val translator = getOrCreateTranslator(mlSource, mlTarget)

        // Download model if needed — if this fails, the translate() call below will also
        // fail and the caller's catch block will trigger the online fallback.
        val downloaded = ensureModelDownloaded(mlSource, mlTarget)
        if (!downloaded) throw Exception("ML Kit model not available for $mlSource→$mlTarget")

        return suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    private fun toMlKitCode(bcp47: String): String? {
        return when (bcp47.lowercase()) {
            "en" -> TranslateLanguage.ENGLISH
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "it" -> TranslateLanguage.ITALIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "ru" -> TranslateLanguage.RUSSIAN
            "zh", "zh-hans" -> TranslateLanguage.CHINESE
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "ar" -> TranslateLanguage.ARABIC
            "hi" -> TranslateLanguage.HINDI
            "tr" -> TranslateLanguage.TURKISH
            "pl" -> TranslateLanguage.POLISH
            "nl" -> TranslateLanguage.DUTCH
            "sv" -> TranslateLanguage.SWEDISH
            "no" -> TranslateLanguage.NORWEGIAN
            "da" -> TranslateLanguage.DANISH
            "fi" -> TranslateLanguage.FINNISH
            "uk" -> TranslateLanguage.UKRAINIAN
            "vi" -> TranslateLanguage.VIETNAMESE
            "th" -> TranslateLanguage.THAI
            "id" -> TranslateLanguage.INDONESIAN
            else -> null
        }
    }

    fun release() {
        cache.values.forEach { it.close() }
        cache.clear()
    }

    companion object {
        private const val TAG = "MlKitTranslator"
    }
}
