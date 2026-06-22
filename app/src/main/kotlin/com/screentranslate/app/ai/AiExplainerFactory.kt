package com.screentranslate.app.ai

object AiExplainerFactory {
    fun create(provider: String, apiKey: String): AiExplainer? {
        if (apiKey.isBlank() || provider == "none") return null
        return when (provider) {
            "openai"   -> OpenAiExplainer(apiKey, "https://api.openai.com", "gpt-4o-mini")
            "deepseek" -> OpenAiExplainer(apiKey, "https://api.deepseek.com", "deepseek-chat")
            "gemini"   -> GeminiExplainer(apiKey)
            else       -> null
        }
    }
}
