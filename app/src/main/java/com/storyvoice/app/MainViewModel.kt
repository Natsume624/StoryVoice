package com.storyvoice.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storyvoice.app.audio.CloudNarrator
import com.storyvoice.app.data.BookParser
import com.storyvoice.app.data.LibraryStore
import com.storyvoice.app.model.Book
import com.storyvoice.app.model.BookCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppState(
    val books: List<Book> = emptyList(),
    val collections: List<BookCollection> = emptyList(),
    val selectedCollectionId: String? = null,
    val selectedBook: Book? = null,
    val pendingResumeBook: Book? = null,
    val selectedChapter: Int = 0,
    val isImporting: Boolean = false,
    val message: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val parser = BookParser(application)
    private val store = LibraryStore(application)
    val narrator = CloudNarrator(application)
    private val _state = MutableStateFlow(AppState(books = store.load(), collections = store.loadCollections()))
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun import(uri: Uri) = viewModelScope.launch {
        _state.value = _state.value.copy(isImporting = true, message = null)
        runCatching { parser.import(uri) }
            .onSuccess { book ->
                val books = listOf(book) + _state.value.books.filterNot { it.id == book.id }
                store.save(books)
                _state.value = _state.value.copy(books = books, isImporting = false, message = "《${book.title}》已加入书架")
            }
            .onFailure { error ->
                _state.value = _state.value.copy(isImporting = false, message = error.message ?: "导入失败")
            }
    }

    fun requestOpen(book: Book) {
        if (book.progress > 0.001f) _state.value = _state.value.copy(pendingResumeBook = book)
        else open(book, resume = false)
    }

    fun open(book: Book, resume: Boolean) {
        narrator.stop()
        val opened = book.copy(lastOpenedAt = System.currentTimeMillis())
        val books = _state.value.books.map { if (it.id == book.id) opened else it }
        store.saveProgress(opened)
        _state.value = _state.value.copy(
            books = books,
            selectedBook = opened,
            selectedChapter = if (resume) opened.lastChapter.coerceIn(opened.chapters.indices) else 0,
            pendingResumeBook = null
        )
    }

    fun dismissResume() { _state.value = _state.value.copy(pendingResumeBook = null) }

    fun closeReader() {
        narrator.stop()
        _state.value = _state.value.copy(selectedBook = null)
    }

    fun selectChapter(index: Int) {
        narrator.stop()
        _state.value = _state.value.copy(selectedChapter = index)
    }

    fun saveProgress(chapterIndex: Int, scrollFraction: Float) {
        val current = _state.value.selectedBook ?: return
        val safeChapter = chapterIndex.coerceIn(current.chapters.indices)
        val before = current.chapters.take(safeChapter).sumOf { it.text.length }
        val overall = if (current.totalCharacters == 0) 0f else
            (before + current.chapters[safeChapter].text.length * scrollFraction.coerceIn(0f, 1f)) / current.totalCharacters.toFloat()
        val updated = current.copy(
            progress = overall.coerceIn(0f, 1f),
            lastChapter = safeChapter,
            lastScrollFraction = scrollFraction.coerceIn(0f, 1f),
            lastOpenedAt = System.currentTimeMillis()
        )
        val books = _state.value.books.map { if (it.id == updated.id) updated else it }
        store.saveProgress(updated)
        _state.value = _state.value.copy(books = books, selectedBook = updated)
    }

    fun createCollection(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val collections = _state.value.collections + BookCollection(
            id = java.util.UUID.randomUUID().toString(), name = clean
        )
        store.saveCollections(collections)
        _state.value = _state.value.copy(collections = collections)
    }

    fun setCollectionFilter(id: String?) {
        _state.value = _state.value.copy(selectedCollectionId = id)
    }

    fun setBookCollections(bookId: String, collectionIds: Set<String>) {
        val collections = _state.value.collections.map { collection ->
            collection.copy(bookIds = if (collection.id in collectionIds) collection.bookIds + bookId else collection.bookIds - bookId)
        }
        store.saveCollections(collections)
        _state.value = _state.value.copy(collections = collections)
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }

    override fun onCleared() { narrator.shutdown() }
}
