package com.screentranslate.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class HistoryRepository(context: Context) {

    private val file = File(context.filesDir, "translation_history.json")
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    companion object {
        private const val MAX_ENTRIES = 500
    }

    suspend fun getAll(): List<HistoryEntry> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<HistoryEntry>>(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun save(entry: HistoryEntry) = withContext(Dispatchers.IO) {
        val entries = getAll().toMutableList()
        entries.add(0, entry)
        if (entries.size > MAX_ENTRIES) entries.subList(MAX_ENTRIES, entries.size).clear()
        file.writeText(json.encodeToString(entries))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val entries = getAll().filter { it.id != id }
        file.writeText(json.encodeToString(entries))
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.writeText("[]")
    }
}
