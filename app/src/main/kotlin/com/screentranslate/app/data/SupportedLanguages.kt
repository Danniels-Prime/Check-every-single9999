package com.screentranslate.app.data

object SupportedLanguages {
    val entries: List<Pair<String, String>> = listOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "zh" to "Chinese (Simplified)",
        "zh-TW" to "Chinese (Traditional)",
        "ja" to "Japanese",
        "ko" to "Korean",
        "ar" to "Arabic",
        "hi" to "Hindi",
        "tr" to "Turkish",
        "pl" to "Polish",
        "nl" to "Dutch",
        "sv" to "Swedish",
        "no" to "Norwegian",
        "da" to "Danish",
        "fi" to "Finnish",
        "uk" to "Ukrainian",
        "vi" to "Vietnamese",
        "th" to "Thai",
        "id" to "Indonesian"
    )

    fun nameForCode(code: String): String =
        entries.firstOrNull { it.first == code }?.second ?: code
}
