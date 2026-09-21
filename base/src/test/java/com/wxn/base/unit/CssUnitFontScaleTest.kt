package com.wxn.base.unit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AS-1 §3.1 [CssUnit.toFontScale] 字号归一化单测（以注入 basePx 断言，不依赖 density）。
 *
 * - em/rem：值即倍率；percent：值/100；px：值/basePx（锚定应用默认正文基准 16sp≈44px@density2.75）
 * - 结果 clamp 到 [CssUnit.MIN_FONT_SCALE, CssUnit.MAX_FONT_SCALE]
 * - Undifined/Auto → null（不缩放）
 */
class CssUnitFontScaleTest {

    private val basePx = 44f

    // ════════════════════════════════════════════════════════════════════
    // em / rem：值即倍率
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun em_normal() {
        assertEquals(1.5f, CssUnit.Em(1.5f).toFontScale(basePx)!!, 0.0001f)
    }

    @Test
    fun em_negative_clampedToMin() {
        assertEquals(0.5f, CssUnit.Em(-2f).toFontScale(basePx)!!, 0.0001f)
    }

    @Test
    fun em_zero_clampedToMin() {
        assertEquals(0.5f, CssUnit.Em(0f).toFontScale(basePx)!!, 0.0001f)
    }

    @Test
    fun rem_value() {
        assertEquals(2.0f, CssUnit.Rem(2f).toFontScale(basePx)!!, 0.0001f)
    }

    @Test
    fun em_overlarge_clampedToMax() {
        assertEquals(5.0f, CssUnit.Em(9f).toFontScale(basePx)!!, 0.0001f)
    }

    // ════════════════════════════════════════════════════════════════════
    // percent：值/100（现网被忽略，本期补齐）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun percent_normal() {
        assertEquals(1.5f, CssUnit.Percent(150f).toFontScale(basePx)!!, 0.0001f)
    }

    @Test
    fun percent_zero_clampedToMin() {
        assertEquals(0.5f, CssUnit.Percent(0f).toFontScale(basePx)!!, 0.0001f)
    }

    // ════════════════════════════════════════════════════════════════════
    // px：锚定换算（format 解析期 px/pt 已 clamp 到 [48,84]）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun px_clampedLowValue_anchored() {
        assertEquals(48f / basePx, CssUnit.Px(48f).toFontScale(basePx)!!, 0.0001f)
        assertEquals(1.0909f, CssUnit.Px(48f).toFontScale(basePx)!!, 0.001f)
    }

    @Test
    fun px_clampedHighValue_anchored() {
        assertEquals(84f / basePx, CssUnit.Px(84f).toFontScale(basePx)!!, 0.0001f)
        assertEquals(1.909f, CssUnit.Px(84f).toFontScale(basePx)!!, 0.001f)
    }

    // ════════════════════════════════════════════════════════════════════
    // 不缩放类型
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun undefined_returnsNull() {
        assertNull(CssUnit(0f).toFontScale(basePx))
    }

    @Test
    fun auto_returnsNull() {
        assertNull(CssUnit.Auto.toFontScale(basePx))
    }

    /** 默认 fontSize=Em(1.0f) → 倍率 1.0，等价无操作（textCssInfo 缺省路径） */
    @Test
    fun defaultEmOne_isUnity() {
        assertEquals(1.0f, CssUnit.Em(1.0f).toFontScale(basePx)!!, 0.0001f)
    }

    // ════════════════════════════════════════════════════════════════════
    // 常量契约（ReaderText span 分支共享，值与原 MIN/MAX_INLINE_SCALE 一致）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun constants_values() {
        assertEquals(0.5f, CssUnit.MIN_FONT_SCALE, 0.0001f)
        assertEquals(5.0f, CssUnit.MAX_FONT_SCALE, 0.0001f)
        assertTrue(CssUnit.MIN_FONT_SCALE < CssUnit.MAX_FONT_SCALE)
        assertFalse(CssUnit.MIN_FONT_SCALE <= 0f)
    }
}
