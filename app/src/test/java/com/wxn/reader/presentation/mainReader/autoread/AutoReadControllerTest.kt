package com.wxn.reader.presentation.mainReader.autoread

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoReadControllerTest {

    private class FakeAdapter(
        private var pxPerChar: Float = 10f,
        private var canForward: Boolean = true,
    ) : AutoReadScrollAdapter {
        var scrolledTotal = 0f
        override suspend fun scrollByPx(px: Float): Boolean { scrolledTotal += px; return canForward }
        override fun viewportHeightPx(): Float = 800f
        override fun verticalPxPerChar(): Float = pxPerChar
        override fun pageProgressFraction(): Float = 0.25f
    }

    private fun newController(
        speed: Int = 300,
        scrollMode: Boolean = false,
        pageChars: Int = 300,
        loading: () -> Boolean = { false },
        commit: () -> Boolean = { true },
        adapter: AutoReadScrollAdapter? = null,
        onBookEnd: () -> Unit = {},
        onFrame: (Float) -> Unit = {},
    ): AutoReadController = AutoReadController(
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        speedCharsPerMin = { speed },
        isScrollMode = { scrollMode },
        currentPageChars = { pageChars },
        commitNextPage = commit,
        isChapterLoading = loading,
        onBookEnd = onBookEnd,
        onFrame = onFrame,
    ).apply { scrollAdapter = adapter }

    // 速度300字/分、页300字 → 每页 60_000ms
    @Test
    fun `揭页推进量与耗时成正比`() = runTest {
        val c = newController()
        c.resetForStart()
        c.tick(1_000L)
        assertEquals(1_000f / 60_000f, c.state.value.progressFraction, 1e-4f)
    }

    @Test
    fun `揭满提交翻页并保留余量续读`() = runTest {
        var commits = 0
        val c = newController(commit = { commits++; true })
        c.resetForStart()
        c.tick(59_000L)
        c.tick(2_000L)                 // 累计 61_000ms > 60_000ms → 提交一次，余 1_000ms 带入新页
        assertEquals(1, commits)
        assertEquals(1_000f / 60_000f, c.state.value.progressFraction, 1e-4f)
        assertTrue(c.isActive)
    }

    @Test
    fun `书末提交失败则停止并回调`() = runTest {
        var bookEnd = false
        val c = newController(commit = { false }, onBookEnd = { bookEnd = true })
        c.resetForStart()
        c.tick(61_000L)
        assertFalse(c.isActive)
        assertTrue(bookEnd)
    }

    @Test
    fun `章节装载中不推进`() = runTest {
        val c = newController(loading = { true })
        c.resetForStart()
        c.tick(1_000L)
        assertEquals(0f, c.state.value.progressFraction, 0f)
    }

    @Test
    fun `触摸与弹层暂停冻结推进`() = runTest {
        val c = newController()
        c.resetForStart()
        c.pauseByTouch()
        c.tick(1_000L)
        assertEquals(0f, c.state.value.progressFraction, 0f)
        c.resumeByTouch()
        c.pauseByOverlay()
        c.tick(1_000L)
        assertEquals(0f, c.state.value.progressFraction, 0f)
        c.resumeByOverlay()
        c.tick(1_000L)
        assertTrue(c.state.value.progressFraction > 0f)
    }

    @Test
    fun `翻页提交钩子归零进度`() = runTest {
        val c = newController()
        c.resetForStart()
        c.tick(30_000L)
        c.onPageCommitted()
        assertEquals(0f, c.state.value.progressFraction, 0f)
        assertTrue(c.isActive)
    }

    @Test
    fun `揭满自提交经同步onPageChange仍保留余量`() = runTest {
        // 模拟生产链路：commitNextPage → factory.moveToNext → provider.setPageIndex →
        // PageViewController:587 同步 clickListener?.onPageChange() → VM 钩子 onPageCommitted()（审查 S7）
        var commits = 0
        var controllerRef: AutoReadController? = null
        val c = newController(commit = { commits++; controllerRef?.onPageCommitted(); true })
        controllerRef = c
        c.resetForStart()
        c.tick(59_000L)
        c.tick(2_000L)                 // 揭满自提交：钩子打进来时不得归零，余 1_000ms 必须带入新页
        assertEquals(1, commits)
        assertEquals(1_000f / 60_000f, c.state.value.progressFraction, 1e-4f)
        assertTrue(c.isActive)
    }

    @Test
    fun `自提交守卫超时作废后外部提交归零`() = runTest {
        // 缓存章节直跨（moveToNextChapter 命中缓存）不触发 onPageChange：
        // 守卫超时作废后，外部提交（音量键/滑动）必须恢复归零语义（代码审查 FIX-B）
        var now = 0L
        val c = AutoReadController(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            speedCharsPerMin = { 300 },
            isScrollMode = { false },
            currentPageChars = { 300 },
            commitNextPage = { true },          // 无钩子回达：模拟缓存跨章直通
            isChapterLoading = { false },
            onBookEnd = {},
            onFrame = {},
            nowMillis = { now },
        )
        c.resetForStart()
        c.tick(61_000L)                // 揭满自提交，余量保留
        assertEquals(1_000f / 60_000f, c.state.value.progressFraction, 1e-4f)
        now = 3_000L                   // 超过 2s 守卫有效期
        c.tick(16L)                    // 守卫作废
        c.onPageCommitted()            // 外部提交 → 正常归零
        assertEquals(0f, c.state.value.progressFraction, 0f)
        assertTrue(c.isActive)
    }

    @Test
    fun `滚动模式按每字垂直像素折算速度`() = runTest {
        val adapter = FakeAdapter(pxPerChar = 10f, canForward = true)
        val c = newController(scrollMode = true, adapter = adapter)   // 300/60*10 = 50 px/s
        c.resetForStart()
        c.tick(1_000L)
        assertEquals(50f, adapter.scrolledTotal, 0.5f)
        assertEquals(0.25f, c.state.value.progressFraction, 1e-4f)
    }

    @Test
    fun `滚动到书末停止并回调`() = runTest {
        var bookEnd = false
        val adapter = FakeAdapter(canForward = false)
        val c = newController(scrollMode = true, adapter = adapter, onBookEnd = { bookEnd = true })
        c.resetForStart()
        c.tick(1_000L)
        assertFalse(c.isActive)
        assertTrue(bookEnd)
    }

    @Test
    fun `重复resetForStart幂等`() {
        val c = newController()
        c.resetForStart()
        c.resetForStart()
        assertTrue(c.isActive)
        assertEquals(0f, c.state.value.progressFraction, 0f)
    }
}
