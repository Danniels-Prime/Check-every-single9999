package com.screentranslate.app.translation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GoogleTranslateApi(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    val isAvailable: Boolean get() = apiKey.isNotBlank()

    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (!isAvailable) throw IllegalStateException("No API key configured")

        val body = buildString {
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
            .post(body.toRequestBody("application/json".toMediaType()))
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
