package com.storyvoice.app.data

import android.content.Context
import com.storyvoice.app.model.Book
import com.storyvoice.app.model.BookFormat
import com.storyvoice.app.model.BookCollection
import com.storyvoice.app.model.Chapter
import org.json.JSONArray
import org.json.JSONObject

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun load(): List<Book> = runCatching {
        val array = JSONArray(prefs.getString("books", "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).toBook() }
    }.getOrDefault(emptyList())

    fun save(books: List<Book>) {
        val array = JSONArray().apply { books.forEach { put(it.toJson()) } }
        prefs.edit().putString("books", array.toString()).apply()
    }

    fun loadCollections(): List<BookCollection> = runCatching {
        val array = JSONArray(prefs.getString("collections", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val ids = item.optJSONArray("bookIds") ?: JSONArray()
            BookCollection(
                id = item.getString("id"),
                name = item.getString("name"),
                bookIds = (0 until ids.length()).map(ids::getString).toSet()
            )
        }
    }.getOrDefault(emptyList())

    fun saveCollections(collections: List<BookCollection>) {
        val array = JSONArray().apply {
            collections.forEach { collection ->
                put(JSONObject().put("id", collection.id).put("name", collection.name)
                    .put("bookIds", JSONArray(collection.bookIds.toList())))
            }
        }
        prefs.edit().putString("collections", array.toString()).apply()
    }

    private fun Book.toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("author", author)
        put("format", format.name); put("path", localPath); put("at", importedAt); put("progress", progress)
        put("lastChapter", lastChapter); put("lastScrollFraction", lastScrollFraction); put("lastOpenedAt", lastOpenedAt)
        put("chapters", JSONArray().apply {
            chapters.forEach { put(JSONObject().put("title", it.title).put("text", it.text)) }
        })
    }

    private fun JSONObject.toBook(): Book {
        val chapterArray = getJSONArray("chapters")
        return Book(
            id = getString("id"), title = getString("title"), author = getString("author"),
            format = BookFormat.valueOf(getString("format")), localPath = getString("path"),
            importedAt = getLong("at"), progress = optDouble("progress", 0.0).toFloat(),
            lastChapter = optInt("lastChapter", 0),
            lastScrollFraction = optDouble("lastScrollFraction", 0.0).toFloat(),
            lastOpenedAt = optLong("lastOpenedAt", 0L),
            chapters = (0 until chapterArray.length()).map { i ->
                chapterArray.getJSONObject(i).let { Chapter(it.getString("title"), it.getString("text")) }
            }
        )
    }
}
