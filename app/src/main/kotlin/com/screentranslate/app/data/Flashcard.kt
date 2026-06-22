package com.screentranslate.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Flashcard(
    val id: String,
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val definition: String = "",
    val examples: List<String> = emptyList(),
    val savedAt: Long = System.currentTimeMillis()
)
