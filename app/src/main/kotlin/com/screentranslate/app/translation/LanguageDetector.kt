package com.screentranslate.app.translation

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LanguageDetector {

    private val detector by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.34f)
                .build()
        )
    }

    suspend fun detect(text: String): String {
        if (text.isBlank()) return "und"
        return try {
            suspendCancellableCoroutine { cont ->
                detector.identifyLanguage(text)
                    .addOnSuccessListener { lang -> cont.resume(lang) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Language detection failed", e)
            "und"
        }
    }

    fun close() = detector.close()

    companion object {
        private const val TAG = "LanguageDetector"
    }
}
