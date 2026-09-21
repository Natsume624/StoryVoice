package com.wxn.bookread.ui

import android.content.Context
import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.unit.CssUnit
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.preference.BASE_FONT_SIZE
import com.wxn.bookread.data.model.preference.BookColorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * AS-1 P2 applyCharPaint 三态颜色 + 归一化仪器测试（方案 §5 Phase 7，I1-I7）。
 *
 * 直调 [RenderResources.applyCharPaint]，不依赖 ChapterProvider 初始化时序：
 * `RenderResources.init(context)` 后本地构造 parent TextPaint 模拟绘制现场
 * （`drawingPaint.set(parent)`），断言绘制终态。
 *
 * 断言基准用 [BASE_FONT_SIZE] 常量现算（勿硬编码 44px 之类 density 换算值——设备 density 不定），
 * 浮点比较用容差 ±0.01f。
 *
 * 运行（需连接设备/模拟器）：
 * ```
 * gradlew.bat :bookread:assembleDebugAndroidTest
 * adb install -r bookread\build\outputs\apk\androidTest\debug\bookread-debug-androidTest.apk
 * adb shell am instrument -w -e class com.wxn.bookread.ui.RenderResourcesApplyCharPaintInstrumentedTest ^
 *   com.wxn.bookread.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class RenderResourcesApplyCharPaintInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        RenderResources.init(context)
        // 隔离：恢复默认模式/背景，避免用例间经单例互相污染
        RenderResources.bookColorMode = BookColorMode.SMART
        RenderResources.pageBgColor = Color.WHITE
    }

    /** 模拟绘制现场：drawingPaint.set(parent) 后调 applyCharPaint（与 ContentTextView/滚动视图同构） */
    private fun applyWith(
        cssInfo: TextCssInfo? = null,
        inlineColor: String? = null,
        isTitle: Boolean = false,
        isImage: Boolean = false,
        parentTextSize: Float = BASE_FONT_SIZE,
        parentColor: Int = Color.BLACK,
    ) {
        val parent = TextPaint().apply {
            textSize = parentTextSize
            color = parentColor
            isAntiAlias = true
        }
        RenderResources.drawingPaint.set(parent)
        val ch = TextChar(charData = "A", start = 0f, end = 10f, isImage = isImage)
        RenderResources.applyCharPaint(
            ch, isTitle, isBold = false, isSmall = false,
            textCssInfo = cssInfo, inlineScale = 1f, inlineColor = inlineColor
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // I1 SMART：可读保留 / 不可读回退
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun I1_smart_whiteOnWhite_fallsBackToUserColor() {
        RenderResources.bookColorMode = BookColorMode.SMART
        RenderResources.pageBgColor = Color.WHITE

        applyWith(inlineColor = "#FFFFFF", parentColor = Color.BLACK)
        assertEquals("白字×白底应回退用户色（不覆盖）", Color.BLACK, RenderResources.drawingPaint.color)
    }

    @Test
    fun I1_smart_redOnWhite_keepsAuthorColor() {
        RenderResources.bookColorMode = BookColorMode.SMART
        RenderResources.pageBgColor = Color.WHITE

        applyWith(inlineColor = "#FF0000", parentColor = Color.BLACK)
        assertEquals("红字×白底 CR≈4.0 可读，应保留作者色",
            0xFFFF0000.toInt(), RenderResources.drawingPaint.color)
    }

    // ════════════════════════════════════════════════════════════════════
    // I2 SMART：低 alpha 守卫
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun I2_smart_lowAlphaColor_guardedToUserColor() {
        RenderResources.bookColorMode = BookColorMode.SMART
        RenderResources.pageBgColor = Color.WHITE

        applyWith(inlineColor = "rgba(255,0,0,0.05)", parentColor = Color.BLACK)
        assertEquals("低 alpha(≈0x0D) 应被守卫拦下，保持用户色",
            Color.BLACK, RenderResources.drawingPaint.color)
    }

    // ════════════════════════════════════════════════════════════════════
    // I3 BOOK：无条件渲染（含不可读色）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun I3_book_whiteOnWhite_stillApplied() {
        RenderResources.bookColorMode = BookColorMode.BOOK
        RenderResources.pageBgColor = Color.WHITE

        applyWith(inlineColor = "#FFFFFF", parentColor = Color.BLACK)
        assertEquals("跟随书籍模式无条件覆盖（跳过判定与守卫）",
            0xFFFFFFFF.toInt(), RenderResources.drawingPaint.color)
    }

    // ════════════════════════════════════════════════════════════════════
    // I4 THEME：作者颜色一律不渲染（①段级与③span 级两入口都验）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun I4_theme_paragraphLevelColor_neverApplied() {
        RenderResources.bookColorMode = BookColorMode.THEME
        RenderResources.pageBgColor = Color.WHITE

        val css = TextCssInfo().apply { fontColor = "#FF0000" }
        applyWith(cssInfo = css, parentColor = Color.BLACK)
        assertEquals("THEME 模式段级作者色不渲染", Color.BLACK, RenderResources.drawingPaint.color)
    }

    @Test
    fun I4_theme_spanLevelColor_neverApplied() {
        RenderResources.bookColorMode = BookColorMode.THEME
        RenderResources.pageBgColor = Color.BLACK

        applyWith(inlineColor = "#FFFFFF", parentColor = Color.BLACK)
        assertEquals("THEME 模式 span 级作者色不渲染（任意底色）",
            Color.BLACK, RenderResources.drawingPaint.color)
    }

    // ════════════════════════════════════════════════════════════════════
    // I5 门控退役实证：display=""（无声明）的段级样式也生效
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun I5_gateRetired_emptyDisplay_paragraphStyleApplied() {
        RenderResources.bookColorMode = BookColorMode.BOOK
        RenderResources.pageBgColor = Color.WHITE

        val css = TextCssInfo().apply {
            fontSize = CssUnit.Em(2f)
            fontColor = "#FF0000"
            display = ""   // 旧实现要求 display=="block" 才生效；门控退役后无声明也应用
        }
        applyWith(cssInfo = css, parentColor = Color.BLACK)
        assertEquals("无 display 声明的段级作者色应生效（门控退役）",
            0xFFFF0000.toInt(), RenderResources.drawingPaint.color)
        assertEquals("无 display 声明的段级字号应生效",
            BASE_FONT_SIZE * 2f, RenderResources.drawingPaint.textSize, 0.01f)
    }

    // ════════════════════════════════════════════════════════════════════
    // I6 字号归一化：em / px 锚定 / percent（期望值以 BASE_FONT_SIZE 现算）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun I6_normalization_em2_doublesBaseSize() {
        val css = TextCssInfo().apply { fontSize = CssUnit.Em(2f) }
        applyWith(cssInfo = css)
        assertEquals(BASE_FONT_SIZE * 2f, RenderResources.drawingPaint.textSize, 0.01f)
    }

    @Test
    fun I6_normalization_px48_anchoredAbsolute() {
        // px 锚定：BASE_FONT_SIZE × (48f / BASE_FONT_SIZE) = 48px（与 density 无关）
        val css = TextCssInfo().apply { fontSize = CssUnit.Px(48f) }
        applyWith(cssInfo = css)
        assertEquals(48f, RenderResources.drawingPaint.textSize, 0.01f)
    }

    @Test
    fun I6_normalization_percent150_scalesBase() {
        val css = TextCssInfo().apply { fontSize = CssUnit.Percent(150f) }
        applyWith(cssInfo = css)
        assertEquals(BASE_FONT_SIZE * 1.5f, RenderResources.drawingPaint.textSize, 0.01f)
    }

    @Test
    fun I6_normalization_userSliderStillWorks() {
        // 用户滑杆始终全局生效：作者倍率作用于用户基准（非默认基准 parent=BASE×1.5）
        val userBase = BASE_FONT_SIZE * 1.5f
        val css = TextCssInfo().apply { fontSize = CssUnit.Em(2f) }
        applyWith(cssInfo = css, parentTextSize = userBase)
        assertEquals(userBase * 2f, RenderResources.drawingPaint.textSize, 0.01f)
    }

    // ════════════════════════════════════════════════════════════════════
    // I7 判定随背景实时更新（pageBgColor 切换）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun I7_bgSwitch_flipsDecisionForWhite_authorRedKeptOnBoth() {
        RenderResources.bookColorMode = BookColorMode.SMART

        // 白字：白底不可读 → 用户色；黑底可读(CR=21) → 作者色
        RenderResources.pageBgColor = Color.WHITE
        applyWith(inlineColor = "#FFFFFF", parentColor = Color.BLACK)
        assertEquals(Color.BLACK, RenderResources.drawingPaint.color)

        RenderResources.pageBgColor = Color.BLACK
        applyWith(inlineColor = "#FFFFFF", parentColor = Color.BLACK)
        assertEquals(0xFFFFFFFF.toInt(), RenderResources.drawingPaint.color)

        // 红字：两底 CR 均 ≥3 → 均保留
        RenderResources.pageBgColor = Color.WHITE
        applyWith(inlineColor = "#FF0000", parentColor = Color.BLACK)
        assertTrue(RenderResources.drawingPaint.color == 0xFFFF0000.toInt())

        RenderResources.pageBgColor = Color.BLACK
        applyWith(inlineColor = "#FF0000", parentColor = Color.BLACK)
        assertTrue(abs(RenderResources.drawingPaint.color - 0xFFFF0000.toInt()) == 0)
    }
}
