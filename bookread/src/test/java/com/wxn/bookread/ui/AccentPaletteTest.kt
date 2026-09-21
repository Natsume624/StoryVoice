package com.wxn.bookread.ui

import com.wxn.base.ext.isReadableOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AD-1 §5.1：色板纯 JVM 单测（AccentPalette 无 android.graphics 依赖，直跑 JUnit）。
 * B5 为色值表回归钉：任何色值调整必须显式改本表并同步验收文档（方案 §3.2 表）。
 */
class AccentPaletteTest {

    private val sepia = 0xFFF5E6C8.toInt()
    private val nearBlack = 0xFF1A1A1A.toInt()

    // B1 判暗边界（0.25 阈值两侧取样，实算亮度：#757575≈0.184 暗 / #9E9E9E≈0.389 亮）
    @Test
    fun B1_isDarkBackground_threshold() {
        assertTrue(AccentPalette.isDarkBackground(0xFF000000.toInt()))
        assertTrue(AccentPalette.isDarkBackground(0xFF424242.toInt()))   // ≈0.062
        assertTrue(AccentPalette.isDarkBackground(0xFF757575.toInt()))
        assertFalse(AccentPalette.isDarkBackground(0xFF9E9E9E.toInt()))
        assertFalse(AccentPalette.isDarkBackground(sepia))               // ≈0.79
        assertFalse(AccentPalette.isDarkBackground(0xFFFFFFFF.toInt()))
    }

    // B2 resolve 分档
    @Test
    fun B2_resolve_selectsVariant() {
        assertEquals(AccentPalette.LIGHT, AccentPalette.resolve(0xFFFFFFFF.toInt()))
        assertEquals(AccentPalette.LIGHT, AccentPalette.resolve(sepia))
        assertEquals(AccentPalette.DARK, AccentPalette.resolve(nearBlack))
        assertEquals(AccentPalette.DARK, AccentPalette.resolve(0xFF000000.toInt()))
    }

    // B3 链接色可读性（不透明项走 isReadableOn 全量判定，含低 alpha 守卫）
    @Test
    fun B3_link_readableOnRepresentativeBgs() {
        assertTrue(AccentPalette.LIGHT.link.isReadableOn(0xFFFFFFFF.toInt()))
        assertTrue(AccentPalette.LIGHT.link.isReadableOn(sepia))
        assertTrue(AccentPalette.DARK.link.isReadableOn(0xFF000000.toInt()))
        assertTrue(AccentPalette.DARK.link.isReadableOn(nearBlack))
        assertTrue(AccentPalette.DARK.link.isReadableOn(0xFF424242.toInt()))
    }

    // B4 不透明默认色（手柄/书签/下划线 fallback）可读性
    @Test
    fun B4_opaqueDefaults_readable() {
        assertTrue(AccentPalette.LIGHT.selectionHandle.isReadableOn(0xFFFFFFFF.toInt()))
        assertTrue(AccentPalette.DARK.selectionHandle.isReadableOn(0xFF000000.toInt()))
        assertTrue(AccentPalette.LIGHT.bookmark.isReadableOn(0xFFFFFFFF.toInt()))
        assertTrue(AccentPalette.DARK.bookmark.isReadableOn(0xFF000000.toInt()))
        assertTrue(AccentPalette.LIGHT.underlineFallback.isReadableOn(0xFFFFFFFF.toInt()))
        assertTrue(AccentPalette.DARK.underlineFallback.isReadableOn(0xFF000000.toInt()))
    }

    // B5 色值表回归钉（9 字段 × 2 档；改动色值必须同步本用例 + 方案 §3.2 表 + 验收文档）
    @Test
    fun B5_paletteTable_regressionPins() {
        assertEquals(0xFF0B57D0.toInt(), AccentPalette.LIGHT.link)
        assertEquals(0x4000BFFF, AccentPalette.LIGHT.searchHighlight)
        assertEquals(0xFFFFFF00.toInt(), AccentPalette.LIGHT.readAloudBg)
        assertEquals(0xFF1A73E8.toInt(), AccentPalette.LIGHT.selectionHandle)
        assertEquals(0xFFCCCCCC.toInt(), AccentPalette.LIGHT.imagePlaceholder)
        assertEquals(0xFF575757.toInt(), AccentPalette.LIGHT.bookmark)
        assertEquals(0xFF575757.toInt(), AccentPalette.LIGHT.underlineFallback)
        assertEquals(0xFFFFFF00.toInt(), AccentPalette.LIGHT.highlightFallback)
        assertEquals(0xFFFFFF00.toInt(), AccentPalette.LIGHT.noteFallback)
        assertEquals(0xFF8AB4F8.toInt(), AccentPalette.DARK.link)
        assertEquals(0x668AB4F8.toInt(), AccentPalette.DARK.searchHighlight)
        assertEquals(0xFFFFC107.toInt(), AccentPalette.DARK.readAloudBg)
        assertEquals(0xFF90CAF9.toInt(), AccentPalette.DARK.selectionHandle)
        assertEquals(0xFF424242.toInt(), AccentPalette.DARK.imagePlaceholder)
        assertEquals(0xFFBDBDBD.toInt(), AccentPalette.DARK.bookmark)
        assertEquals(0xFFBDBDBD.toInt(), AccentPalette.DARK.underlineFallback)
        assertEquals(0x80FFC107.toInt(), AccentPalette.DARK.highlightFallback)
        assertEquals(0xFFFFFF00.toInt(), AccentPalette.DARK.noteFallback)
    }
}
