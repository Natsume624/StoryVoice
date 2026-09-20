package com.storyvoice.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyvoice.app.audio.NarrationState
import com.storyvoice.app.audio.NarrationVoice
import com.storyvoice.app.model.Book
import com.storyvoice.app.model.BookFormat
import com.storyvoice.app.ui.theme.StoryVoiceTheme

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StoryVoiceTheme { StoryVoiceApp(model) } }
    }
}

@Composable
private fun StoryVoiceApp(model: MainViewModel = viewModel()) {
    val state by model.state.collectAsState()
    val narration by model.narrator.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::import)
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); model.dismissMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        AnimatedContent(
            targetState = state.selectedBook,
            label = "screen",
            modifier = Modifier.padding(padding)
        ) { book ->
            if (book == null) {
                LibraryScreen(
                    books = state.books,
                    isImporting = state.isImporting,
                    onImport = { picker.launch(arrayOf("application/epub+zip", "application/pdf")) },
                    onBookClick = model::open
                )
            } else {
                ReaderScreen(
                    book = book,
                    chapterIndex = state.selectedChapter,
                    narration = narration,
                    voices = model.narrator.availableVoices,
                    onBack = model::closeReader,
                    onChapter = model::selectChapter,
                    onPlayPause = {
                        if (narration.isPlaying) model.narrator.pause()
                        else model.narrator.play(book.chapters[state.selectedChapter].text)
                    },
                    onSeek = model.narrator::seekTo,
                    onRate = model.narrator::setRate,
                    onExpressiveness = model.narrator::setExpressiveness,
                    onVoice = model.narrator::setVoice,
                    onServerUrl = model.narrator::setServerUrl
                )
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    books: List<Book>,
    isImporting: Boolean,
    onImport: () -> Unit,
    onBookClick: (Book) -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("拾声", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("让每一本书，都被温柔讲述", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("我的书架", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("${books.size} 本书 · 随时继续你的故事", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (books.isEmpty()) {
            EmptyLibrary(isImporting, onImport)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { ImportCard(isImporting, onImport) }
                items(books, key = { it.id }) { BookCard(it) { onBookClick(it) } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(isImporting: Boolean, onImport: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(124.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(24.dp))
            Text("书架还是空的", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("导入 EPUB 或文字版 PDF\n从今天开始听完一本书", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 23.sp)
            Spacer(Modifier.height(28.dp))
            Button(onClick = onImport, enabled = !isImporting, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp)) {
                if (isImporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isImporting) "正在识别…" else "导入一本书")
            }
        }
    }
}

@Composable
private fun ImportCard(isImporting: Boolean, onImport: () -> Unit) {
    Card(
        onClick = onImport,
        enabled = !isImporting,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(45.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                if (isImporting) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (isImporting) "正在识别书籍" else "导入新书", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("支持 EPUB 和文字版 PDF", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 76.dp, height = 104.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF29463F), Color(0xFF6F8D79)))),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = Color(0xFFF4D9A4), modifier = Modifier.size(28.dp))
                    Text(book.format.name, color = Color.White.copy(alpha = .75f), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(13.dp))
                Text("${book.chapters.size} ${if (book.format == BookFormat.PDF) "页" else "章"} · ${book.totalCharacters / 1000} 千字", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Rounded.MoreHoriz, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    book: Book,
    chapterIndex: Int,
    narration: NarrationState,
    voices: List<NarrationVoice>,
    onBack: () -> Unit,
    onChapter: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onRate: (Float) -> Unit,
    onExpressiveness: (Float) -> Unit,
    onVoice: (String) -> Unit,
    onServerUrl: (String) -> Boolean
) {
    val chapter = book.chapters[chapterIndex]
    var rate by remember { mutableFloatStateOf(.9f) }
    var warmth by remember { mutableFloatStateOf(.96f) }
    var voiceMenuOpen by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(19f) }
    var nightMode by remember { mutableStateOf(false) }
    var serverDialogOpen by remember { mutableStateOf(false) }
    var serverUrlDraft by remember(narration.serverUrl) { mutableStateOf(narration.serverUrl) }
    val scrollState = rememberScrollState()
    val readingBackground = if (nightMode) Color(0xFF171A18) else MaterialTheme.colorScheme.background
    val readingTextColor = if (nightMode) Color(0xFFE6E2D9) else MaterialTheme.colorScheme.onBackground.copy(alpha = .88f)
    val highlightColor = if (nightMode) Color(0xFF496A5E) else Color(0xFFF3DCA9)
    val highlightedText = remember(chapter.text, narration.currentText, narration.currentOffset, highlightColor) {
        highlightCurrentSentence(chapter.text, narration.currentText, narration.currentOffset, highlightColor)
    }
    val progress = if (narration.totalChunks > 0) narration.currentChunk.toFloat() / narration.totalChunks else 0f

    LaunchedEffect(chapterIndex) { scrollState.scrollTo(0) }
    LaunchedEffect(narration.currentText, scrollState.maxValue) {
        if (narration.currentText.isNotBlank() && narration.isPlaying) {
            val start = narration.currentOffset.takeIf {
                it >= 0 && chapter.text.regionMatches(it, narration.currentText, 0, narration.currentText.length)
            } ?: chapter.text.indexOf(narration.currentText)
            if (start >= 0 && chapter.text.isNotEmpty()) {
                val target = (scrollState.maxValue * start.toFloat() / chapter.text.length).toInt()
                scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(readingBackground)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = readingTextColor) }
            Column(Modifier.weight(1f)) {
                Text(book.title, fontWeight = FontWeight.Bold, color = readingTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chapter.title, fontSize = 12.sp, color = readingTextColor.copy(alpha = .65f))
            }
            IconButton(onClick = { fontSize = (fontSize - 1f).coerceAtLeast(15f) }) { Icon(Icons.Rounded.TextDecrease, "缩小字号", tint = readingTextColor) }
            IconButton(onClick = { fontSize = (fontSize + 1f).coerceAtMost(28f) }) { Icon(Icons.Rounded.TextIncrease, "放大字号", tint = readingTextColor) }
            IconButton(onClick = { nightMode = !nightMode }) {
                Icon(if (nightMode) Icons.Rounded.WbSunny else Icons.Rounded.DarkMode, if (nightMode) "日间模式" else "夜间模式", tint = readingTextColor)
            }
            IconButton(onClick = {
                serverUrlDraft = narration.serverUrl
                serverDialogOpen = true
            }) { Icon(Icons.Rounded.Settings, "朗读服务设置", tint = readingTextColor) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 26.dp, vertical = 12.dp)
        ) {
            Text(chapter.title, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = readingTextColor)
            Spacer(Modifier.height(22.dp))
            Text(highlightedText, fontSize = fontSize.sp, lineHeight = (fontSize * 1.68f).sp, color = readingTextColor)
            Spacer(Modifier.height(30.dp))
        }

        Surface(tonalElevation = 8.dp, shadowElevation = 10.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("讲述音色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { voiceMenuOpen = true }) {
                            Text(narration.voiceLabel, fontWeight = FontWeight.Bold)
                            Icon(Icons.Rounded.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                            voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(voice.label, fontWeight = if (voice.id == narration.voiceId) FontWeight.Bold else FontWeight.Normal)
                                            Text(voice.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        onVoice(voice.id)
                                        voiceMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
                Slider(value = progress, onValueChange = onSeek, modifier = Modifier.height(30.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (chapterIndex > 0) onChapter(chapterIndex - 1) }, enabled = chapterIndex > 0) {
                        Icon(Icons.Rounded.KeyboardArrowLeft, "上一章", Modifier.size(32.dp))
                    }
                    IconButton(
                        onClick = onPlayPause,
                        enabled = narration.isReady && !narration.isLoading,
                        modifier = Modifier.size(62.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    ) {
                        if (narration.isLoading) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (narration.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (narration.isPlaying) "暂停" else "播放",
                                Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = { if (chapterIndex < book.chapters.lastIndex) onChapter(chapterIndex + 1) }, enabled = chapterIndex < book.chapters.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowRight, "下一章", Modifier.size(32.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("语速", fontSize = 12.sp, modifier = Modifier.width(34.dp))
                    Slider(value = rate, onValueChange = { rate = it; onRate(it) }, valueRange = .65f..1.25f, modifier = Modifier.weight(1f))
                    Text(String.format("%.1fx", rate), fontSize = 12.sp, modifier = Modifier.width(38.dp))
                    Text("情感", fontSize = 12.sp, modifier = Modifier.width(34.dp))
                    Slider(value = warmth, onValueChange = { warmth = it; onExpressiveness(it) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                }
                Text(
                    "AI 配音 · ${narration.speaker} · ${narration.emotion}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)
                )
                narration.error?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(top = 5.dp))
                        TextButton(onClick = {
                            serverUrlDraft = narration.serverUrl
                            serverDialogOpen = true
                        }) { Text("设置服务") }
                    }
                }
            }
        }
    }

    if (serverDialogOpen) {
        AlertDialog(
            onDismissRequest = { serverDialogOpen = false },
            title = { Text("朗读服务地址") },
            text = {
                Column {
                    Text("真机需填写电脑的局域网地址或已部署的 HTTPS 地址。手机与电脑必须在同一网络。")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = serverUrlDraft,
                        onValueChange = { serverUrlDraft = it },
                        label = { Text("例如 http://192.168.50.149:8787") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onServerUrl(serverUrlDraft)) serverDialogOpen = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { serverDialogOpen = false }) { Text("取消") } }
        )
    }
}

private fun highlightCurrentSentence(fullText: String, currentText: String, currentOffset: Int, color: Color): AnnotatedString {
    if (currentText.isBlank()) return AnnotatedString(fullText)
    val start = currentOffset.takeIf {
        it >= 0 && fullText.regionMatches(it, currentText, 0, currentText.length)
    } ?: fullText.indexOf(currentText)
    if (start < 0) return AnnotatedString(fullText)
    return buildAnnotatedString {
        append(fullText.substring(0, start))
        withStyle(SpanStyle(background = color, fontWeight = FontWeight.SemiBold)) {
            append(currentText)
        }
        append(fullText.substring(start + currentText.length))
    }
}
