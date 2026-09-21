package com.wxn.reader.presentation.mainReader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 连续滚动视图初始定位决策用例（F3，docs/plans/2026-09-11-plan-auto-read-scroll-position-hardening.md §5.1）：
 * 精确命中 / 同章首项回退（跨模式分页几何差异） / 目标章未就绪返回 -1（保持未定位态重试）。
 *
 * 纯 JUnit：policy 为纯函数对象，测试用独立 fake 实现 PageRef，不依赖 MergedPageItem。
 */
class ScrollInitialPositionPolicyTest {

    private class FakePage(
        override val chapterIndex: Int,
        override val pageIndex: Int,
    ) : ScrollInitialPositionPolicy.PageRef

    private fun pages(vararg pairs: Pair<Int, Int>): List<ScrollInitialPositionPolicy.PageRef> =
        pairs.map { FakePage(it.first, it.second) }

    @Test
    fun `精确命中返回目标下标`() {
        val pages = pages(0 to 0, 0 to 1, 1 to 0)
        assertEquals(2, ScrollInitialPositionPolicy.resolve(pages, durChapterIndex = 1, durPageIndex = 0))
    }

    @Test
    fun `精确页越界回退同章首项`() {
        // 跨模式分页几何差异：目标章存在但页序不存在 → 落到该章首项，保证"同章不丢"
        val pages = pages(0 to 0, 0 to 1, 1 to 0)
        assertEquals(2, ScrollInitialPositionPolicy.resolve(pages, durChapterIndex = 1, durPageIndex = 5))
    }

    @Test
    fun `目标章不在列表返回负一保持未定位`() {
        val pages = pages(0 to 0, 0 to 1)
        assertEquals(-1, ScrollInitialPositionPolicy.resolve(pages, durChapterIndex = 1, durPageIndex = 0))
    }

    @Test
    fun `空列表返回负一`() {
        assertEquals(-1, ScrollInitialPositionPolicy.resolve(emptyList(), durChapterIndex = 0, durPageIndex = 0))
    }

    @Test
    fun `durPageIndex为负时同章首项兜底`() {
        // 控制器尚无有效页码（如刚切视图还未同步）→ 章首兜底
        val pages = pages(0 to 0, 0 to 1)
        assertEquals(0, ScrollInitialPositionPolicy.resolve(pages, durChapterIndex = 0, durPageIndex = -1))
    }

    @Test
    fun `目标章位于列表中部或尾部均正确下标`() {
        val pages = pages(0 to 0, 1 to 0, 1 to 1, 2 to 0)
        assertEquals(1, ScrollInitialPositionPolicy.resolve(pages, durChapterIndex = 1, durPageIndex = 0))
        assertEquals(3, ScrollInitialPositionPolicy.resolve(pages, durChapterIndex = 2, durPageIndex = 0))
    }
}
