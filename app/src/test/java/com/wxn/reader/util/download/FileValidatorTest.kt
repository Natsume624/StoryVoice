package com.wxn.reader.util.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 方案 docs/plans/2026-09-17-plan-opds-prc-mobi-open-stuck.md §6.1：
 * prc 扩展名纳入 MOBI 魔数校验（BOOKMOBI @60），与 mobi/azw3 同路径；
 * 非 MOBI 内容 + prc 扩展名必须拒绝。
 */
class FileValidatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 78 字节最小 MOBI 头：偏移 60 处写 BOOKMOBI（PDB 容器 magic） */
    private fun mobiHeader(): ByteArray = ByteArray(78).also {
        "BOOKMOBI".toByteArray(Charsets.US_ASCII).copyInto(it, 60)
    }

    @Test
    fun prcWithMobiMagicPasses() {
        val f: File = tmp.newFile("27475.kf8.images.prc").apply { writeBytes(mobiHeader()) }
        assertTrue(FileValidator.validate(f, "prc").isValid)
    }

    @Test
    fun prcWithGarbageFails() {
        val f: File = tmp.newFile("x.prc").apply { writeBytes(ByteArray(200)) }
        assertFalse(FileValidator.validate(f, "prc").isValid)
    }

    @Test
    fun prcTooShortFails() {
        val f: File = tmp.newFile("short.prc").apply { writeBytes(ByteArray(10)) }
        assertFalse(FileValidator.validate(f, "prc").isValid)
    }

    @Test
    fun mobiAndAzw3StillValidated() {
        // 回归锚点：既有 mobi/azw3 校验路径不变
        val good: File = tmp.newFile("a.mobi").apply { writeBytes(mobiHeader()) }
        val bad: File = tmp.newFile("b.azw3").apply { writeBytes(ByteArray(100)) }
        assertTrue(FileValidator.validate(good, "mobi").isValid)
        assertFalse(FileValidator.validate(bad, "azw3").isValid)
    }

    @Test
    fun emptyFileFails() {
        val f: File = tmp.newFile("empty.prc").apply { writeBytes(ByteArray(0)) }
        assertFalse(FileValidator.validate(f, "prc").isValid)
    }

    @Test
    fun unknownExtensionPassesThrough() {
        // 既有行为：表外扩展名不做魔数校验（返回 valid）
        val f: File = tmp.newFile("c.xyz").apply { writeBytes(ByteArray(50)) }
        assertTrue(FileValidator.validate(f, "xyz").isValid)
    }
}
