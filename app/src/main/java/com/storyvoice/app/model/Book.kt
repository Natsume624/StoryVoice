package com.storyvoice.app.model

data class Chapter(
    val title: String,
    val text: String
)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val format: BookFormat,
    val localPath: String,
    val chapters: List<Chapter>,
    val importedAt: Long,
    val progress: Float = 0f
) {
    val totalCharacters: Int get() = chapters.sumOf { it.text.length }
}

enum class BookFormat { EPUB, PDF }
