package com.wxn.reader.presentation.mainReader.autoread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话视图权威值状态机用例（F2，docs/plans/2026-09-11-plan-auto-read-session-scroll-authority-fix.md §6.1）：
 * 进入/推进/退出恢复/幂等/无会话防御 + epoch 代际语义（陈旧协程防护凭据）。
 *
 * 纯 JUnit：tracker 无 Android 依赖。
 */
class AutoReadSessionScrollTrackerTest {

    @Test
    fun `enter后hasSession与基准权威值建立`() {
        val t = AutoReadSessionScrollTracker()
        assertFalse(t.hasSession)
        t.enter(1)
        assertTrue(t.hasSession)
        assertEquals(1, t.originalScroll)
        assertEquals(1, t.sessionScroll)
    }

    @Test
    fun `trackViewChange推进权威值不动恢复基准`() {
        val t = AutoReadSessionScrollTracker()
        t.enter(1)
        t.trackViewChange(6)
        assertEquals(6, t.sessionScroll)
        assertEquals(1, t.originalScroll)
    }

    @Test
    fun `无会话时trackViewChange忽略`() {
        val t = AutoReadSessionScrollTracker()
        t.trackViewChange(6)
        assertEquals(-1, t.sessionScroll)
        assertFalse(t.hasSession)
    }

    @Test
    fun `视图被改动时exit返回恢复基准并复位`() {
        val t = AutoReadSessionScrollTracker()
        t.enter(1)
        t.trackViewChange(6)
        assertEquals(1, t.exit())
        assertFalse(t.hasSession)
    }

    @Test
    fun `视图未被改动时exit返回null`() {
        val t = AutoReadSessionScrollTracker()
        t.enter(1)
        assertNull(t.exit())
    }

    @Test
    fun `exit幂等_复位后再次调用返回null`() {
        val t = AutoReadSessionScrollTracker()
        t.enter(1)
        t.trackViewChange(6)
        assertEquals(1, t.exit())
        assertNull(t.exit())
        assertFalse(t.hasSession)
    }

    @Test
    fun `重复enter重置陈旧状态`() {
        val t = AutoReadSessionScrollTracker()
        t.enter(1)
        t.trackViewChange(6)
        t.enter(3)
        assertEquals(3, t.originalScroll)
        assertEquals(3, t.sessionScroll)
    }

    @Test
    fun `reset兜底复位`() {
        val t = AutoReadSessionScrollTracker()
        t.enter(1)
        t.trackViewChange(6)
        t.reset()
        assertNull(t.exit())
        assertFalse(t.hasSession)
    }

    @Test
    fun `epoch在真实会话边界递增_无会话空操作不递增`() {
        val t = AutoReadSessionScrollTracker()
        val e0 = t.epoch
        t.reset()                       // 无会话复位：非边界，不递增
        assertEquals(e0, t.epoch)
        t.enter(1)
        assertEquals(e0 + 1, t.epoch)
        t.exit()                        // 真实结束活跃会话：递增
        assertEquals(e0 + 2, t.epoch)
        t.reset()                       // 已复位：非边界，不递增
        assertEquals(e0 + 2, t.epoch)
    }

    @Test
    fun `trackViewChange不影响epoch`() {
        val t = AutoReadSessionScrollTracker()
        t.enter(1)
        val e = t.epoch
        t.trackViewChange(6)
        assertEquals(e, t.epoch)
    }

    @Test
    fun `exit幂等不重复递增epoch`() {
        val t = AutoReadSessionScrollTracker()
        t.enter(1)
        t.exit()
        val e = t.epoch
        t.exit()
        assertEquals(e, t.epoch)
    }
}
