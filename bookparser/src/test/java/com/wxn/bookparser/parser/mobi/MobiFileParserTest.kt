package com.wxn.bookparser.parser.mobi

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 方案 docs/plans/2026-09-17-plan-opds-prc-mobi-open-stuck.md §6.3：
 * DB fileType 归一化——仅 prc → mobi，其余（含 azw3/epub 及大小写变体）原样保留。
 */
class MobiFileParserTest {

    @Test
    fun prcNormalizedToMobi() = assertEquals("mobi", MobiFileParser.normalizeStoredFileType("prc"))

    @Test
    fun prcCaseInsensitive() {
        assertEquals("mobi", MobiFileParser.normalizeStoredFileType("PRC"))
        assertEquals("mobi", MobiFileParser.normalizeStoredFileType("Prc"))
    }

    @Test
    fun mobiUntouched() = assertEquals("mobi", MobiFileParser.normalizeStoredFileType("mobi"))

    @Test
    fun azw3Untouched() = assertEquals("azw3", MobiFileParser.normalizeStoredFileType("azw3"))

    @Test
    fun epubUntouched() = assertEquals("epub", MobiFileParser.normalizeStoredFileType("epub"))
}
