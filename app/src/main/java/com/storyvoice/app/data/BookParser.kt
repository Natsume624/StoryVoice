package com.storyvoice.app.data

import android.content.Context
import android.net.Uri
import android.text.Html
import android.webkit.MimeTypeMap
import com.storyvoice.app.model.Book
import com.storyvoice.app.model.BookFormat
import com.storyvoice.app.model.Chapter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class BookParser(private val context: Context) {

    suspend fun import(uri: Uri): Book = withContext(Dispatchers.IO) {
        val displayName = queryName(uri) ?: "未命名书籍"
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(extension == "epub" || extension == "pdf") { "仅支持 EPUB 和 PDF 文件" }

        val id = sha256(uri.toString() + System.currentTimeMillis()).take(16)
        val bookDir = File(context.filesDir, "books").apply { mkdirs() }
        val target = File(bookDir, "$id.$extension")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选文件" }
            target.outputStream().use(input::copyTo)
        }

        try {
            when (extension) {
                "epub" -> parseEpub(id, target, displayName)
                else -> parsePdf(id, target, displayName)
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun parseEpub(id: String, file: File, fallbackName: String): Book {
        ZipFile(file).use { zip ->
            val container = zip.getEntry("META-INF/container.xml")
                ?: error("无效的 EPUB：缺少 container.xml")
            val opfPath = zip.getInputStream(container).use(::findRootFile)
                ?: error("无效的 EPUB：找不到内容清单")
            val opfEntry = zip.getEntry(opfPath) ?: error("EPUB 内容清单损坏")
            val packageInfo = zip.getInputStream(opfEntry).use(::readPackage)
            val base = opfPath.substringBeforeLast('/', "")

            val chapters = packageInfo.spine.mapNotNull { idRef ->
                val href = packageInfo.manifest[idRef] ?: return@mapNotNull null
                val path = if (base.isBlank()) href else "$base/$href"
                val entry = zip.getEntry(normalizeZipPath(path)) ?: return@mapNotNull null
                val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val plain = htmlToText(html)
                if (plain.length < 2) null else Chapter(
                    title = extractHtmlTitle(html).ifBlank { "第 ${packageInfo.spine.indexOf(idRef) + 1} 章" },
                    text = plain
                )
            }
            require(chapters.isNotEmpty()) { "未能从 EPUB 中识别出正文" }
            return Book(
                id = id,
                title = packageInfo.title.ifBlank { fallbackName.substringBeforeLast('.') },
                author = packageInfo.author.ifBlank { "未知作者" },
                format = BookFormat.EPUB,
                localPath = file.absolutePath,
                chapters = chapters,
                importedAt = System.currentTimeMillis()
            )
        }
    }

    private fun parsePdf(id: String, file: File, fallbackName: String): Book {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper()
            val chapters = (1..document.numberOfPages).mapNotNull { page ->
                stripper.startPage = page
                stripper.endPage = page
                val text = normalizeText(stripper.getText(document))
                text.takeIf { it.isNotBlank() }?.let { Chapter("第 $page 页", it) }
            }
            require(chapters.isNotEmpty()) { "未识别到 PDF 文本；扫描版 PDF 暂需 OCR 支持" }
            val info = document.documentInformation
            return Book(
                id = id,
                title = info.title?.takeIf(String::isNotBlank) ?: fallbackName.substringBeforeLast('.'),
                author = info.author?.takeIf(String::isNotBlank) ?: "未知作者",
                format = BookFormat.PDF,
                localPath = file.absolutePath,
                chapters = chapters,
                importedAt = System.currentTimeMillis()
            )
        }
    }

    private fun findRootFile(input: InputStream): String? {
        val parser = newParser(input)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            parser.next()
        }
        return null
    }

    private fun readPackage(input: InputStream): PackageInfo {
        val parser = newParser(input)
        var title = ""
        var author = ""
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':')) {
                    "title" -> if (title.isBlank()) title = parser.nextText()
                    "creator" -> if (author.isBlank()) author = parser.nextText()
                    "item" -> {
                        val itemId = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (itemId != null && href != null) manifest[itemId] = href.substringBefore('#')
                    }
                    "itemref" -> parser.getAttributeValue(null, "idref")?.let(spine::add)
                }
            }
            parser.next()
        }
        return PackageInfo(title, author, manifest, spine)
    }

    private fun newParser(input: InputStream) = XmlPullParserFactory.newInstance().newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(input, null)
    }

    @Suppress("DEPRECATION")
    private fun htmlToText(html: String): String = normalizeText(
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
    )

    private fun extractHtmlTitle(html: String): String {
        val match = Regex("<(h[1-3]|title)[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        return match?.groupValues?.get(2)?.let(::htmlToText).orEmpty().take(80)
    }

    private fun normalizeText(text: String): String = text
        .replace("\r", "")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun normalizeZipPath(path: String): String {
        val parts = mutableListOf<String>()
        path.replace('\\', '/').split('/').forEach {
            when (it) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += it
            }
        }
        return parts.joinToString("/")
    }

    private fun queryName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class PackageInfo(
        val title: String,
        val author: String,
        val manifest: Map<String, String>,
        val spine: List<String>
    )
}
