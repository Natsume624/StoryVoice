package com.wxn.bookread.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class InfoBarSpecTest {

    // ---- isEnabled：空配置等价关闭 ----
    @Test
    fun `isEnabled - hidden or all-none slots returns false`() {
        val slots = InfoBarSlots(InfoBarSpec.SLOT_NONE, InfoBarSpec.SLOT_NONE, InfoBarSpec.SLOT_NONE)
        assertFalse(InfoBarSpec.isEnabled(hide = false, slots = slots))
        val withContent = InfoBarSlots(InfoBarSpec.SLOT_NONE, InfoBarSpec.SLOT_TIME, InfoBarSpec.SLOT_NONE)
        assertFalse(InfoBarSpec.isEnabled(hide = true, slots = withContent))
    }

    @Test
    fun `isEnabled - visible with content`() {
        val slots = InfoBarSlots(
            InfoBarSpec.SLOT_CHAPTER_TITLE,
            InfoBarSpec.SLOT_NONE,
            InfoBarSpec.SLOT_TOTAL_PROGRESS
        )
        assertTrue(InfoBarSpec.isEnabled(hide = false, slots = slots))
    }

    @Test
    fun `hasCode detects slot presence`() {
        val slots = InfoBarSlots(InfoBarSpec.SLOT_NONE, InfoBarSpec.SLOT_TIME, InfoBarSpec.SLOT_NONE)
        assertTrue(slots.hasCode(InfoBarSpec.SLOT_TIME))
        assertFalse(slots.hasCode(InfoBarSpec.SLOT_BATTERY))
    }

    // ---- computeReservePx ----
    @Test
    fun `computeReservePx - zero when scroll mode or nothing enabled`() {
        assertEquals(
            0,
            InfoBarSpec.computeReservePx(3f, 80, headerEnabled = true, footerEnabled = true, isScrollMode = true)
        )
        assertEquals(
            0,
            InfoBarSpec.computeReservePx(3f, 80, headerEnabled = false, footerEnabled = false, isScrollMode = false)
        )
    }

    @Test
    fun `computeReservePx - footer only needs bar plus gap`() {
        // density=3 → barPx=72, gap=24 → bottomNeed=96
        assertEquals(
            96,
            InfoBarSpec.computeReservePx(3f, 80, headerEnabled = false, footerEnabled = true, isScrollMode = false)
        )
    }

    @Test
    fun `computeReservePx - header only uses overflow beyond status bar placeholder`() {
        // barPx=72 < statusBar 80 → 无需预留
        assertEquals(
            0,
            InfoBarSpec.computeReservePx(3f, 80, headerEnabled = true, footerEnabled = false, isScrollMode = false)
        )
        // barPx=72 > statusBar 60 → 溢出 12
        assertEquals(
            12,
            InfoBarSpec.computeReservePx(3f, 60, headerEnabled = true, footerEnabled = false, isScrollMode = false)
        )
    }

    @Test
    fun `computeReservePx - both bars take max of needs`() {
        // topNeed=12, bottomNeed=96 → 96
        assertEquals(
            96,
            InfoBarSpec.computeReservePx(3f, 60, headerEnabled = true, footerEnabled = true, isScrollMode = false)
        )
    }

    // ---- 格式化 ----
    @Test
    fun `formatPage is 1-based`() {
        assertEquals("12/345", InfoBarSpec.formatPage(11, 345))
        assertEquals("1/1", InfoBarSpec.formatPage(0, 1))
    }

    @Test
    fun `formatProgress clamps and keeps two decimals`() {
        assertEquals("45.28%", InfoBarSpec.formatProgress(0.4528))
        assertEquals("0.00%", InfoBarSpec.formatProgress(-1.0))
        assertEquals("100.00%", InfoBarSpec.formatProgress(1.5))
    }

    @Test
    fun `formatProgress uses invariant western digits regardless of default locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("45.28%", InfoBarSpec.formatProgress(0.4528))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `formatPageAndTotal combines page and progress`() {
        assertEquals("12/345 · 45.28%", InfoBarSpec.formatPageAndTotal(11, 345, 0.4528))
    }

    @Test
    fun `formatTime follows 12-24 hour setting`() {
        val cal = Calendar.getInstance().apply { set(2026, 8, 4, 14, 5) } // 14:05
        assertEquals("14:05", InfoBarSpec.formatTime(is24Hour = true, cal, Locale.US))
        assertEquals("2:05", InfoBarSpec.formatTime(is24Hour = false, cal, Locale.US))
    }

    // ---- 电量解析 ----
    @Test
    fun `parseBattery normalizes and rejects invalid input`() {
        assertEquals(87, InfoBarSpec.parseBattery(87, 100))
        assertEquals(50, InfoBarSpec.parseBattery(1, 2))
        assertNull(InfoBarSpec.parseBattery(-1, 100))
        assertNull(InfoBarSpec.parseBattery(50, 0))
    }

    // ---- 电池图形填充比例 ----
    @Test
    fun `batteryFillFraction maps percent to 0-1`() {
        assertEquals(0f, InfoBarSpec.batteryFillFraction(0), 1e-6f)
        assertEquals(0.5f, InfoBarSpec.batteryFillFraction(50), 1e-6f)
        assertEquals(1f, InfoBarSpec.batteryFillFraction(100), 1e-6f)
    }

    @Test
    fun `batteryFillFraction clamps out of range input`() {
        assertEquals(0f, InfoBarSpec.batteryFillFraction(-5), 1e-6f)
        assertEquals(1f, InfoBarSpec.batteryFillFraction(120), 1e-6f)
    }
}
