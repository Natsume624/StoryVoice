package com.storyvoice.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storyvoice.app.audio.CloudNarrator
import com.storyvoice.app.data.BookParser
import com.storyvoice.app.data.LibraryStore
import com.storyvoice.app.model.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppState(
    val books: List<Book> = emptyList(),
    val selectedBook: Book? = null,
    val selectedChapter: Int = 0,
    val isImporting: Boolean = false,
    val message: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val parser = BookParser(application)
    private val store = LibraryStore(application)
    val narrator = CloudNarrator(application)
    private val _state = MutableStateFlow(AppState(books = store.load()))
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

    fun open(book: Book) {
        narrator.stop()
        _state.value = _state.value.copy(selectedBook = book, selectedChapter = 0)
    }

    fun closeReader() {
        narrator.stop()
        _state.value = _state.value.copy(selectedBook = null)
    }

    fun selectChapter(index: Int) {
        narrator.stop()
        _state.value = _state.value.copy(selectedChapter = index)
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }

    override fun onCleared() { narrator.shutdown() }
}
