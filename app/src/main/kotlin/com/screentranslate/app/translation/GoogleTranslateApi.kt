package com.screentranslate.app.translation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleTranslateApi(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    val isAvailable: Boolean get() = apiKey.isNotBlank()

    // Free endpoint — no API key, no quota, works on any internet connection.
    // Response format: [[["translated","original",...],...],...,"detectedLang"]
    suspend fun translateFree(
        text: String,
        targetLanguage: String,
        sourceLanguage: String? = null
    ): String = withContext(Dispatchers.IO) {
        val sl = when {
            sourceLanguage.isNullOrBlank() || sourceLanguage == "und" -> "auto"
            else -> sourceLanguage
        }
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=$sl&tl=$targetLanguage&dt=t&q=$encoded"

        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()
            ?: throw Exception("Empty response from free translate endpoint")

        if (!response.isSuccessful) {
            throw Exception("Free translate HTTP ${response.code}")
        }

        val root = json.parseToJsonElement(body).jsonArray
        // root[0] = array of segments, each segment[0] = translated chunk
        root[0].jsonArray
            .mapNotNull { segment ->
                segment.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull
            }
            .filter { it.isNotBlank() }
            .joinToString("")
            .ifBlank { throw Exception("Empty translation result") }
    }

    // Official Cloud Translate API — requires paid API key (optional).
    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (!isAvailable) throw IllegalStateException("No API key configured")

        val bodyStr = buildString {
            append("{\"q\":\"")
            append(text.replace("\"", "\\\"").replace("\n", "\\n"))
            append("\",\"target\":\"")
            append(targetLanguage)
            append("\"")
            if (sourceLanguage != null) {
                append(",\"source\":\"")
                append(sourceLanguage)
                append("\"")
            }
            append(",\"format\":\"text\"}")
        }

        val request = Request.Builder()
            .url("https://translation.googleapis.com/language/translate/v2?key=$apiKey")
            .post(bodyStr.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from Translate API")

        if (!response.isSuccessful) {
            Log.e(TAG, "Translate API error ${response.code}: $responseBody")
            throw Exception("Translate API error: ${response.code}")
        }

        try {
            val jsonElement = json.parseToJsonElement(responseBody)
            jsonElement.jsonObject["data"]
                ?.jsonObject?.get("translations")
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("translatedText")
                ?.jsonPrimitive?.content
                ?: throw Exception("Unexpected response format")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Translate API response", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "GoogleTranslateApi"
    }
}
