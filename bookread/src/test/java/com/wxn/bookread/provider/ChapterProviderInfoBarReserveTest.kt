package com.wxn.bookread.provider

import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 契约：infoBarReservePx 仅作为 paddingVertical 的对称下限参与 max() 合并，
 * 不得改变 visibleHeight = viewHeight - paddingVertical*2 的既有几何公式
 * （约 30 个 instrumented 布局测试依赖该公式）。
 *
 * isVScrollMode 为 private，由 applyStyleInternal 从 prefs.scroll 派生——
 * 滚动场景一律经 upStyle(context, ReaderPreferences(scroll = 6)) 驱动。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterProviderInfoBarReserveTest {

    @Before
    fun setUp() {
        // 重置单例字段，避免测试间状态泄漏（参照既有 ChapterProviderViewSetSizeContractTest）
        ChapterProvider.viewWidth = 0
        ChapterProvider.viewHeight = 0
        ChapterProvider.paddingHorizontal = 0
        ChapterProvider.paddingVertical = 0
        ChapterProvider.visibleWidth = 0
        ChapterProvider.visibleHeight = 0
        ChapterProvider.visibleRight = 0
        ChapterProvider.visibleBottom = 0
        ChapterProvider.infoBarReservePx = 0
    }

    // ReaderPreferences 字段无默认值（默认值在 ReaderPreferencesUtil.defaultPreferences），
    // 测试一律从该 companion 实例出发 copy 修改。
    private fun triggerUpVisibleSize(
        prefs: ReaderPreferences = ReaderPreferencesUtil.defaultPreferences
    ) = runBlocking {
        ChapterProvider.upStyle(RuntimeEnvironment.getApplication(), prefs)
    }

    @Test
    fun `reserve larger than margin padding bumps paddingVertical symmetrically`() = runBlocking {
        ChapterProvider.infoBarReservePx = 0
        triggerUpVisibleSize()
        val base = ChapterProvider.paddingVertical

        ChapterProvider.infoBarReservePx = base + 50
        triggerUpVisibleSize()
        assertEquals(base + 50, ChapterProvider.paddingVertical)
        assertEquals(
            ChapterProvider.viewHeight - ChapterProvider.paddingVertical * 2,
            ChapterProvider.visibleHeight
        )
    }

    @Test
    fun `reserve smaller than margin padding keeps paddingVertical`() = runBlocking {
        ChapterProvider.infoBarReservePx = 0
        triggerUpVisibleSize()
        val base = ChapterProvider.paddingVertical

        ChapterProvider.infoBarReservePx = (base / 2).coerceAtLeast(1)
        triggerUpVisibleSize()
        assertEquals(base, ChapterProvider.paddingVertical)
    }

    @Test
    fun `scroll mode ignores reserve`() = runBlocking {
        ChapterProvider.infoBarReservePx = 999
        triggerUpVisibleSize(ReaderPreferencesUtil.defaultPreferences.copy(scroll = 6))
        assertEquals(0, ChapterProvider.paddingVertical)
    }

    @Test
    fun `reserve scales proportionally on rotation like paddingVertical`() {
        // 竖屏 1080x1920，padding 60 + 预留 100
        ChapterProvider.viewWidth = 1080
        ChapterProvider.viewHeight = 1920
        ChapterProvider.paddingVertical = 60
        ChapterProvider.infoBarReservePx = 100

        // 旋转到横屏 1920x1080
        ChapterProvider.synchronouslyUpdateLayout(w = 1920, h = 1080, oldw = 1080, oldh = 1920)

        // paddingVertical = 60*1080/1920 = 33；预留 = 100*1080/1920 = 56（同口径缩放）
        assertEquals((60f * 1080 / 1920).toInt(), ChapterProvider.paddingVertical)
        assertEquals((100f * 1080 / 1920).toInt(), ChapterProvider.infoBarReservePx)
        assertEquals(
            ChapterProvider.paddingVertical + ChapterProvider.visibleHeight,
            ChapterProvider.visibleBottom
        )
    }
}
