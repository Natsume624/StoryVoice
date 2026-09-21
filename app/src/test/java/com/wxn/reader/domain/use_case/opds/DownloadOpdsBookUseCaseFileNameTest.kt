package com.wxn.reader.domain.use_case.opds

import com.wxn.reader.domain.use_case.opds.DownloadOpdsBookUseCase.Companion.buildDownloadFileName
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 方案 docs/plans/2026-09-17-plan-opds-prc-mobi-open-stuck.md §6.2：
 * 直测 companion 纯函数 buildDownloadFileName，无需构造 UseCase（context/manager 均不涉及）。
 */
class DownloadOpdsBookUseCaseFileNameTest {

    @Test
    fun knownPrcKeepsOriginalName() {
        assertEquals(
            "27475.kf8.images.prc",
            buildDownloadFileName("27475.kf8.images.prc", "T", "application/x-mobipocket-ebook")
        )
    }

    @Test
    fun knownEpubKeepsName() {
        assertEquals("foo.epub", buildDownloadFileName("foo.epub", "T", "application/epub+zip"))
    }

    @Test
    fun unknownUtf8ExtReplacedNoDoubleExt() {
        assertEquals("12345.txt", buildDownloadFileName("12345.txt.utf-8", "T", "text/plain"))
    }

    @Test
    fun unknownXyzReplacedByMimeExt() {
        assertEquals("foo.mobi", buildDownloadFileName("foo.xyz", "T", "application/x-mobipocket-ebook"))
    }

    @Test
    fun noUrlNameUsesSanitizedTitle() {
        // sanitize 移除非白名单字符（不插空格）："My/Book" → "MyBook"（r2-R4 复核修正）
        assertEquals("MyBook.epub", buildDownloadFileName(null, "My/Book", "application/epub+zip"))
    }

    @Test
    fun dotfileNameFallsBackToAppend() {
        assertEquals(".hidden.mobi", buildDownloadFileName(".hidden", "T", "application/x-mobipocket-ebook"))
    }

    @Test
    fun azw3MimeMapsToAzw3Ext() {
        assertEquals("foo.azw3", buildDownloadFileName("foo.kf8", "T", "application/vnd.amazon.mobi8-ebook"))
    }
}
