package com.wxn.reader.presentation.mainReader

/**
 * 连续滚动视图初始定位决策（F3 加固，纯函数可单测）。
 * 三级定位：精确 (chapter, page) → 同章首项 → -1（未就绪，等待重试）。
 */
object ScrollInitialPositionPolicy {

    /** 定位输入的轻量投影（章内页序对），避免调用方依赖具体列表项类型 */
    interface PageRef {
        val chapterIndex: Int
        val pageIndex: Int
    }

    /**
     * 在合并页列表中解析初始定位目标。
     *
     * @param pages 合并页列表（MergedPageItem 实现 [PageRef]）
     * @param durChapterIndex 期望定位的章节索引
     * @param durPageIndex 期望定位的章内页索引
     * @return pages 中的目标下标；-1 表示当前列表中不存在可定位目标
     *         （目标章尚未进入列表，调用方应保持未定位态等待重试）
     */
    fun <T : PageRef> resolve(pages: List<T>, durChapterIndex: Int, durPageIndex: Int): Int {
        if (pages.isEmpty()) return -1
        // 1. 精确命中
        val exact = pages.indexOfFirst {
            it.chapterIndex == durChapterIndex && it.pageIndex == durPageIndex
        }
        if (exact >= 0) return exact
        // 2. 精确页不存在（跨模式分页几何差异 / 重排后页序越界）→ 回退同章首项，保证"同章不丢"
        return pages.indexOfFirst { it.chapterIndex == durChapterIndex }
    }
}
