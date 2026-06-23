package com.screentranslate.app.ai

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
import java.util.concurrent.TimeUnit

class OpenAiExplainer(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : AiExplainer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun explain(original: String, translated: String, targetLang: String): AiResult =
        withContext(Dispatchers.IO) {
            val prompt = buildPrompt(original, translated, targetLang)
            val bodyStr = """{"model":"$model","messages":[{"role":"user","content":${escapeJson(prompt)}}],"max_tokens":200}"""

            val request = Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyStr.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty AI response")
            if (!response.isSuccessful) throw Exception("AI API error ${response.code}")

            val content = json.parseToJsonElement(body)
                .jsonObject["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNull
                ?: throw Exception("Unexpected AI response format")

            parseAiResponse(content)
        }

    private fun buildPrompt(original: String, translated: String, targetLang: String): String =
        "The phrase \"$original\" translates to \"$translated\" in $targetLang.\n" +
        "Reply with exactly this format (no extra text):\n" +
        "DEFINITION: [one sentence plain-English definition]\n" +
        "1. [example sentence using \"$translated\"]\n" +
        "2. [example sentence using \"$translated\"]"

    private fun parseAiResponse(content: String): AiResult {
        val lines = content.trim().lines().map { it.trim() }.filter { it.isNotBlank() }
        val definition = lines.firstOrNull { it.startsWith("DEFINITION:") }
            ?.removePrefix("DEFINITION:")?.trim() ?: ""
        val examples = lines.filter { it.matches(Regex("^\\d+\\..*")) }
            .map { it.replaceFirst(Regex("^\\d+\\.\\s*"), "") }
        return AiResult(definition, examples)
    }

    private fun escapeJson(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
