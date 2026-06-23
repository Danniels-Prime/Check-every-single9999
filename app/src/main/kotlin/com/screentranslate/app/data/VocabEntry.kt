package com.screentranslate.app.data

import kotlinx.serialization.Serializable

@Serializable
data class VocabEntry(
    val english: String,
    val russian: String,
    val pronunciation: String = "",
    val category: String = "",
    val exampleRu: String = "",
    val exampleEn: String = ""
)
