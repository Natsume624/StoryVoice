package com.wxn.bookread.ui

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.bookread.provider.ChapterProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AD-1 §5.1：背景自适应默认色板仪器测试（RenderResources 消费端；Paint 为 native 实现，
 * Robolectric 不做真实绘制，须真机/模拟器——同 StaticLayoutHyphenationSpike 经验）。
 *
 * 运行：gradlew.bat :bookread:connectedDebugAndroidTest --tests
 *   "com.wxn.bookread.ui.RenderResourcesAdaptiveDefaultsInstrumentedTest"
 */
@RunWith(AndroidJUnit4::class)
class RenderResourcesAdaptiveDefaultsInstrumentedTest {

    @Before
    fun resetToLight() {
        RenderResources.onPageBgChanged(Color.WHITE)
    }

    // A1 浅色档烧入（7 画笔 + aPaint）
    @Test
    fun A1_lightBg_burnsLightPalette() {
        RenderResources.onPageBgChanged(Color.WHITE)
        assertEquals(AccentPalette.LIGHT.link, ChapterProvider.aPaint.color)
        assertEquals(AccentPalette.LIGHT.searchHighlight, RenderResources.searchHighlightPaint.color)
        assertEquals(AccentPalette.LIGHT.selectionHandle, RenderResources.handlePaint.color)
        assertEquals(AccentPalette.LIGHT.selectionHandle, RenderResources.handleStrokePaint.color)
        assertEquals(AccentPalette.LIGHT.imagePlaceholder, RenderResources.imagePlaceholderPaint.color)
        assertEquals(AccentPalette.LIGHT.bookmark, RenderResources.bookmarkPaint.color)
        assertEquals(AccentPalette.LIGHT.underlineFallback, RenderResources.underlinePaint.color)
    }

    // A2 深色档烧入 + aPaint 文字/下划线同色（本次投诉主判读的自动化锚）
    @Test
    fun A2_darkBg_burnsDarkPalette_linkUnderlineSync() {
        RenderResources.onPageBgChanged(Color.BLACK)
        assertEquals(AccentPalette.DARK.link, ChapterProvider.aPaint.color)
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.Q) {
            assertEquals(AccentPalette.DARK.link, ChapterProvider.aPaint.underlineColor)
        }
        assertEquals(AccentPalette.DARK.searchHighlight, RenderResources.searchHighlightPaint.color)
        assertEquals(AccentPalette.DARK.bookmark, RenderResources.bookmarkPaint.color)
        assertEquals(AccentPalette.DARK.imagePlaceholder, RenderResources.imagePlaceholderPaint.color)
        assertEquals(AccentPalette.DARK.selectionHandle, RenderResources.handlePaint.color)
    }

    // A3 记忆化失效再解析：深→浅能切回
    @Test
    fun A3_memoInvalidation_reswitch() {
        RenderResources.onPageBgChanged(Color.BLACK)
        RenderResources.onPageBgChanged(Color.WHITE)
        assertEquals(AccentPalette.LIGHT.link, ChapterProvider.aPaint.color)
    }

    // A4 同背景重复调用：色值稳定（短路路径不破坏状态）
    @Test
    fun A4_sameBg_shortCircuitStable() {
        RenderResources.onPageBgChanged(Color.BLACK)
        val before = ChapterProvider.aPaint.color
        RenderResources.onPageBgChanged(Color.BLACK)
        assertEquals(before, ChapterProvider.aPaint.color)
    }

    // A5 深色壁纸 foldColor 主色（近似深背景）→ 深档
    @Test
    fun A5_foldColorDark_darkPalette() {
        RenderResources.onPageBgChanged(0xFF1A1A1A.toInt())
        assertEquals(AccentPalette.DARK.link, ChapterProvider.aPaint.color)
    }

    // A6 sepia 浅背景 → 浅档（浅色观感零回归锚）
    @Test
    fun A6_sepia_lightPalette() {
        RenderResources.onPageBgChanged(0xFFF5E6C8.toInt())
        assertEquals(AccentPalette.LIGHT.link, ChapterProvider.aPaint.color)
    }
}
