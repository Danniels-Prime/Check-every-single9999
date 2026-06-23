package com.screentranslate.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long = System.currentTimeMillis()
)
