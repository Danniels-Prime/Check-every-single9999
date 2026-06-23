package com.screentranslate.app.ai

data class AiResult(
    val definition: String,
    val examples: List<String>
)

interface AiExplainer {
    suspend fun explain(original: String, translated: String, targetLang: String): AiResult
}
