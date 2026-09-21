package com.wxn.base.ext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AS-1 §3.2 颜色对比度自适应纯函数单测（WCAG 相对亮度 / 对比度 / isReadableOn）。
 *
 * 不依赖 Android 类（纯 Kotlin 数学），可用纯 JVM junit。
 * 锚定值为 WCAG 标准算法手算结果，用于锁定「门控退役后大多数书会不会变色」的判定面：
 * - 真实出版书正文色 #333333 在白底可读（保留作者色）、在 #121212 夜间底不可读（回退用户色）
 * - 强调色 #FF0000 在浅色/深色底均 ≥3.0（两主题都保留）
 * - 弱化色 #777777（CR≈4.48）不被 4.5 严格线误杀、#999999（CR≈2.85）被 3.0 拦下
 */
class ContrastRatioTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    // ════════════════════════════════════════════════════════════════════
    // relativeLuminance
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun luminance_white_isOne() {
        assertEquals(1.0f, white.relativeLuminance(), 0.0001f)
    }

    @Test
    fun luminance_black_isZero() {
        assertEquals(0.0f, black.relativeLuminance(), 0.0001f)
    }

    @Test
    fun luminance_ignoresAlpha() {
        // 亮度只看 RGB；alpha 由 isReadableOn 守卫单独处理
        assertEquals(white.relativeLuminance(), 0x00FFFFFF.relativeLuminance(), 0.0001f)
    }

    // ════════════════════════════════════════════════════════════════════
    // contrastRatio（WCAG 锚定值）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun contrast_whiteOnWhite_isOne() {
        assertEquals(1.0f, white.contrastRatio(white), 0.0001f)
    }

    @Test
    fun contrast_blackOnWhite_isTwentyOne() {
        assertEquals(21.0f, black.contrastRatio(white), 0.01f)
    }

    @Test
    fun contrast_orderIndependent() {
        assertEquals(black.contrastRatio(white), white.contrastRatio(black), 0.0001f)
    }

    /** 弱化灰 #777777×白 ≈ 4.48 —— 4.5 严格线会误杀的真实出版弱化色 */
    @Test
    fun contrast_777_onWhite_is448() {
        assertEquals(4.48f, 0xFF777777.toInt().contrastRatio(white), 0.01f)
    }

    /** 更浅灰 #999999×白 ≈ 2.85 —— 低于 3.0 应判不可读 */
    @Test
    fun contrast_999_onWhite_is285() {
        assertEquals(2.85f, 0xFF999999.toInt().contrastRatio(white), 0.01f)
    }

    /** 强调红 #FF0000×白 ≈ 4.0 —— 浅色主题下保留 */
    @Test
    fun contrast_red_onWhite_is400() {
        assertEquals(3.998f, 0xFFFF0000.toInt().contrastRatio(white), 0.01f)
    }

    /** 出版正文深灰 #333333×白 ≈ 12.6 —— 门控退役后浅色主题下作者正文色生效的核心锚点 */
    @Test
    fun contrast_333_onWhite_is126() {
        assertEquals(12.6f, 0xFF333333.toInt().contrastRatio(white), 0.05f)
    }

    /** 出版正文深灰 #333333×#121212（夜间主题底）≈ 1.5 —— 夜间下回退用户色的核心锚点 */
    @Test
    fun contrast_333_onNight_is15() {
        assertEquals(1.5f, 0xFF333333.toInt().contrastRatio(0xFF121212.toInt()), 0.05f)
    }

    /** 强调红×#121212 ≈ 5.25 —— 夜间下也保留 */
    @Test
    fun contrast_red_onNight_aboveThreshold() {
        assertTrue(0xFFFF0000.toInt().contrastRatio(0xFF121212.toInt()) >= 3.0f)
    }

    // ════════════════════════════════════════════════════════════════════
    // isReadableOn：低 alpha 守卫 + 阈值判定
    // ════════════════════════════════════════════════════════════════════

    /** transparent（alpha=0）在白底亮度对比虚高 21:1，但实际不可见 —— alpha 守卫必须拦下 */
    @Test
    fun readable_transparentOnWhite_guardedFalse() {
        assertFalse(0x00FFFFFF.isReadableOn(white))
    }

    /** 低 alpha（0x10 = 6.3%，< 0x1A≈10%）守卫 */
    @Test
    fun readable_lowAlpha_guardedFalse() {
        assertFalse(0x10FFFFFF.isReadableOn(white))
    }

    /** alpha 恰在守卫界上（0x1A≈10%）不再拦截，交由对比度按纯 RGB 判定（红×白 CR≈4.0 → 可读） */
    @Test
    fun readable_alphaAtGuardBoundary_passesToContrast() {
        assertTrue(0x1AFF0000.toInt().isReadableOn(white))
    }

    /** rgba(255,0,0,0.05) 解析产物 ≈0x0DFF0000：低 alpha 守卫拦截（仪器测试 I2 的 JVM 侧锚定） */
    @Test
    fun readable_rgbaVeryLowAlpha_false() {
        assertFalse(0x0DFF0000.toInt().isReadableOn(white))
    }

    /** 智能对比：白字×白底不可读（回退用户色）、红字×白底可读（保留作者色） */
    @Test
    fun readable_smartBaselineCases() {
        assertFalse(white.isReadableOn(white))
        assertTrue(0xFFFF0000.toInt().isReadableOn(white))
    }

    /** 夜间底矩阵：深灰字不可读（回退）、白字可读（保留设计） */
    @Test
    fun readable_nightThemeMatrix() {
        val night = 0xFF121212.toInt()
        assertFalse(0xFF333333.toInt().isReadableOn(night))
        assertTrue(white.isReadableOn(night))
    }

    /** 阈值边界：CR≈2.85 的 #999999 在 2.8 阈值下可读、2.9/3.0 下不可读（>= 判定） */
    @Test
    fun readable_thresholdBoundary() {
        val gray = 0xFF999999.toInt()
        assertTrue(gray.isReadableOn(white, threshold = 2.8f))
        assertFalse(gray.isReadableOn(white, threshold = 2.9f))
        assertFalse(gray.isReadableOn(white, threshold = CONTRAST_READABLE_THRESHOLD))
    }
}
