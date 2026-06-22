package com.screentranslate.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class FlashcardRepository(context: Context) {

    private val file = File(context.filesDir, "flashcards.json")
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    suspend fun getAll(): List<Flashcard> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<Flashcard>>(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun save(card: Flashcard) = withContext(Dispatchers.IO) {
        val cards = getAll().toMutableList()
        cards.removeAll { it.id == card.id }
        cards.add(0, card)
        file.writeText(json.encodeToString(cards))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val cards = getAll().filter { it.id != id }
        file.writeText(json.encodeToString(cards))
    }

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val cards = getAll()
        buildString {
            appendLine("Original,Translation,Definition,Examples")
            cards.forEach { card ->
                val examples = card.examples.joinToString(" | ")
                appendLine("\"${card.originalText.csv()}\",\"${card.translatedText.csv()}\",\"${card.definition.csv()}\",\"${examples.csv()}\"")
            }
        }
    }

    private fun String.csv() = replace("\"", "\"\"")
}
